/**
 * 社区楼栋态势 — 参考图一比一视觉复刻（固定演示，不接接口）
 */
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import {
  DEMO_BUILDINGS,
  DEMO_COLORS,
  DEMO_EVENTS,
  DEMO_HUDS,
  DEMO_TRAILS,
  DEMO_TREES,
  type DemoBuilding,
  type DemoEvent,
} from './demoLayout'

const FLOOR_H = 1.12
const ISO_DIR = new THREE.Vector3(1.02, 1.22, 1.02).normalize()

export class CommunityTwinScene {
  private host: HTMLElement
  private width = 0
  private height = 0
  private renderer!: THREE.WebGLRenderer
  private labelRenderer!: CSS2DRenderer
  private composer!: EffectComposer
  private bloomPass!: UnrealBloomPass
  private scene!: THREE.Scene
  private camera!: THREE.OrthographicCamera
  private controls!: OrbitControls
  private raf = 0
  private clock = new THREE.Clock()
  private raycaster = new THREE.Raycaster()
  private pointer = new THREE.Vector2()
  private disposed = false

  private fitRoot = new THREE.Group()
  private eventPins: THREE.Group[] = []
  private trailMeshes: THREE.Mesh[] = []
  private pickables: THREE.Object3D[] = []
  private onTip: ((text: string) => void) | null = null
  private boundPointer = (e: PointerEvent) => this.onPointer(e)

  constructor(host: HTMLElement) {
    this.host = host
  }

  setTipHandler(fn: (text: string) => void) {
    this.onTip = fn
  }

  init() {
    this.readSize()
    this.scene = new THREE.Scene()
    this.scene.background = new THREE.Color(DEMO_COLORS.bg)
    this.scene.fog = new THREE.FogExp2(DEMO_COLORS.bg, 0.012)

    const aspect = this.width / Math.max(this.height, 1)
    const fr = 30
    this.camera = new THREE.OrthographicCamera(-fr * aspect, fr * aspect, fr, -fr, -120, 220)
    this.camera.position.copy(ISO_DIR.clone().multiplyScalar(85))
    this.camera.lookAt(0, 0, 0)

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    this.renderer.setSize(this.width, this.height, false)
    this.renderer.shadowMap.enabled = true
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap
    this.renderer.outputColorSpace = THREE.SRGBColorSpace
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.28
    Object.assign(this.renderer.domElement.style, { display: 'block', width: '100%', height: '100%' })
    this.host.appendChild(this.renderer.domElement)

    this.labelRenderer = new CSS2DRenderer()
    this.labelRenderer.setSize(this.width, this.height)
    const lr = this.labelRenderer.domElement
    Object.assign(lr.style, { position: 'absolute', inset: '0', pointerEvents: 'none', zIndex: '2' })
    this.host.appendChild(lr)

    this.composer = new EffectComposer(this.renderer)
    this.composer.addPass(new RenderPass(this.scene, this.camera))
    // 阈值偏高：只让轮廓/道路亮点/事件点 bloom，避免整屏泛白
    this.bloomPass = new UnrealBloomPass(new THREE.Vector2(this.width, this.height), 0.55, 0.32, 0.72)
    this.composer.addPass(this.bloomPass)
    this.composer.addPass(new OutputPass())

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.08
    this.controls.enableRotate = false
    this.controls.enablePan = false
    this.controls.enableZoom = true
    this.controls.minZoom = 0.88
    this.controls.maxZoom = 1.28
    this.controls.target.set(0, 0, 0)

    this.scene.add(this.fitRoot)
    this.setupLights()
    this.buildGround()
    this.buildRoads()
    this.buildGates()
    this.buildGreenery()
    DEMO_BUILDINGS.forEach((b) => this.fitRoot.add(this.createBuilding(b)))
    DEMO_EVENTS.forEach((e) => {
      const pin = this.createEventPin(e)
      this.fitRoot.add(pin)
      this.eventPins.push(pin)
      this.pickables.push(pin)
    })
    DEMO_HUDS.forEach((h) => this.fitRoot.add(this.createHud(h)))

    this.renderer.domElement.addEventListener('pointerdown', this.boundPointer)
    this.fitToScene()
    this.animate()
  }

  resize() {
    if (this.disposed) return
    this.readSize()
    this.renderer.setSize(this.width, this.height, false)
    this.labelRenderer.setSize(this.width, this.height)
    this.composer.setSize(this.width, this.height)
    this.bloomPass.resolution.set(this.width, this.height)
    this.fitToScene()
  }

  dispose() {
    this.disposed = true
    cancelAnimationFrame(this.raf)
    this.renderer.domElement.removeEventListener('pointerdown', this.boundPointer)
    this.controls.dispose()
    this.composer.dispose()
    this.renderer.dispose()
    this.labelRenderer.domElement.remove()
    this.renderer.domElement.remove()
    this.scene.traverse((obj) => {
      const m = obj as THREE.Mesh
      if (m.geometry) m.geometry.dispose()
      const mat = m.material
      if (Array.isArray(mat)) mat.forEach((x) => x.dispose())
      else if (mat) (mat as THREE.Material).dispose()
    })
  }

  private readSize() {
    this.width = Math.max(160, this.host.clientWidth || 640)
    this.height = Math.max(160, this.host.clientHeight || 400)
  }

  private setupLights() {
    this.scene.add(new THREE.HemisphereLight(0xb8dce8, 0x0c1c24, 0.9))
    const dir = new THREE.DirectionalLight(0xf0f7fa, 1.15)
    dir.position.set(26, 44, 20)
    dir.castShadow = true
    dir.shadow.mapSize.set(1024, 1024)
    dir.shadow.camera.near = 2
    dir.shadow.camera.far = 110
    dir.shadow.camera.left = -42
    dir.shadow.camera.right = 42
    dir.shadow.camera.top = 42
    dir.shadow.camera.bottom = -42
    dir.shadow.bias = -0.0003
    dir.shadow.normalBias = 0.035
    this.scene.add(dir)
    const fill = new THREE.DirectionalLight(0x6a9aaa, 0.45)
    fill.position.set(-22, 26, -16)
    this.scene.add(fill)
  }

  private fitToScene() {
    this.readSize()
    const box = new THREE.Box3().setFromObject(this.fitRoot)
    if (box.isEmpty()) box.set(new THREE.Vector3(-24, 0, -24), new THREE.Vector3(24, 12, 24))
    box.max.y = Math.max(box.max.y, 14)
    const center = box.getCenter(new THREE.Vector3())
    center.y = 0
    const size = box.getSize(new THREE.Vector3())
    const span = Math.max(size.x, size.z) * 1.05 + size.y * 0.3
    const aspect = this.width / Math.max(this.height, 1)
    const halfH = (span / 2) * 1.06
    const halfW = halfH * aspect
    this.camera.left = -halfW
    this.camera.right = halfW
    this.camera.top = halfH
    this.camera.bottom = -halfH
    this.camera.near = -140
    this.camera.far = 220
    this.camera.zoom = 1
    this.camera.updateProjectionMatrix()
    this.camera.position.copy(center.clone().add(ISO_DIR.clone().multiplyScalar(85)))
    this.controls.target.copy(center)
    this.controls.update()
  }

  private buildGround() {
    const ground = new THREE.Mesh(
      new THREE.CircleGeometry(32, 64),
      new THREE.MeshStandardMaterial({ color: DEMO_COLORS.ground, roughness: 0.96, metalness: 0.02 }),
    )
    ground.rotation.x = -Math.PI / 2
    ground.receiveShadow = true
    this.fitRoot.add(ground)

    const grid = new THREE.GridHelper(58, 29, 0x0f3040, 0x0a2432)
    grid.position.y = 0.01
    const mats = Array.isArray(grid.material) ? grid.material : [grid.material]
    mats.forEach((m) => {
      m.transparent = true
      ;(m as THREE.Material & { opacity: number }).opacity = 0.32
    })
    this.fitRoot.add(grid)

    const rim = new THREE.Mesh(
      new THREE.RingGeometry(31.4, 32.1, 72),
      new THREE.MeshBasicMaterial({
        color: DEMO_COLORS.edge,
        transparent: true,
        opacity: 0.22,
        side: THREE.DoubleSide,
      }),
    )
    rim.rotation.x = -Math.PI / 2
    rim.position.y = 0.02
    this.fitRoot.add(rim)
  }

  private buildRoads() {
    const roadMat = new THREE.MeshStandardMaterial({
      color: DEMO_COLORS.road,
      roughness: 0.42,
      metalness: 0.22,
      transparent: true,
      opacity: 0.92,
    })
    const edgeMat = new THREE.LineBasicMaterial({ color: DEMO_COLORS.edge, transparent: true, opacity: 0.88 })

    const vRoad = new THREE.Mesh(new THREE.BoxGeometry(5.0, 0.06, 50), roadMat)
    vRoad.position.set(0, 0.04, 0)
    vRoad.receiveShadow = true
    this.fitRoot.add(vRoad)
    this.addRoadFrame(0, 0, 5.0, 50, edgeMat)

    const hRoad = new THREE.Mesh(new THREE.BoxGeometry(50, 0.06, 5.0), roadMat.clone())
    hRoad.position.set(0, 0.045, 0)
    hRoad.receiveShadow = true
    this.fitRoot.add(hRoad)
    this.addRoadFrame(0, 0, 50, 5.0, edgeMat)

    // 中央路口更亮 + 网格
    const plaza = new THREE.Mesh(
      new THREE.BoxGeometry(7.4, 0.05, 7.4),
      new THREE.MeshStandardMaterial({
        color: 0x14808f,
        roughness: 0.35,
        metalness: 0.28,
        transparent: true,
        opacity: 0.96,
      }),
    )
    plaza.position.set(0, 0.058, 0)
    this.fitRoot.add(plaza)

    const plazaGrid = new THREE.GridHelper(6.8, 9, DEMO_COLORS.edge, 0x1a9aaa)
    plazaGrid.position.y = 0.095
    const pg = Array.isArray(plazaGrid.material) ? plazaGrid.material : [plazaGrid.material]
    pg.forEach((m) => {
      m.transparent = true
      ;(m as THREE.Material & { opacity: number }).opacity = 0.65
    })
    this.fitRoot.add(plazaGrid)

    this.addDashed(new THREE.Vector3(0, 0.11, -23), new THREE.Vector3(0, 0.11, 23))
    this.addDashed(new THREE.Vector3(-23, 0.11, 0), new THREE.Vector3(23, 0.11, 0))

    // 道路光轨（参考图车流亮点）
    const trailMat = new THREE.MeshBasicMaterial({ color: DEMO_COLORS.edge, transparent: true, opacity: 0.95 })
    DEMO_TRAILS.forEach((t, i) => {
      const geo =
        t.axis === 'v' ? new THREE.BoxGeometry(0.35, 0.08, 1.6) : new THREE.BoxGeometry(1.6, 0.08, 0.35)
      const m = new THREE.Mesh(geo, trailMat.clone())
      m.position.set(t.x, 0.13, t.z)
      m.userData = { phase: i * 0.55, axis: t.axis, baseX: t.x, baseZ: t.z }
      this.fitRoot.add(m)
      this.trailMeshes.push(m)
    })
  }

  private addRoadFrame(cx: number, cz: number, w: number, d: number, mat: THREE.LineBasicMaterial) {
    const hw = w / 2
    const hd = d / 2
    const y = 0.1
    const pts = [
      new THREE.Vector3(cx - hw, y, cz - hd),
      new THREE.Vector3(cx + hw, y, cz - hd),
      new THREE.Vector3(cx + hw, y, cz - hd),
      new THREE.Vector3(cx + hw, y, cz + hd),
      new THREE.Vector3(cx + hw, y, cz + hd),
      new THREE.Vector3(cx - hw, y, cz + hd),
      new THREE.Vector3(cx - hw, y, cz + hd),
      new THREE.Vector3(cx - hw, y, cz - hd),
    ]
    this.fitRoot.add(new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(pts), mat))
  }

  private addDashed(a: THREE.Vector3, b: THREE.Vector3) {
    const geo = new THREE.BufferGeometry().setFromPoints([a, b])
    const mat = new THREE.LineDashedMaterial({
      color: 0xa8f4fa,
      dashSize: 0.85,
      gapSize: 0.45,
      transparent: true,
      opacity: 0.7,
    })
    const line = new THREE.Line(geo, mat)
    line.computeLineDistances()
    this.fitRoot.add(line)
  }

  private buildGates() {
    ;[
      { x: 0, z: -24.2, title: '北门' },
      { x: 0, z: 24.2, title: '南门' },
    ].forEach(({ x, z, title }) => {
      const mat = new THREE.MeshStandardMaterial({
        color: DEMO_COLORS.building,
        transparent: true,
        opacity: 0.78,
        roughness: 0.45,
        metalness: 0.15,
      })
      const postL = new THREE.Mesh(new THREE.BoxGeometry(0.38, 2.5, 0.38), mat)
      postL.position.set(x - 2.5, 1.25, z)
      postL.castShadow = true
      const postR = postL.clone()
      postR.position.set(x + 2.5, 1.25, z)
      const beam = new THREE.Mesh(new THREE.BoxGeometry(5.4, 0.36, 0.38), mat)
      beam.position.set(x, 2.6, z)
      beam.castShadow = true
      this.fitRoot.add(postL, postR, beam)

      const edge = new THREE.LineSegments(
        new THREE.EdgesGeometry(new THREE.BoxGeometry(5.4, 0.36, 0.38)),
        new THREE.LineBasicMaterial({ color: DEMO_COLORS.edge, transparent: true, opacity: 0.9 }),
      )
      edge.position.copy(beam.position)
      this.fitRoot.add(edge)

      const el = document.createElement('div')
      el.className = 'twin-gate-label'
      el.textContent = title
      const label = new CSS2DObject(el)
      label.position.set(x, 3.8, z)
      this.fitRoot.add(label)
    })
  }

  private buildGreenery() {
    ;[
      { x: -12, z: -12.5, s: 1.15 },
      { x: 12, z: -12.5, s: 1.1 },
      { x: -12, z: 11.5, s: 1.1 },
      { x: 12, z: 11.5, s: 1.15 },
      { x: -19, z: 0, s: 0.75 },
      { x: 19, z: 0, s: 0.75 },
    ].forEach((p) => this.addGreenPatch(p.x, p.z, p.s))
    DEMO_TREES.forEach((t) => this.addTree(t.x, t.z, t.s))
  }

  private addGreenPatch(x: number, z: number, scale: number) {
    const shape = new THREE.Shape()
    shape.moveTo(-4.2 * scale, -1.4 * scale)
    shape.bezierCurveTo(-5.2 * scale, 1.6 * scale, -1.8 * scale, 3.8 * scale, 0.6 * scale, 4.0 * scale)
    shape.bezierCurveTo(3.2 * scale, 4.2 * scale, 5.2 * scale, 1.4 * scale, 4.5 * scale, -1.0 * scale)
    shape.bezierCurveTo(3.8 * scale, -3.5 * scale, 0.4 * scale, -4.2 * scale, -2.2 * scale, -3.8 * scale)
    shape.bezierCurveTo(-3.8 * scale, -3.4 * scale, -4.2 * scale, -2.4 * scale, -4.2 * scale, -1.4 * scale)
    const geo = new THREE.ShapeGeometry(shape)
    geo.rotateX(-Math.PI / 2)
    const mesh = new THREE.Mesh(
      geo,
      new THREE.MeshStandardMaterial({ color: DEMO_COLORS.tree, roughness: 1 }),
    )
    mesh.position.set(x, 0.018, z)
    mesh.receiveShadow = true
    this.fitRoot.add(mesh)
  }

  private addTree(x: number, z: number, s: number) {
    const trunk = new THREE.Mesh(
      new THREE.CylinderGeometry(0.1 * s, 0.14 * s, 0.7 * s, 5),
      new THREE.MeshStandardMaterial({ color: 0x163028, roughness: 1 }),
    )
    trunk.position.set(x, 0.35 * s, z)
    this.fitRoot.add(trunk)
    // 低多边形圆冠（参考图灌木感）
    const crownMat = new THREE.MeshStandardMaterial({ color: DEMO_COLORS.tree, roughness: 0.98 })
    const c1 = new THREE.Mesh(new THREE.IcosahedronGeometry(0.75 * s, 0), crownMat)
    c1.position.set(x, 1.15 * s, z)
    c1.castShadow = true
    const c2 = new THREE.Mesh(new THREE.IcosahedronGeometry(0.55 * s, 0), crownMat.clone())
    c2.position.set(x + 0.25 * s, 1.35 * s, z - 0.15 * s)
    this.fitRoot.add(c1, c2)
  }

  private createBuilding(cfg: DemoBuilding) {
    const g = new THREE.Group()
    g.position.set(cfg.x, 0, cfg.z)
    g.rotation.y = cfg.rotY

    const h = cfg.floors * FLOOR_H
    const bright = 1 - cfg.depth * 0.2
    const bodyColor = new THREE.Color(DEMO_COLORS.building).multiplyScalar(0.88 + bright * 0.18)

    const bodyMat = new THREE.MeshStandardMaterial({
      color: bodyColor,
      roughness: 0.4,
      metalness: 0.22,
      transparent: true,
      opacity: 0.8 + bright * 0.08,
    })
    const darkMat = new THREE.MeshStandardMaterial({
      color: bodyColor.clone().multiplyScalar(0.62),
      roughness: 0.55,
      metalness: 0.12,
      transparent: true,
      opacity: 0.82,
    })
    const edgeMat = new THREE.LineBasicMaterial({
      color: DEMO_COLORS.edge,
      transparent: true,
      opacity: 0.62 + bright * 0.28,
    })
    const floorMat = new THREE.LineBasicMaterial({
      color: DEMO_COLORS.edge,
      transparent: true,
      opacity: 0.32 + bright * 0.15,
    })

    const body = new THREE.Mesh(new THREE.BoxGeometry(cfg.w, h, cfg.d), bodyMat)
    body.position.y = h / 2
    body.castShadow = true
    body.receiveShadow = true
    g.add(body)

    const roof = new THREE.Mesh(new THREE.BoxGeometry(cfg.w * 1.06, 0.3, cfg.d * 1.06), bodyMat.clone())
    roof.position.y = h + 0.15
    roof.castShadow = true
    g.add(roof)
    const roofCap = new THREE.Mesh(new THREE.BoxGeometry(cfg.w * 0.42, 0.38, cfg.d * 0.32), darkMat)
    roofCap.position.y = h + 0.48
    g.add(roofCap)

    const bal = new THREE.Mesh(new THREE.BoxGeometry(cfg.w * 0.3, h * 0.4, 0.32), darkMat)
    bal.position.set(cfg.w * 0.06, h * 0.5, cfg.d / 2 + 0.15)
    bal.castShadow = true
    g.add(bal)

    const base = new THREE.Mesh(
      new THREE.BoxGeometry(cfg.w * 1.1, 0.16, cfg.d * 1.1),
      new THREE.MeshStandardMaterial({
        color: bodyColor.clone().multiplyScalar(0.68),
        roughness: 0.7,
        transparent: true,
        opacity: 0.88,
      }),
    )
    base.position.y = 0.08
    g.add(base)

    // 四面楼层线
    for (let i = 1; i < cfg.floors; i++) {
      const y = i * FLOOR_H
      const hw = cfg.w / 2
      const hd = cfg.d / 2
      const ring = [
        new THREE.Vector3(-hw, y, hd + 0.02),
        new THREE.Vector3(hw, y, hd + 0.02),
        new THREE.Vector3(hw, y, hd + 0.02),
        new THREE.Vector3(hw, y, -hd - 0.02),
        new THREE.Vector3(hw, y, -hd - 0.02),
        new THREE.Vector3(-hw, y, -hd - 0.02),
        new THREE.Vector3(-hw, y, -hd - 0.02),
        new THREE.Vector3(-hw, y, hd + 0.02),
      ]
      g.add(new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(ring), floorMat))
    }

    // 竖向分隔
    for (let c = 1; c < 3; c++) {
      const x = -cfg.w / 2 + (cfg.w * c) / 3
      g.add(
        new THREE.Line(
          new THREE.BufferGeometry().setFromPoints([
            new THREE.Vector3(x, 0.15, cfg.d / 2 + 0.02),
            new THREE.Vector3(x, h - 0.1, cfg.d / 2 + 0.02),
          ]),
          floorMat,
        ),
      )
    }

    // 稀疏窗格
    const winMat = new THREE.MeshStandardMaterial({
      color: 0x05141c,
      roughness: 0.3,
      metalness: 0.3,
      transparent: true,
      opacity: 0.88,
    })
    const rows = Math.min(cfg.floors - 1, 5)
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < 2; c++) {
        const win = new THREE.Mesh(new THREE.BoxGeometry(cfg.w * 0.15, FLOOR_H * 0.42, 0.05), winMat)
        win.position.set(-cfg.w * 0.2 + c * cfg.w * 0.4, FLOOR_H * 1.05 + r * FLOOR_H, cfg.d / 2 + 0.03)
        g.add(win)
      }
    }

    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(cfg.w, h, cfg.d)), edgeMat)
    edges.position.y = h / 2
    g.add(edges)
    const roofEdge = new THREE.LineSegments(
      new THREE.EdgesGeometry(new THREE.BoxGeometry(cfg.w * 1.06, 0.3, cfg.d * 1.06)),
      edgeMat,
    )
    roofEdge.position.y = h + 0.15
    g.add(roofEdge)

    // 参考图：部分楼栋外层高亮框
    if (cfg.highlight) {
      const frame = new THREE.LineSegments(
        new THREE.EdgesGeometry(new THREE.BoxGeometry(cfg.w * 1.18, h + 0.8, cfg.d * 1.18)),
        new THREE.LineBasicMaterial({ color: DEMO_COLORS.edge, transparent: true, opacity: 0.75 }),
      )
      frame.position.y = h / 2 + 0.1
      g.add(frame)
    }

    return g
  }

  private createEventPin(ev: DemoEvent) {
    const g = new THREE.Group()
    g.position.set(ev.x, ev.y, ev.z)
    g.userData = { kind: 'event', text: ev.text, phase: ev.phase, baseY: ev.y }

    const color = ev.level === 'warn' ? DEMO_COLORS.warn : DEMO_COLORS.event
    // 定位图钉：锥体 + 球体，明显浮在楼顶上方
    const tip = new THREE.Mesh(
      new THREE.ConeGeometry(0.38, 0.85, 14),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.95 }),
    )
    tip.rotation.x = Math.PI
    tip.position.y = 0.35
    g.add(tip)
    const head = new THREE.Mesh(
      new THREE.SphereGeometry(0.48, 16, 16),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.98 }),
    )
    head.position.y = 0.95
    g.add(head)

    const ring = new THREE.Mesh(
      new THREE.RingGeometry(0.45, 0.62, 24),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.4,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.02
    ring.name = 'pulse'
    g.add(ring)

    const hit = new THREE.Mesh(new THREE.SphereGeometry(0.75, 8, 8), new THREE.MeshBasicMaterial({ visible: false }))
    hit.position.y = 0.4
    g.add(hit)
    return g
  }

  private createHud(h: { x: number; y: number; z: number; lines: string[] }) {
    const el = document.createElement('div')
    el.className = 'twin-hud-card'
    el.innerHTML = h.lines.map((l) => `<span>${l}</span>`).join('')
    const obj = new CSS2DObject(el)
    obj.position.set(h.x, h.y, h.z)
    return obj
  }

  private onPointer(e: PointerEvent) {
    const rect = this.renderer.domElement.getBoundingClientRect()
    this.pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    this.pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
    this.raycaster.setFromCamera(this.pointer, this.camera)
    const hits = this.raycaster.intersectObjects(this.pickables, true)
    if (!hits.length) return
    let obj: THREE.Object3D | null = hits[0].object
    while (obj && obj.userData?.kind !== 'event') obj = obj.parent
    if (!obj) return
    this.onTip?.(String(obj.userData.text || '普通事件'))
  }

  private animate = () => {
    if (this.disposed) return
    this.raf = requestAnimationFrame(this.animate)
    const t = this.clock.elapsedTime
    this.controls.update()

    // 光轨沿道路缓慢流动
    for (const m of this.trailMeshes) {
      const phase = Number(m.userData.phase) || 0
      const axis = m.userData.axis as 'v' | 'h'
      const travel = ((t * 3.2 + phase * 4) % 46) - 23
      if (axis === 'v') m.position.z = travel
      else m.position.x = travel
      const mat = m.material as THREE.MeshBasicMaterial
      mat.opacity = 0.55 + Math.sin(t * 4 + phase) * 0.35
    }

    for (const pin of this.eventPins) {
      const phase = Number(pin.userData.phase) || 0
      const baseY = Number(pin.userData.baseY) || 0
      pin.position.y = baseY + Math.sin(t * 1.55 + phase) * 0.2
      const pulse = pin.getObjectByName('pulse') as THREE.Mesh | undefined
      if (pulse) {
        const s = 1 + Math.sin(t * 2.0 + phase * 1.4) * 0.14
        pulse.scale.setScalar(s)
        ;(pulse.material as THREE.MeshBasicMaterial).opacity = 0.22 + Math.sin(t * 2.0 + phase) * 0.12
      }
    }

    this.composer.render()
    this.labelRenderer.render(this.scene, this.camera)
  }
}

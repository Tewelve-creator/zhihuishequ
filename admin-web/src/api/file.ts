import request from '@/utils/request'

/** 上传图片，返回相对路径如 /file/static/{tenantId}/{name} */
export async function uploadFile(file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  const data: any = await request.post('/file/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  if (typeof data === 'string') return data
  return data?.url || ''
}

/** 把后端相对路径转成可在管理端预览的地址 */
export function fileUrl(path?: string | null): string {
  if (!path) return ''
  const raw = String(path).trim()
  if (!raw) return ''
  if (raw.startsWith('blob:') || raw.startsWith('data:')) return raw

  let p = raw
  // 绝对地址：尽量改走本地 /api 代理，避免跨端口/鉴权导致「加载失败」
  if (/^https?:\/\//i.test(p)) {
    try {
      const u = new URL(p)
      p = u.pathname + u.search
    } catch {
      return raw
    }
  }
  // App 端曾把 getBaseUrl()=/api 拼进库，去掉重复前缀
  p = p.replace(/^\/api(?=\/)/, '')
  if (!p.startsWith('/')) p = `/${p}`
  return `/api${p}`
}

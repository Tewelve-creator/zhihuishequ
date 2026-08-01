import request from '@/utils/request'

export interface InspPoint {
  id: number
  name: string
  pointType?: string
  pointTypeLabel?: string
  locationDesc?: string
  address?: string
  latitude?: number
  longitude?: number
  status?: number
  qrCode?: string
  qrEnabled?: number
  qrEnabledLabel?: string
  remark?: string
  createTime?: string
}

export interface InspTask {
  id: number
  title?: string
  task?: string
  assigneeId?: number
  assigneeName?: string
  owner?: string
  cycleType?: string
  cycleLabel?: string
  startTime?: string
  endTime?: string
  requirement?: string
  remark?: string
  pointTotal?: number
  points?: number
  pointDone?: number
  done?: number
  hazardCount?: number
  status?: string
  statusLabel?: string
  pointIds?: number[]
  taskPoints?: InspTaskPoint[]
}

export interface InspTaskPoint {
  id: number
  pointId: number
  pointName?: string
  locationDesc?: string
  latitude?: number
  longitude?: number
  checked?: number
  checkinId?: number
}

export interface InspHazard {
  id: number
  taskId?: number
  taskTitle?: string
  pointName?: string
  userName?: string
  checkTime?: string
  hazardDesc?: string
  hazardStatus?: string
  photoUrls?: string
}

export interface InspCheckin {
  id: number
  taskId?: number
  taskTitle?: string
  pointName?: string
  userName?: string
  checkTime?: string
  result?: string
  resultLabel?: string
  photoUrls?: string
  photos?: string[]
  hasHazard?: number
  hazardDesc?: string
  remark?: string
}

export function listInspPoints(params?: { keyword?: string; status?: number }) {
  return request.get('/inspection/points', { params }) as Promise<InspPoint[]>
}

export function saveInspPoint(data: Partial<InspPoint>) {
  if (data.id) return request.put(`/inspection/points/${data.id}`, data) as Promise<InspPoint>
  return request.post('/inspection/points', data) as Promise<InspPoint>
}

export function deleteInspPoint(id: number) {
  return request.delete(`/inspection/points/${id}`)
}

export function regenInspPointQr(id: number) {
  return request.post(`/inspection/points/${id}/regen-qr`) as Promise<InspPoint>
}

export function listInspTasks(params?: { status?: string; keyword?: string }) {
  return request.get('/inspection/tasks', { params }) as Promise<InspTask[]>
}

export function getInspTask(id: number) {
  return request.get(`/inspection/tasks/${id}`) as Promise<InspTask>
}

export function saveInspTask(data: {
  id?: number
  title: string
  assigneeId: number
  assigneeName?: string
  cycleType: string
  startTime?: string
  endTime?: string
  requirement?: string
  remark?: string
  pointIds: number[]
}) {
  if (data.id) return request.put(`/inspection/tasks/${data.id}`, data) as Promise<InspTask>
  return request.post('/inspection/tasks', data) as Promise<InspTask>
}

export function deleteInspTask(id: number) {
  return request.delete(`/inspection/tasks/${id}`)
}

export function listInspHazards(status?: string) {
  return request.get('/inspection/hazards', { params: { status } }) as Promise<InspHazard[]>
}

export function fixInspHazard(checkinId: number) {
  return request.post(`/inspection/hazards/${checkinId}/fix`)
}

export function listInspCheckins(params?: {
  keyword?: string
  result?: string
  taskId?: number
  limit?: number
}) {
  return request.get('/inspection/checkins', { params }) as Promise<InspCheckin[]>
}

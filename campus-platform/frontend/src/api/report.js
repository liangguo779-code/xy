import request from '@/utils/request'

export function createReport(data) {
  return request.post('/api/reports', data)
}

export function getMyReports(params) {
  return request.get('/api/reports/my', { params })
}

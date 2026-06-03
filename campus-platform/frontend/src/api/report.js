import request from '@/utils/request'

export function createReport(data) {
  return request.post('/api/reports', data)
}

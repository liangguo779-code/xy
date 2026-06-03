import request from '@/utils/request'

export function createDispute(data) {
  return request.post('/api/disputes', data)
}

export function getMyDisputes(params) {
  return request.get('/api/disputes/my', { params })
}

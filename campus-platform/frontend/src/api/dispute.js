import request from '@/utils/request'

/** 发起交易纠纷 */
export function createDispute(data) {
  return request.post('/api/disputes', data)
}

/** 我的纠纷列表（params 为分页与状态筛选条件） */
export function getMyDisputes(params) {
  return request.get('/api/disputes/my', { params })
}

import request from '@/utils/request'

/** 提交举报（商品/用户/评价等，type 由后端约定） */
export function createReport(data) {
  return request.post('/api/reports', data)
}

/** 我的举报记录（params 为分页条件） */
export function getMyReports(params) {
  return request.get('/api/reports/my', { params })
}

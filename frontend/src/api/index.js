import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error?.response?.data
    const message = body?.message || error?.message || '请求失败'
    const wrapped = new Error(message)
    // 乘坐核验被拒绝时后端仍返回结构化结果（逐项核验明细），透传给页面展示
    wrapped.data = body?.data || null
    return Promise.reject(wrapped)
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    const err = new Error((body && body.message) || '请求失败')
    // 透传结构化 data（如乘坐核验的逐项结果），供页面直接渲染
    err.data = body?.data || null
    throw err
  }
  return body.data
}

const params = (o) => ({ params: o })

export default {
  // ---------------- 游乐设施 ----------------
  listRides: (p) => call(http.get('/rides', params(p))),
  createRide: (d) => call(http.post('/rides', d)),
  updateRide: (id, d) => call(http.put(`/rides/${id}`, d)),
  transferRide: (id, target) =>
    call(http.post(`/rides/${id}/transfer`, null, params({ target }))),
  boardRide: (id, heightCm, age, memberId) =>
    call(http.post(`/rides/${id}/board`, null,
      params({ heightCm, age, memberId: memberId ?? null }))),

  // ---------------- 检修安全复核与放行 ----------------
  listInspections: (rideId) => call(http.get(`/rides/${rideId}/inspections`)),
  activeInspection: (rideId) => call(http.get(`/rides/${rideId}/inspections/active`)),
  ensureInspection: (rideId) => call(http.post(`/rides/${rideId}/inspections`)),
  saveInspectionItems: (id, d) => call(http.put(`/inspections/${id}/items`, d)),
  submitInspection: (id, operator) =>
    call(http.post(`/inspections/${id}/submit`, null, params({ operator }))),
  releaseInspection: (id, operator) =>
    call(http.post(`/inspections/${id}/release`, null, params({ operator }))),

  // ---------------- 园区演出 ----------------
  listShows: () => call(http.get('/shows')),
  createShow: (d) => call(http.post('/shows', d)),
  updateShow: (id, d) => call(http.put(`/shows/${id}`, d)),
  reserveShow: (id, seats) =>
    call(http.post(`/shows/${id}/reserve`, null, params({ seats }))),

  // ---------------- 餐饮网点 ----------------
  listFnb: (p) => call(http.get('/fnb', params(p))),
  createFnb: (d) => call(http.post('/fnb', d)),
  updateFnb: (id, d) => call(http.put(`/fnb/${id}`, d)),
  transferFnb: (id, target) =>
    call(http.post(`/fnb/${id}/transfer`, null, params({ target }))),

  // ---------------- 会员 ----------------
  listMembers: (p) => call(http.get('/members', params(p))),
  // 乘坐弹窗会员选项：会员 + 共享主键卡片有效期 + 当天卡状态
  listBoardingOptions: (p) => call(http.get('/members/boarding-options', params(p))),
  createMember: (d) => call(http.post('/members', d)),

  // ---------------- 年卡 / 季卡 ----------------
  listSeasonPasses: () => call(http.get('/season-passes')),
  issueSeasonPass: (memberId, d) => call(http.post('/season-passes', d, params({ memberId }))),
  renewSeasonPass: (memberId, d) => call(http.put(`/season-passes/${memberId}`, d))
}

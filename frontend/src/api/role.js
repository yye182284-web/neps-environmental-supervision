import request from './request'
import { encryptPassword } from './security'

export async function gridLogin(data) {
  const encryptedPassword = await encryptPassword(data.password)
  return request.post('/auth/grid/login', { account: data.account, encryptedPassword })
}

export async function staffLogin(data) {
  const encryptedPassword = await encryptPassword(data.password)
  return request.post('/auth/staff/login', { username: data.username, role: data.role, encryptedPassword })
}

export const getCurrentUser = () => request.get('/auth/me')
export const logout = () => request.post('/auth/logout')
export const getLoginLogs = limit => request.get('/auth/login-logs', { params: { limit } })
export const getGridTasks = gmId => request.get(`/task/grid/${gmId}`)
export const getTaskDetail = afId => request.get(`/task/${afId}`)
export const getAvailableMembers = params => request.get('/task/available-members', { params })
export const assignTask = data => request.post('/task/assign', data)
export const acceptTask = data => request.post('/task/accept', data)
export const submitMeasurement = data => request.post('/task/measure', data)
export const getMeasurements = () => request.get('/task/measurements')
export const queryMeasurements = params => request.get('/task/measurements/query', { params })
export const getStatistics = (refresh = false) => request.get('/statistics/dashboard', { params: { refresh } })
export const getStatisticsOverview = () => request.get('/statistics/overview')
export const getGridMembers = () => request.get('/gridMember/list')
export const generateFeedbackDraft = text => request.post('/ai/public/feedback-draft', { text })
export const generateInspectionPlan = data => request.post('/ai/grid/inspection-plan', data)
export const getDispatchAdvice = afId => request.post('/ai/admin/dispatch-advice', { afId })
export const getDecisionSummary = () => request.get('/ai/decision/summary')
export const getStaffingRequests = () => request.get('/staffing/requests')
export const decideStaffingRequest = (id, data) => request.post(`/staffing/requests/${id}/decision`, data)

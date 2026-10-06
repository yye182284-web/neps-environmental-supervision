import request from './request'

export const getAqiFeedbackList = () => request.get('/aqiFeedback/select')
export const queryAqiFeedback = params => request.get('/aqiFeedback/query', { params })
export const getMyAqiFeedbackList = telId => request.get(`/aqiFeedback/history/${telId}`)
export const deleteByAfid = afId => request.get(`/aqiFeedback/delete/${afId}`)
export const saveAqiFeedback = data => request.post('/aqiFeedback/save', data)
export const getAqiFeedbackById = afId => request.get(`/aqiFeedback/find/${afId}`)
export const updateAqiFeedback = data => request.post('/aqiFeedback/update', data)
export const getProvinces = () => request.get('/province/list')
export const getCityByPid = provinceId => request.get('/province/getCitys', { params: { provinceId } })

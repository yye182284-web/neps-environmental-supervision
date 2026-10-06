import request from './request'

export const getAqiList = () => request.get('/aqi/list')
export const getAqiById = aqiId => request.get(`/aqi/find/${aqiId}`)
export const saveAqi = data => request.post('/aqi/save', data)
export const updateAqi = data => request.post('/aqi/update', data)
export const deleteAqi = aqiId => request.get(`/aqi/delete/${aqiId}`)

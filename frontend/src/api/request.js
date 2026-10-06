import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  // 历史环比看板包含数千条数据聚合，给统计请求留出合理执行时间。
  timeout: 30000,
  withCredentials: true
})

request.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('neps-session-expired'))
    }
    return Promise.reject(error)
  }
)

export default request

import axios from 'axios'

export const api = axios.create({ baseURL: '/api/v1' })
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(undefined, (error) => {
  if (error.response?.status === 401 && location.pathname !== '/login') {
    localStorage.removeItem('accessToken')
    location.href = '/login'
  }
  return Promise.reject(error)
})



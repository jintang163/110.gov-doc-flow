import axios from 'axios'
import { message } from 'antd'

/** 统一响应：code=0 成功；401 跳登录；其余弹错误提示 */
const client = axios.create({ baseURL: '/api', timeout: 60000 })

client.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

client.interceptors.response.use(
  (res) => {
    const d = res.data
    if (d && typeof d.code === 'number') {
      if (d.code === 0) return d.data
      if (d.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        if (location.pathname !== '/login') location.href = '/login'
        throw new Error(d.message)
      }
      message.error(d.message || '操作失败')
      throw new Error(d.message)
    }
    return d
  },
  (err) => {
    message.error(err.response?.data?.message || err.message || '网络错误')
    throw err
  },
)

export default client

export function fileUrl(key: string) {
  return `/api/files/${key}?token=${localStorage.getItem('token')}`
}

export function docPdfUrl(id: number) {
  return `/api/docs/${id}/pdf?token=${localStorage.getItem('token')}`
}

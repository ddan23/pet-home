import { API_BASE, notifyUnauthorized } from './apiBase'

export class ApiError extends Error {
  constructor(message, code, status, data) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
  }
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (!(options.body instanceof FormData) && options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method: options.method || 'GET',
      headers,
      credentials: 'include',
      body: options.body instanceof FormData ? options.body : options.body === undefined
        ? undefined
        : JSON.stringify(options.body)
    })
    const text = await response.text()
    let payload = {}
    if (text) {
      try {
        payload = JSON.parse(text)
      } catch (error) {
        // 非 JSON 响应统一按文本错误处理，保留原始状态码。
        payload = { code: response.status, msg: text }
      }
    }
    if (response.status === 401 && !options.suppressUnauthorized) {
      notifyUnauthorized()
    }
    if (!response.ok || (payload.code && payload.code >= 400)) {
      throw new ApiError(payload.msg || '请求失败', payload.code || response.status,
        response.status, payload.data)
    }
    return payload
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('网络连接异常，请稍后重试', -1, 0)
  }
}

export const api = {
  get: (path, options = {}) => request(path, options),
  post: (path, body = {}, options = {}) => request(path, {
    ...options,
    method: 'POST',
    body
  }),
  put: (path, body = {}, options = {}) => request(path, {
    ...options,
    method: 'PUT',
    body
  }),
  delete: (path, options = {}) => request(path, {
    ...options,
    method: 'DELETE'
  }),
  download: async (path, options = {}) => {
    try {
      const response = await fetch(`${API_BASE}${path}`, {
        method: options.method || 'GET',
        headers: new Headers(options.headers || {}),
        credentials: 'include'
      })
      if (response.status === 401 && !options.suppressUnauthorized) {
        notifyUnauthorized()
      }
      if (!response.ok) {
        const text = await response.text()
        let payload = {}
        try {
          payload = text ? JSON.parse(text) : {}
        } catch (error) {
          payload = { msg: text }
        }
        throw new ApiError(payload.msg || '文件下载失败', payload.code || response.status,
          response.status, payload.data)
      }
      const disposition = response.headers.get('Content-Disposition') || ''
      const filenameMatch = disposition.match(/filename="?([^";]+)"?/i)
      return {
        blob: await response.blob(),
        filename: filenameMatch?.[1] || ''
      }
    } catch (error) {
      if (error instanceof ApiError) throw error
      throw new ApiError('网络连接异常，请稍后重试', -1, 0)
    }
  },
  upload: (path, file, options = {}) => {
    const form = new FormData()
    form.append('file', file)
    return request(path, {
      ...options,
      method: 'POST',
      body: form
    })
  }
}

export function assetUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path) || path.startsWith('data:')) return path
  return `${import.meta.env.BASE_URL.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`
}

export function queryString(params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, value)
  })
  const text = query.toString()
  return text ? `?${text}` : ''
}

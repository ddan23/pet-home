import { API_BASE, notifyUnauthorized } from './apiBase'

export class AiStreamError extends Error {
  constructor(message, code = 'AI_STREAM_ERROR') {
    super(message)
    this.name = 'AiStreamError'
    this.code = code
  }
}

/**
 * 通过 SSE 接收 AI 流式响应。
 *
 * @param {string} path 接口路径
 * @param {object} payload 请求参数
 * @param {{ signal?: AbortSignal, onEvent: Function }} options 流式选项
 */
export async function streamAi(path, payload, options) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload),
    signal: options.signal
  })
  if (response.status === 401) {
    notifyUnauthorized()
  }
  if (!response.ok) {
    throw await toStreamError(response)
  }
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new AiStreamError('AI 服务未返回流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      buffer = buffer.replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        dispatchEventBlock(block, options.onEvent)
        boundary = buffer.indexOf('\n\n')
      }
      if (done) break
    }
    if (buffer.trim()) {
      dispatchEventBlock(buffer, options.onEvent)
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      await reader.cancel().catch(() => {})
    }
    throw error
  } finally {
    reader.releaseLock()
  }
}

function dispatchEventBlock(block, onEvent) {
  if (!block.trim() || block.trimStart().startsWith(':')) return
  let eventName = 'message'
  let eventId = ''
  const dataLines = []
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      return
    }
    if (line.startsWith('id:')) {
      eventId = line.slice(3).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  })
  const text = dataLines.join('\n')
  let data = {}
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { text }
    }
  }
  if (eventName === 'error') {
    throw new AiStreamError(data.message || data.msg || 'AI 生成失败', data.code)
  }
  onEvent({
    event: eventName,
    id: eventId,
    data
  })
}

async function toStreamError(response) {
  const text = await response.text()
  if (!text) {
    return new AiStreamError('AI 请求失败', String(response.status))
  }
  try {
    const payload = JSON.parse(text)
    return new AiStreamError(
      payload.msg || payload.message || 'AI 请求失败',
      payload.code || String(response.status)
    )
  } catch {
    return new AiStreamError(text, String(response.status))
  }
}

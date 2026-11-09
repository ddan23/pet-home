export function formatPrice(value) {
  return Number(value || 0).toFixed(2)
}

export function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

export function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

export function relativeTime(value) {
  if (!value) return ''
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return String(value)
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}小时前`
  if (diff < 604_800_000) return `${Math.floor(diff / 86_400_000)}天前`
  return formatDate(value)
}

export function parseJson(value, fallback = []) {
  if (Array.isArray(value) || (value && typeof value === 'object')) return value
  if (!value) return fallback
  try {
    return JSON.parse(value)
  } catch (error) {
    // 历史数据可能不是合法 JSON，调用方使用安全默认值。
    return fallback
  }
}

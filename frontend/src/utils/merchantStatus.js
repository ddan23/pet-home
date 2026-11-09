const SHOP_STATUS = Object.freeze({
  open: { label: '营业中', tone: 'success' },
  pending_audit: { label: '审核中', tone: 'warning' },
  rejected: { label: '审核未通过', tone: 'danger' },
  closed: { label: '已关闭', tone: 'muted' }
})

const PRODUCT_STATUS = Object.freeze({
  on: { label: '在售', tone: 'success' },
  off: { label: '已下架', tone: 'muted' }
})

const ORDER_STATUS = Object.freeze({
  pending_payment: { label: '待支付', tone: 'warning' },
  paid: { label: '待发货', tone: 'warning' },
  shipped: { label: '已发货', tone: 'info' },
  completed: { label: '已完成', tone: 'success' },
  cancelled: { label: '已取消', tone: 'muted' }
})

const APPOINTMENT_STATUS = Object.freeze({
  confirmed: { label: '待服务', tone: 'warning' },
  completed: { label: '已完成', tone: 'success' },
  cancelled: { label: '已取消', tone: 'muted' }
})

function resolveStatus(statusMap, value) {
  return statusMap[value] || { label: '未知状态', tone: 'muted' }
}

export function shopStatus(status) {
  return resolveStatus(SHOP_STATUS, status)
}

export function productStatus(status) {
  return resolveStatus(PRODUCT_STATUS, status)
}

export function orderStatus(status) {
  return resolveStatus(ORDER_STATUS, status)
}

export function appointmentStatus(status) {
  return resolveStatus(APPOINTMENT_STATUS, status)
}

export function parseOrderItems(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'object') return value.items || []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : parsed?.items || []
  } catch (error) {
    return []
  }
}

export function shippingAddressSummary(value) {
  if (!value) return '收货信息待确认'
  let address = value
  if (typeof value === 'string') {
    try {
      address = JSON.parse(value)
    } catch (error) {
      return value
    }
  }
  if (!address || typeof address !== 'object') return '收货信息待确认'
  return [
    address.name || address.receiver,
    address.phone,
    address.region || address.city,
    address.detail || address.address
  ].filter(Boolean).join(' ') || '收货信息待确认'
}

export function orderItemSummary(order) {
  const items = parseOrderItems(order?.items)
  if (!items.length) return '商品明细待确认'
  return items
    .map((item) => `${item.productName || item.name || '商品'} × ${item.num || item.quantity || 1}`)
    .join('、')
}

// 服务预约按项目计数没有意义，数量为 1 时不显示 ×1
export function orderServiceSummary(order) {
  const items = parseOrderItems(order?.items)
  if (!items.length) return '服务项目待确认'
  return items
    .map((item) => {
      const name = item.productName || item.name || '服务项目'
      const quantity = Number(item.num || item.quantity || 1)
      return quantity > 1 ? `${name} × ${quantity}` : name
    })
    .join('、')
}

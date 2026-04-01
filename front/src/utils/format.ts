export function formatCurrency(value: number | string | null | undefined) {
  const amount = Number(value ?? 0)
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

export function formatDateTime(value: string | Date | null | undefined) {
  if (!value) {
    return '-'
  }
  const date = value instanceof Date ? value : new Date(value)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatOrderStatus(status: string) {
  if (status === 'SUCCESS') {
    return '已成功'
  }
  if (status === 'CLOSED') {
    return '已关闭'
  }
  return status
}

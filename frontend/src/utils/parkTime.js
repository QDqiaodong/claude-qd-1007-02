// 园区演出时刻的唯一展示口径：后端已把历史值（带/不带偏移量）统一解释为
// 园区固定时区 Asia/Shanghai 的挂钟值，前端只做纯字符串拆分/格式化，
// 绝不 new Date(...) 或调用 toLocaleString —— 因此客户端处于夏令时地区、
// 系统时区变化或前后端默认时区不一致时，展示结果完全相同。

// 解析后端给出的 ISO 挂钟值（YYYY-MM-DDTHH:mm[:ss]，不带偏移量）；
// 纯字符串解析，不经过本地时区。
export function splitParkWallText(text) {
  if (!text || typeof text !== 'string') return null
  const m = text.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?$/)
  if (!m) return null
  return { date: m[1], hour: m[2], minute: m[3], second: m[4] || '00' }
}

// 优先使用后端显式给出的园区日期/时间字段；缺失时再从挂钟文本拆分。
// 未排期（无时刻）统一返回 null，由页面渲染占位文案。
export function parkDate(row) {
  if (!row) return null
  if (row.parkDate) return row.parkDate
  const p = splitParkWallText(row.showAt)
  return p ? p.date : null
}

export function parkTime(row) {
  if (!row) return null
  if (row.parkTime) return row.parkTime
  const p = splitParkWallText(row.showAt)
  return p ? `${p.hour}:${p.minute}` : null
}

export const PARK_ZONE_LABEL = 'UTC+08:00（上海）'

// 供测试与页面复用：把一行演出渲染成稳定的「日期 时间」展示文本。
export function formatParkDateTime(row) {
  const d = parkDate(row)
  const t = parkTime(row)
  if (!d && !t) return '待排期'
  return `${d} ${t}`
}

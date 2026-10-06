/**
 * 园区演出列表的纯展示工具。
 *
 * 关键约定：园区日期、园区时间与列表次序都由后端按园区固定时区
 * （Asia/Shanghai，无夏令时）解释后随接口给出，前端只做纯文本展示，
 * 绝不使用浏览器时区对这些字段再做 new Date()/toLocaleString 换算——
 * 否则不同客户端时区会把同一场演出显示成不同的园区日期。
 *
 * 无时刻（scheduled=false）的记录由后端统一排在末尾，这里仅提供展示占位。
 */

export const EMPTY_DATE_PLACEHOLDER = '—'
export const EMPTY_TIME_PLACEHOLDER = '未排期'

/** 园区日期文本；无时刻返回占位符 */
export function parkDateText(row) {
  return row && row.scheduled ? row.parkDate : EMPTY_DATE_PLACEHOLDER
}

/** 园区时间文本；无时刻返回“未排期” */
export function parkTimeText(row) {
  return row && row.scheduled ? row.parkTime : EMPTY_TIME_PLACEHOLDER
}

/** 完整园区时刻（日期 + 时间）；无时刻返回空串 */
export function parkDateTimeText(row) {
  if (!row || !row.scheduled) return ''
  return [row.parkDate, row.parkTime].filter(Boolean).join(' ')
}

/**
 * 悬停追溯文案：说明该场演出的原始历史值与解释方式。
 * BARE=原值不带偏移量，按园区挂钟原样解释；否则原值带偏移量，已据此换算。
 */
export function sourceTip(row) {
  if (!row || !row.scheduled) {
    return '该演出未登记时刻，统一排在列表末尾'
  }
  if (row.timeOffset === 'BARE') {
    return `原值无时区偏移：${row.timeSource}，按园区时间（上海）原样解释`
  }
  return `原值：${row.timeSource}（偏移 ${row.timeOffset}），已按偏移换算为园区时间（上海）`
}

/**
 * 前端兜底排序（正常情况下后端已排好序，这里保证即使数据源乱序也能呈现同样的
 * 确定次序）：有园区时刻者按 parkDateTime 升序 → 编号升序；无时刻置末。
 * 不解析时间、不触碰浏览器时区，仅比较后端给出的规范化文本与编号。
 */
export function sortShowRows(rows) {
  return [...(rows || [])].sort((a, b) => {
    const aHas = !!(a && a.scheduled && a.parkDateTime)
    const bHas = !!(b && b.scheduled && b.parkDateTime)
    if (aHas !== bHas) return aHas ? -1 : 1
    if (aHas) {
      const byTime = a.parkDateTime.localeCompare(b.parkDateTime)
      if (byTime !== 0) return byTime
    }
    return String(a?.showCode ?? '').localeCompare(String(b?.showCode ?? ''))
  })
}

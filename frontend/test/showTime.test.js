import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  EMPTY_DATE_PLACEHOLDER,
  EMPTY_TIME_PLACEHOLDER,
  parkDateText,
  parkDateTimeText,
  parkTimeText,
  sortShowRows,
  sourceTip
} from '../src/views/showTime.js'

/**
 * 园区演出列表前端纯函数的可重复验证（node --test，无需浏览器/第三方依赖）。
 *
 * 这些用例锁定前后端契约：页面只渲染后端给出的园区日期/时间与顺序，
 * 任何客户端时区运行这些用例结果都相同（纯字符串，不 new Date()）。
 */

function row(code, parkDate, parkTime, extra = {}) {
  return {
    showCode: code,
    showName: `演出${code}`,
    scheduled: extra.scheduled ?? true,
    parkDate,
    parkTime,
    parkDateTime: parkDate && parkTime ? `${parkDate} ${parkTime}` : null,
    timeSource: extra.timeSource,
    timeOffset: extra.timeOffset
  }
}

test('空时间：无时刻记录展示占位与“未排期”，且不参与时刻文本拼接', () => {
  const none = { showCode: 'S-NULL', scheduled: false }
  assert.equal(parkDateText(none), EMPTY_DATE_PLACEHOLDER)
  assert.equal(parkTimeText(none), EMPTY_TIME_PLACEHOLDER)
  assert.equal(parkDateTimeText(none), '')
  assert.match(sourceTip(none), /未登记时刻/)
})

test('普通日期：园区日期与时间按后端文本原样展示，不做任何换算', () => {
  const r = row('S-001', '2024-02-28', '10:00', {
    timeSource: '2024-02-28T10:00:00', timeOffset: 'BARE'
  })
  assert.equal(parkDateText(r), '2024-02-28')
  assert.equal(parkTimeText(r), '10:00')
  assert.equal(parkDateTimeText(r), '2024-02-28 10:00')
  assert.match(sourceTip(r), /BARE|无时区偏移/)
})

test('跨日边界：UTC 历史值换算后的园区 00:30 归 3 月 1 日（由后端字段体现）', () => {
  const r = row('S-XDAY', '2024-03-01', '00:30', {
    timeSource: '2024-02-29T16:30:00Z', timeOffset: 'Z'
  })
  assert.equal(parkDateText(r), '2024-03-01')
  assert.equal(parkTimeText(r), '00:30')
  assert.match(sourceTip(r), /16:30:00Z/)
})

test('闰日：不带偏移量的 2024-02-29 晚场仍是园区闰日', () => {
  const leap = row('S-LEAP', '2024-02-29', '20:00', {
    timeSource: '2024-02-29T20:00:00', timeOffset: 'BARE'
  })
  assert.equal(parkDateText(leap), '2024-02-29')
  assert.equal(parkTimeText(leap), '20:00')
})

test('排序：按解释后的园区时刻从早到晚，无时刻置末', () => {
  const none1 = { showCode: 'S-Z1', scheduled: false }
  const none2 = { showCode: 'S-Z2', scheduled: false }
  const early = row('S-A', '2024-02-28', '10:00')
  const leap = row('S-B', '2024-02-29', '20:00')
  const crossDay = row('S-C', '2024-03-01', '00:30')
  const later = row('S-D', '2024-03-02', '19:00')

  // 故意乱序输入（含无时刻）
  const shuffled = [later, none1, crossDay, leap, none2, early]
  const codes = sortShowRows(shuffled).map((r) => r.showCode)
  assert.deepEqual(codes, ['S-A', 'S-B', 'S-C', 'S-D', 'S-Z1', 'S-Z2'])
})

test('相同时刻：按演出编号形成确定先后，可重复', () => {
  const a = row('S-10', '2024-03-01', '00:30')
  const b = row('S-03', '2024-03-01', '00:30')
  const c = row('S-04', '2024-03-01', '00:30')
  const first = sortShowRows([c, a, b]).map((r) => r.showCode)
  const second = sortShowRows([a, b, c]).map((r) => r.showCode)
  assert.deepEqual(first, ['S-03', 'S-04', 'S-10'])
  assert.deepEqual(second, first)
})

test('带/不带偏移量的同瞬间场次：后端给出相同园区时刻，前端并排且按编号定序', () => {
  const fromZ = row('S-OFF-Z', '2024-03-01', '00:30', {
    timeSource: '2024-02-29T16:30:00Z', timeOffset: 'Z'
  })
  const fromMinus = row('S-OFF-M', '2024-03-01', '00:30', {
    timeSource: '2024-02-29T11:30:00-05:00', timeOffset: '-05:00'
  })
  const sorted = sortShowRows([fromZ, fromMinus]).map((r) => r.showCode)
  assert.deepEqual(sorted, ['S-OFF-M', 'S-OFF-Z'])
})

test('排序函数不修改入参数组（无副作用）', () => {
  const a = row('S-A', '2024-02-28', '10:00')
  const b = row('S-B', '2024-03-01', '00:30')
  const input = [b, a]
  sortShowRows(input)
  assert.deepEqual(input.map((r) => r.showCode), ['S-B', 'S-A'])
})

test('空列表与异常输入安全返回', () => {
  assert.deepEqual(sortShowRows([]), [])
  assert.deepEqual(sortShowRows(undefined), [])
  assert.equal(parkDateText(null), EMPTY_DATE_PLACEHOLDER)
  assert.equal(parkTimeText(undefined), EMPTY_TIME_PLACEHOLDER)
})

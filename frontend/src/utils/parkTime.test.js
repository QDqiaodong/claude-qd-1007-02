// 园区演出时刻格式化的可重复验证（零依赖，node --test 运行）。
//
// 关键点：parkTime.js 只做纯字符串处理，绝不构造 Date 对象，因此其输出
// 与客户端时区/夏令时无关。本文件除直接断言格式规则外，还以多个 TZ
// （UTC、上海、洛杉矶夏令时、奥克兰）启动子进程重复同一批断言，
// 证明客户端处于夏令时地区或系统时区变化时展示结果完全相同。
//
// 运行：npm test（见 package.json）

import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { describe, it } from 'node:test'

import { splitParkWallText, parkDate, parkTime, formatParkDateTime, PARK_ZONE_LABEL } from './parkTime.js'

const here = path.dirname(fileURLToPath(import.meta.url))
const childScript = path.join(here, 'parkTime.selfcheck.mjs')

// 一组覆盖普通日期、跨日边界、闰日、空时间的园区挂钟值
const CASES = [
  { row: { showAt: '2028-03-03T07:30:00' }, date: '2028-03-03', time: '07:30' },
  { row: { showAt: '2028-02-29T10:30:00' }, date: '2028-02-29', time: '10:30' }, // 闰日
  { row: { showAt: '2028-03-03T00:00:00' }, date: '2028-03-03', time: '00:00' }, // 午夜边界
  { row: { showAt: '2028-03-02T23:59:59' }, date: '2028-03-02', time: '23:59' }
]

describe('parkTime 纯字符串格式化（不构造 Date，客户端时区/DST 无关）', () => {
  it('拆分 ISO 挂钟值：日期与时间正确，秒不影响 HH:mm', () => {
    for (const c of CASES) {
      assert.equal(parkDate(c.row), c.date)
      assert.equal(parkTime(c.row), c.time)
    }
  })

  it('优先使用后端显式 parkDate/parkTime 字段', () => {
    const row = { parkDate: '2028-03-03', parkTime: '07:30', showAt: 'OTHER' }
    assert.equal(parkDate(row), '2028-03-03')
    assert.equal(parkTime(row), '07:30')
  })

  it('空格分隔的挂钟文本也能拆分', () => {
    assert.deepEqual(splitParkWallText('2028-03-03 07:30:00'),
      { date: '2028-03-03', hour: '07', minute: '30', second: '00' })
  })

  it('空时间：日期/时间为空，完整文本渲染为「待排期」', () => {
    for (const row of [null, undefined, {}, { showAt: null }, { showAt: '' }, { showAt: 'junk' }]) {
      assert.equal(parkDate(row), null)
      assert.equal(parkTime(row), null)
      assert.equal(formatParkDateTime(row), '待排期')
    }
  })

  it('完整文本稳定为「日期 时间」，时区标签固定为上海 UTC+08:00', () => {
    assert.equal(formatParkDateTime({ showAt: '2028-03-03T07:30:00' }), '2028-03-03 07:30')
    assert.match(PARK_ZONE_LABEL, /UTC\+08:00/)
  })

  it('跨客户端时区重复运行：UTC / 上海 / 洛杉矶夏令时 / 奥克兰 输出逐字节相同', () => {
    const zones = ['UTC', 'Asia/Shanghai', 'America/Los_Angeles', 'Pacific/Auckland']
    const outputs = zones.map((tz) => {
      const r = spawnSync(process.execPath, [childScript], {
        env: { ...process.env, TZ: tz },
        encoding: 'utf8'
      })
      assert.equal(r.status, 0, `TZ=${tz} 自检失败：\n${r.stderr}\n${r.stdout}`)
      return r.stdout
    })
    for (const out of outputs) {
      assert.equal(out, outputs[0], `时区 ${zones[outputs.indexOf(out)]} 与 UTC 的输出不一致`)
    }
    // 基准内容：普通日期 / 闰日 / 午夜 / 跨日 / 未排期
    const expected = [
      '2028-03-03|07:30',
      '2028-02-29|10:30',
      '2028-03-03|00:00',
      '2028-03-02|23:59',
      'null|null|待排期'
    ].join('\n') + '\n'
    assert.equal(outputs[0], expected)
  })
})

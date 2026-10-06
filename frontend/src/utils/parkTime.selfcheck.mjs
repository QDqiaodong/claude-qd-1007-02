// 子进程自检：在父进程指定的 TZ 下打印园区时刻格式化结果。
// 由 parkTime.test.js 以多个客户端时区分别拉起，逐字节比对 stdout。
import { parkDate, parkTime, formatParkDateTime } from './parkTime.js'

const rows = [
  { showAt: '2028-03-03T07:30:00' },
  { showAt: '2028-02-29T10:30:00' }, // 闰日
  { showAt: '2028-03-03T00:00:00' }, // 午夜边界
  { showAt: '2028-03-02T23:59:59' }, // 跨日前一刻
  { showAt: null }                   // 未排期
]

for (const row of rows) {
  const d = parkDate(row)
  const t = parkTime(row)
  if (!d && !t) {
    process.stdout.write(`null|null|${formatParkDateTime(row)}\n`)
  } else {
    process.stdout.write(`${d}|${t}\n`)
  }
}

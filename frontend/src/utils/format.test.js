import { describe, expect, it } from 'vitest'
import { coverStyle, formatCount, formatDate, formatDuration, sortByTime } from './format'

describe('formatCount', () => {
  it('小于一万时原样显示', () => {
    expect(formatCount(999)).toBe('999')
    expect(formatCount(9999)).toBe('9999')
  })

  it('一万以上换算成「万」', () => {
    expect(formatCount(10000)).toBe('1.0万')
    expect(formatCount(123456)).toBe('12.3万')
  })

  it('一亿以上换算成「亿」', () => {
    expect(formatCount(100000000)).toBe('1.0亿')
    expect(formatCount(250000000)).toBe('2.5亿')
  })

  it('非法输入不会崩', () => {
    expect(formatCount(null)).toBe('0')
    expect(formatCount(undefined)).toBe('0')
    expect(formatCount('abc')).toBe('0')
  })
})

describe('formatDuration', () => {
  it('一小时以内是 mm:ss', () => {
    expect(formatDuration(0)).toBe('00:00')
    expect(formatDuration(65)).toBe('01:05')
    expect(formatDuration(599)).toBe('09:59')
  })

  it('超过一小时是 h:mm:ss', () => {
    expect(formatDuration(3600)).toBe('1:00:00')
    expect(formatDuration(3725)).toBe('1:02:05')
  })

  it('负数会兜底成 00:00', () => {
    expect(formatDuration(-10)).toBe('00:00')
  })
})

describe('formatDate', () => {
  it('只保留到分钟', () => {
    expect(formatDate('2024-11-08 20:15:33')).toBe('2024-11-08 20:15')
  })

  it('空值返回空字符串', () => {
    expect(formatDate('')).toBe('')
    expect(formatDate(null)).toBe('')
  })
})

describe('coverStyle', () => {
  it('把两个颜色拼成渐变背景', () => {
    const style = coverStyle({ coverColor1: '#ffffff', coverColor2: '#000000' })
    expect(style.backgroundImage).toContain('#ffffff')
    expect(style.backgroundImage).toContain('#000000')
  })

  it('没有颜色时用默认粉色兜底', () => {
    const style = coverStyle({})
    expect(style.backgroundImage).toContain('#FB7299')
  })
})

describe('sortByTime', () => {
  it('按出现时间排序，且不改变原数组', () => {
    const input = [{ time: 5 }, { time: 1 }, { time: 3 }]
    expect(sortByTime(input).map((d) => d.time)).toEqual([1, 3, 5])
    expect(input.map((d) => d.time)).toEqual([5, 1, 3])
  })

  it('空数组也安全', () => {
    expect(sortByTime()).toEqual([])
  })
})

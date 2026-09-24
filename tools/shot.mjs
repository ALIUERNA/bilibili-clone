import fs from 'node:fs'
import path from 'node:path'
import { openBrowser, sleep } from './cdp.mjs'

const BASE = 'http://localhost:8080'
const OUT = path.resolve('shots/preview')
fs.mkdirSync(OUT, { recursive: true })

const pages = JSON.parse(process.argv[2] || '[]')
const width = Number(process.argv[3] || 1680)
const height = Number(process.argv[4] || 1050)

const b = await openBrowser({ port: 9235, profile: '.chrome-shot', width, height })

async function shoot(name, url, { full = false, wait = 2200 } = {}) {
  await b.goto(BASE + url, wait)
  await b.eval('window.scrollTo(0,0)')
  await sleep(400)
  const args = { format: 'png' }
  if (full) args.captureBeyondViewport = true
  const { data } = await b.cdp.send('Page.captureScreenshot', args, b.sessionId)
  fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(data, 'base64'))
  console.log('✓', name)
}

for (const p of pages) await shoot(p.name, p.url, p)

if (b.errors.length) console.log('页面错误:', b.errors.slice(0, 10))
b.close()
process.exit(0)

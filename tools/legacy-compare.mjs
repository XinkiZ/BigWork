#!/usr/bin/env node
/**
 * 遗留课程目录访问性能对比脚本：直连（direct） vs 中间层缓存（cached）
 *
 * 用法：node tools/legacy-compare.mjs
 * 前置：后端已启动，注册管理员与演示学生账号可用
 *
 * 该脚本会自动：
 *   1) 切换到 cached 模式 → 测缓存命中的目录访问耗时
 *   2) 手动刷新缓存（回源遗留库）→ 测首次回源耗时
 *   3) 切换到 direct 模式 → 测每次直连遗留库的耗时
 *   4) 输出对比结论（加速比），并把结果写入 tools/legacy-compare-result.json
 */
import { performance } from 'node:perf_hooks'
import { writeFileSync } from 'node:fs'

const BASE = process.env.API_BASE || 'http://localhost:8080'
const OUT = process.env.OUT_FILE || 'tools/legacy-compare-result.json'

async function call(path, { method = 'GET', token, body } = {}) {
  const started = performance.now()
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {})
    },
    ...(body ? { body: JSON.stringify(body) } : {})
  })
  const text = await response.text()
  return { ok: response.ok, status: response.status, text, cost: +(performance.now() - started).toFixed(1) }
}

async function login(username, password) {
  const response = await call('/api/auth/login', { method: 'POST', body: { username, password } })
  if (!response.ok) throw new Error(`登录失败：${username} → ${response.text}`)
  return JSON.parse(response.text).token
}

function row(label, value, note = '') {
  console.log(`${label.padEnd(34, ' ')}${String(value).padStart(12, ' ')}   ${note}`)
}

async function main() {
  console.log('==============================================================')
  console.log(' 遗留课程目录访问性能对比（中间层优化效果验证）')
  console.log(` 后端：${BASE}`)
  console.log('==============================================================')

  const studentToken = await login('2026001', '123456')
  const registrarToken = await login('registrar', 'admin123')

  // 1) 直连模式：每次访问都回源遗留库
  await call('/api/system/legacy-mode?mode=direct', { method: 'POST', token: registrarToken })
  const direct1 = await call('/api/student/catalog', { token: studentToken })
  const direct2 = await call('/api/student/catalog', { token: studentToken })

  // 2) 缓存模式：首次回源 + 后续命中缓存
  await call('/api/system/legacy-mode?mode=cached', { method: 'POST', token: registrarToken })
  const cold = await call('/api/system/cache/refresh', { method: 'POST', token: registrarToken })
  const cached1 = await call('/api/student/catalog', { token: studentToken })
  const cached2 = await call('/api/student/catalog', { token: studentToken })

  // 3) 汇总
  console.log('')
  row('场景', '耗时(ms)', '说明')
  console.log('-'.repeat(72))
  row('直连遗留库 第 1 次', direct1.cost, `HTTP ${direct1.status}`)
  row('直连遗留库 第 2 次', direct2.cost, `HTTP ${direct2.status}`)
  row('中间层缓存 回源刷新', cold.cost, `加载 ${JSON.parse(cold.text).loadMillis} ms（模拟遗留延迟）`)
  row('中间层缓存 命中 第 1 次', cached1.cost, `HTTP ${cached1.status}`)
  row('中间层缓存 命中 第 2 次', cached2.cost, `HTTP ${cached2.status}`)
  console.log('-'.repeat(72))

  const directAvg = +((direct1.cost + direct2.cost) / 2).toFixed(1)
  const cachedAvg = +((cached1.cost + cached2.cost) / 2).toFixed(1)
  const speedup = +(directAvg / Math.max(cachedAvg, 0.1)).toFixed(1)

  row('直连平均耗时', directAvg, '题目要求 ≤ 10000 ms')
  row('缓存平均耗时', cachedAvg, '毫秒级')
  row('加速比', `${speedup} 倍`, '')

  const payload = {
    generatedAt: new Date().toISOString(),
    base: BASE,
    direct: { first: direct1.cost, second: direct2.cost, average: directAvg },
    cached: { refresh: cold.cost, first: cached1.cost, second: cached2.cost, average: cachedAvg },
    speedup
  }
  writeFileSync(OUT, JSON.stringify(payload, null, 2), 'utf8')
  console.log('==============================================================')
  console.log(`结论：中间层缓存将遗留目录访问从约 ${directAvg} ms 降低到约 ${cachedAvg} ms（约 ${speedup} 倍）。`)
  console.log(`结果已写入：${OUT}`)
}

main().catch((error) => {
  console.error(`[错误] ${error.message}`)
  console.error('请确认后端已启动且数据库已初始化。')
  process.exit(1)
})
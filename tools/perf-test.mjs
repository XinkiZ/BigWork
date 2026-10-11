#!/usr/bin/env node
/**
 * 性能测试脚本（Node 内置 fetch，无第三方依赖）
 *
 * 用法：
 *   node tools/perf-test.mjs                        # 默认 50/100/200 并发，测试学生课程目录
 *   node tools/perf-test.mjs --endpoint=student/schedule
 *   node tools/perf-test.mjs --levels=10,50 --rounds=5
 *   node tools/perf-test.mjs --out=tools/perf-result.json
 *
 * 说明：本机为开发机（单进程后端 + 单实例 MySQL），用于验证系统在本机环境的响应能力；
 *      题目要求的外推指标（2000 并发访问中央数据库、500 并发访问本地服务器）见测试报告。
 */
import { performance } from 'node:perf_hooks'
import { writeFileSync } from 'node:fs'

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [key, value = 'true'] = a.replace(/^--/, '').split('=')
    return [key, value]
  })
)

const BASE = args.base || process.env.API_BASE || 'http://localhost:8080'
const ENDPOINT = args.endpoint || 'student/catalog'
const LEVELS = (args.levels || '50,100,200').split(',').map((n) => Number(n.trim())).filter(Boolean)
const ROUNDS = Number(args.rounds || 3)
const TIMEOUT_MS = Number(args.timeout || 60000)
const OUT = args.out || 'tools/perf-result.json'

async function request(path, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const started = performance.now()
  try {
    const response = await fetch(`${BASE}${path}`, { ...options, signal: controller.signal })
    const text = await response.text()
    return { ok: response.ok, status: response.status, text, cost: performance.now() - started }
  } catch (error) {
    return { ok: false, status: 0, text: String(error), cost: performance.now() - started }
  } finally {
    clearTimeout(timer)
  }
}

async function login() {
  const response = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: '2026001', password: '123456' })
  })
  if (!response.ok) {
    throw new Error(`登录失败（${response.status}）：${response.text}`)
  }
  return JSON.parse(response.text).token
}

function percentile(sorted, ratio) {
  if (!sorted.length) return 0
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)
  return sorted[index]
}

async function runRound(token, concurrency) {
  const started = performance.now()
  const tasks = Array.from({ length: concurrency }, () =>
    request(`/api/${ENDPOINT}`, { headers: { Authorization: `Bearer ${token}` } })
  )
  const results = await Promise.all(tasks)
  const wall = performance.now() - started
  const costs = results.map((r) => r.cost).sort((a, b) => a - b)
  const success = results.filter((r) => r.ok).length
  return {
    concurrency,
    success,
    failed: concurrency - success,
    successRate: +(success / concurrency).toFixed(4),
    min: +costs[0].toFixed(1),
    p50: +percentile(costs, 0.5).toFixed(1),
    p95: +percentile(costs, 0.95).toFixed(1),
    max: +costs[costs.length - 1].toFixed(1),
    avg: +(costs.reduce((a, b) => a + b, 0) / costs.length).toFixed(1),
    wallMs: +wall.toFixed(1),
    qps: +((concurrency / wall) * 1000).toFixed(1)
  }
}

function pad(value, width) {
  return String(value).padEnd(width, ' ')
}

async function main() {
  console.log('==============================================================')
  console.log(' Wylie College 选课注册系统 —— 并发性能测试')
  console.log(` 目标：${BASE}/api/${ENDPOINT}   并发等级：${LEVELS.join(' / ')}   轮次：${ROUNDS}`)
  console.log('==============================================================')

  let token
  try {
    token = await login()
  } catch (error) {
    console.error(`[错误] ${error.message}`)
    console.error('请确认后端已启动（mvn spring-boot:run）且数据库已初始化。')
    process.exit(1)
  }

  // 预热一次，避免首次请求的额外开销影响结果
  await request(`/api/${ENDPOINT}`, { headers: { Authorization: `Bearer ${token}` } })

  const summary = []
  for (const level of LEVELS) {
    const rounds = []
    for (let i = 0; i < ROUNDS; i += 1) {
      rounds.push(await runRound(token, level))
    }
    const aggregate = {
      concurrency: level,
      successRate: +(rounds.reduce((a, r) => a + r.successRate, 0) / rounds.length).toFixed(4),
      p50: +Math.max(...rounds.map((r) => r.p50)).toFixed(1),
      p95: +Math.max(...rounds.map((r) => r.p95)).toFixed(1),
      max: +Math.max(...rounds.map((r) => r.max)).toFixed(1),
      avg: +(rounds.reduce((a, r) => a + r.avg, 0) / rounds.length).toFixed(1),
      qps: +(rounds.reduce((a, r) => a + r.qps, 0) / rounds.length).toFixed(1)
    }
    summary.push(aggregate)
    console.log(
      `并发 ${pad(level, 5)} | 成功率 ${pad((aggregate.successRate * 100).toFixed(1) + '%', 7)}` +
        ` | P50 ${pad(aggregate.p50 + 'ms', 10)} | P95 ${pad(aggregate.p95 + 'ms', 10)}` +
        ` | 平均 ${pad(aggregate.avg + 'ms', 10)} | 最大 ${pad(aggregate.max + 'ms', 10)}` +
        ` | 吞吐 ${aggregate.qps} QPS`
    )
  }

  const payload = {
    generatedAt: new Date().toISOString(),
    base: BASE,
    endpoint: `GET /api/${ENDPOINT}`,
    rounds: ROUNDS,
    results: summary
  }
  writeFileSync(OUT, JSON.stringify(payload, null, 2), 'utf8')
  console.log('==============================================================')
  console.log(`结果已写入：${OUT}`)
}

main()
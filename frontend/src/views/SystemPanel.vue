<!-- 系统演示面板：遗留课程目录中间层（direct/cached）、缓存刷新、可用性开关 -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>遗留课程目录系统（DEC VAX / Ingres 模拟库）</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <span class="badge">访问模式：{{ legacy.mode === 'direct' ? '直连遗留库（direct）' : '中间层缓存（cached）' }}</span>
        <span class="badge" :class="legacy.available ? 'ok' : 'err'">
          遗留系统：{{ legacy.available ? '可用' : '不可用' }}
        </span>
        <span class="badge" :class="legacy.cached ? 'ok' : 'warn'">
          缓存：{{ legacy.cached ? '已就绪（' + legacy.courseCount + ' 门课程）' : '未命中' }}
        </span>
        <span class="badge">模拟延迟：{{ legacy.simulatedLatencyMs }} ms</span>
        <span class="badge">缓存有效期：{{ Math.round((legacy.cacheTtlSeconds || 0) / 60) }} 分钟</span>
        <span class="badge">上次加载耗时：{{ legacy.loadMillis != null ? legacy.loadMillis + ' ms' : '—' }}</span>
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <Win95Button label="切换为中间层缓存（cached）" :disabled="busy || legacy.mode === 'cached'" @click="setMode('cached')" />
        <Win95Button label="切换为直连遗留库（direct）" :disabled="busy || legacy.mode === 'direct'" @click="setMode('direct')" />
        <Win95Button label="刷新缓存（回源）" :disabled="busy" @click="refreshCache" />
        <Win95Button
          :label="legacy.available ? '模拟目录不可用' : '恢复目录可用'"
          :disabled="busy"
          @click="toggleLegacy"
        />
      </div>
      <div class="hint">
        演示要点：direct 模式每次访问目录都直连遗留库（约 8 秒，满足「不超过 10 秒」的底线要求）；
        cached 模式读取中间层缓存（毫秒级），并在启动时预热、支持手动刷新。
        关闭「目录可用」后，学生选课用例将提示「课程目录系统暂时不可用」并终止。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>计费系统开关</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <span class="badge" :class="billing.available ? 'ok' : 'err'">
          计费系统：{{ billing.available ? '可用' : '不可用' }}
        </span>
        <span class="badge">失败重试周期：{{ billing.retryIntervalSeconds }} 秒</span>
        <Win95Button
          :label="billing.available ? '模拟计费系统宕机' : '恢复计费系统'"
          :disabled="busy"
          @click="toggleBilling"
        />
      </div>
      <div class="hint">计费系统不可用时，关闭注册产生的账单仍会登记成功，但发送失败并按周期自动重试，恢复后自动补发。</div>
    </fieldset>

    <fieldset class="win-group">
      <legend>学期信息</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <span class="badge info">{{ semester.name || '—' }}</span>
        <span class="badge" :class="semester.registrationStatus === 'OPEN' ? 'ok' : 'warn'">
          注册状态：{{ semester.registrationStatus === 'OPEN' ? '开放中' : '已关闭' }}
        </span>
        <span class="badge">选课调整期：{{ semester.adjustmentStart }} ~ {{ semester.adjustmentEnd }}</span>
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>性能对比操作提示</legend>
      <div class="hint">
        使用 <span class="mono">tools/legacy-compare.mjs</span> 可自动完成「直连 vs 缓存」响应时间对比；
        使用 <span class="mono">tools/perf-test.mjs</span> 可进行 50/100/200 并发压力测试并输出 P50/P95。
      </div>
    </fieldset>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { systemApi } from '../api'
import { useUiStore } from '../stores/ui'
import Win95Button from '../components/Win95Button.vue'

const ui = useUiStore()
const status = ref({})
const busy = ref(false)

const legacy = computed(() => status.value.legacy || {})
const billing = computed(() => status.value.billing || {})
const semester = computed(() => status.value.semester || {})

async function load() {
  status.value = await systemApi.status()
}

async function guarded(action, message) {
  busy.value = true
  try {
    await action()
    await load()
    if (message) await ui.alert(message, '演示开关')
  } catch (e) {
    await ui.alert(e.message, '操作失败')
  } finally {
    busy.value = false
  }
}

function setMode(mode) {
  guarded(
    () => systemApi.setLegacyMode(mode),
    mode === 'direct'
      ? '已切换为直连模式：下一次访问课程目录将直连遗留库（约 8 秒）。'
      : '已切换为中间层缓存模式：课程目录将从缓存读取（毫秒级）。'
  )
}

function refreshCache() {
  guarded(() => systemApi.refreshCache(), '缓存已刷新（已回源遗留库重新读取课程目录）。')
}

function toggleLegacy() {
  const next = !legacy.value.available
  guarded(
    () => systemApi.setLegacyAvailability(next),
    next ? '遗留课程目录已恢复可用。' : '已模拟课程目录不可用：学生选课用例将被终止。'
  )
}

function toggleBilling() {
  const next = !billing.value.available
  guarded(
    () => systemApi.setBillingAvailability(next),
    next ? '计费系统已恢复可用。' : '已模拟计费系统宕机：账单将进入重试队列。'
  )
}

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
})
</script>
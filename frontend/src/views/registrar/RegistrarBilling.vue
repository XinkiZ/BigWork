<!-- 注册管理员：计费交易监控（含自动/手动重试） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>计费交易状态</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <Win95Button label="刷新" @click="load" />
        <Win95Button label="手动触发一次重试" is-default :disabled="busy" @click="retry" />
        <Win95Button
          :label="available ? '模拟计费系统宕机' : '恢复计费系统'"
          :disabled="busy"
          @click="toggleAvailability"
        />
        <span class="badge" :class="available ? 'ok' : 'err'">
          计费系统：{{ available ? '可用' : '不可用' }}
        </span>
        <span class="badge">重试周期：{{ retryInterval }} 秒（定时任务自动重试）</span>
        <span class="badge warn">待重试：{{ pendingCount }} 笔</span>
      </div>
      <div class="hint">
        关闭注册后生成的学期账单与调整期差额账单均记录在此。发送失败的交易会按固定周期自动重试，直至发送成功。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>交易明细</legend>
      <Win95Table :columns="columns" :rows="rows" row-key="id" max-height="420px" empty-text="暂无计费交易（请先执行关闭注册）">
        <template #txType="{ row }">
          <span class="badge" :class="row.txType === 'INITIAL' ? 'info' : ''">
            {{ row.txType === 'INITIAL' ? '学期账单' : '调整期差额' }}
          </span>
        </template>
        <template #status="{ row }">
          <span class="badge" :class="row.status === 'SENT' ? 'ok' : row.status === 'FAILED' ? 'err' : 'warn'">
            {{ statusText(row.status) }}
          </span>
        </template>
      </Win95Table>
    </fieldset>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { registrarApi, systemApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const rows = ref([])
const available = ref(true)
const retryInterval = ref(30)
const busy = ref(false)

const columns = [
  { key: 'id', label: '交易号', width: '68px', align: 'center' },
  { key: 'studentNo', label: '学号', width: '96px' },
  { key: 'studentName', label: '姓名', width: '82px' },
  { key: 'semesterName', label: '学期', width: '190px' },
  { key: 'txType', label: '类型', width: '104px' },
  { key: 'amount', label: '金额(元)', width: '90px', align: 'right' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'retryCount', label: '重试次数', width: '78px', align: 'center' },
  { key: 'lastAttemptAt', label: '最后尝试时间', width: '160px' },
  { key: 'description', label: '说明' }
]

const pendingCount = computed(() => rows.value.filter((r) => r.status !== 'SENT').length)

function statusText(status) {
  return { PENDING: '待发送', SENT: '已发送', FAILED: '失败待重试' }[status] || status
}

async function load() {
  try {
    rows.value = await registrarApi.billingTransactions()
    const status = await systemApi.status()
    available.value = !!status.billing.available
    retryInterval.value = status.billing.retryIntervalSeconds
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

async function retry() {
  busy.value = true
  try {
    const response = await registrarApi.retryBilling()
    rows.value = response.billing
    await ui.alert(`本轮重试发送成功 ${response.sentNow} 笔交易。`, '重试完成')
  } catch (e) {
    await ui.alert(e.message, '重试失败')
  } finally {
    busy.value = false
  }
}

async function toggleAvailability() {
  busy.value = true
  try {
    const next = !available.value
    await systemApi.setBillingAvailability(next)
    await load()
    await ui.alert(
      next ? '计费系统已恢复可用，定时重试将自动发送待处理交易。' : '计费系统已模拟宕机，新交易将进入重试队列。',
      '计费系统开关'
    )
  } catch (e) {
    await ui.alert(e.message, '操作失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
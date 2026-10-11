<!-- 学生：我的账单（仅本人计费交易，可按学期筛选） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>我的账单</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <SemesterSelect v-model="semesterId" :include-all="true" all-label="全部学期" label="学期" @change="load" />
        <Win95Button label="刷新" @click="load" />
        <span class="badge">交易笔数：{{ rows.length }}</span>
        <span class="badge info">应收合计：{{ totalText }}</span>
      </div>
      <div class="hint">
        学期账单在注册管理员执行「关闭注册」后生成；选课调整期的加/退选会产生差额账单（加课补收、退课退还）。
        发送失败的交易由系统按周期自动重试，无需人工处理。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>交易明细</legend>
      <Win95Table :columns="columns" :rows="rows" row-key="id" max-height="400px" empty-text="暂无计费交易">
        <template #txType="{ row }">
          <span class="badge" :class="row.txType === 'INITIAL' ? 'info' : ''">
            {{ row.txType === 'INITIAL' ? '学期账单' : '调整期差额' }}
          </span>
        </template>
        <template #amount="{ row }">
          <span :class="row.amount < 0 ? 'money-refund' : ''">
            {{ row.amount > 0 ? '+' : '' }}{{ Number(row.amount).toFixed(2) }}
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
import { studentApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import SemesterSelect from '../../components/SemesterSelect.vue'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const semesterId = ref('')
const rows = ref([])

const columns = [
  { key: 'id', label: '交易号', width: '68px', align: 'center' },
  { key: 'semesterName', label: '学期', width: '190px' },
  { key: 'txType', label: '类型', width: '110px' },
  { key: 'amount', label: '金额(元)', width: '100px', align: 'right' },
  { key: 'status', label: '状态', width: '100px' },
  { key: 'retryCount', label: '重试次数', width: '78px', align: 'center' },
  { key: 'lastAttemptAt', label: '最后尝试时间', width: '160px' },
  { key: 'description', label: '说明' }
]

const totalText = computed(() => {
  const sum = rows.value.reduce((acc, row) => acc + Number(row.amount || 0), 0)
  return `${sum > 0 ? '+' : ''}${sum.toFixed(2)} 元`
})

function statusText(status) {
  return { PENDING: '待发送', SENT: '已发送', FAILED: '失败待重试' }[status] || status
}

async function load() {
  try {
    rows.value = await studentApi.billingTransactions(semesterId.value || undefined)
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

onMounted(load)
</script>
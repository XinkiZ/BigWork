<!-- 注册管理员：关闭注册（Close Registration） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>关闭注册操作</legend>
      <div class="hint">
        执行关闭注册后，系统将在同一事务中完成：校验注册状态 → 取消未分配教授或人数不足 3 人的课程班 →
        按备选顺序补位课表 → 二次校验并取消仍不足 3 人的课程班 → 计算学费并登记计费交易（计费系统不可用时按周期自动重试）。
      </div>
      <div class="toolbar" style="margin-bottom: 0">
        <Win95Button label="执行关闭注册" is-default :disabled="busy" @click="close" />
        <Win95Button label="刷新系统状态" @click="loadStatus" />
        <span class="badge" :class="status.semester && status.semester.registrationStatus === 'OPEN' ? 'warn' : 'ok'">
          当前学期注册状态：{{ registrationText }}
        </span>
        <span class="badge" :class="status.billing && status.billing.available ? 'ok' : 'err'">
          计费系统：{{ status.billing && status.billing.available ? '可用' : '不可用（将进入重试队列）' }}
        </span>
      </div>
    </fieldset>

    <fieldset v-if="result" class="win-group">
      <legend>执行报告</legend>
      <div class="toolbar" style="margin-bottom: 6px">
        <span class="badge info">{{ result.semesterName }}</span>
        <span class="badge">执行时间：{{ result.closedAt }}</span>
        <span class="badge ok">生效课程班：{{ result.confirmedOfferingCount }}</span>
        <span class="badge err">取消课程班：{{ result.cancelledOfferings.length }}</span>
        <span class="badge">补位成功：{{ result.promotions.length }}</span>
        <span class="badge warn">补位失败：{{ result.backupFailures.length }}</span>
        <span class="badge">账单：{{ result.billingTransactionCount }} 笔 / 合计 {{ result.totalAmount }} 元</span>
      </div>
      <div class="grid-2">
        <div>
          <div class="hint"><b>被取消的课程班</b></div>
          <Win95Table :columns="cancelColumns" :rows="result.cancelledOfferings" row-key="offeringId" max-height="180px" empty-text="无课程班被取消" />
        </div>
        <div>
          <div class="hint"><b>备选补位成功</b></div>
          <Win95Table :columns="promotionColumns" :rows="result.promotions" row-key="courseNo" max-height="180px" empty-text="无补位记录" />
        </div>
      </div>
      <div class="grid-2" style="margin-top: 8px">
        <div>
          <div class="hint"><b>补位失败明细</b></div>
          <Win95Table :columns="failureColumns" :rows="result.backupFailures" row-key="courseNo" max-height="180px" empty-text="无补位失败" />
        </div>
        <div>
          <div class="hint"><b>系统消息</b></div>
          <div class="win-table-wrap" style="max-height: 180px; padding: 8px">
            <div v-for="(msg, index) in result.messages" :key="index">· {{ msg }}</div>
          </div>
        </div>
      </div>
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
const status = ref({})
const result = ref(null)
const busy = ref(false)

const cancelColumns = [
  { key: 'offeringId', label: '课程班ID', width: '76px', align: 'center' },
  { key: 'courseNo', label: '课程号', width: '82px' },
  { key: 'title', label: '课程名称', width: '120px' },
  { key: 'reason', label: '取消原因' }
]

const promotionColumns = [
  { key: 'studentNo', label: '学号', width: '92px' },
  { key: 'studentName', label: '姓名', width: '76px' },
  { key: 'courseNo', label: '课程号', width: '82px' },
  { key: 'title', label: '课程名称', width: '120px' },
  { key: 'order', label: '顺位', width: '56px', align: 'center' }
]

const failureColumns = [
  { key: 'studentNo', label: '学号', width: '92px' },
  { key: 'studentName', label: '姓名', width: '76px' },
  { key: 'title', label: '课程名称', width: '110px' },
  { key: 'reason', label: '失败原因' }
]

const registrationText = computed(() => {
  const value = status.value.semester && status.value.semester.registrationStatus
  return value === 'CLOSED' ? '已关闭' : '开放中'
})

async function loadStatus() {
  try {
    status.value = await systemApi.status()
  } catch (e) {
    await ui.alert(e.message, '状态加载失败')
  }
}

async function close() {
  const ok = await ui.confirm(
    '确定要执行「关闭注册」吗？\n系统将取消人数不足的课程班、按备选顺序补位，并生成学期账单。',
    '关闭注册确认'
  )
  if (!ok) return
  busy.value = true
  try {
    const response = await registrarApi.closeRegistration()
    result.value = response.report
    await loadStatus()
    const report = response.report
    await ui.alert(
      `关闭注册执行完成。\n` +
        `取消课程班：${report.cancelledOfferings.length} 个\n` +
        `备选补位成功：${report.promotions.length} 条\n` +
        `补位失败：${report.backupFailures.length} 条\n` +
        `生成账单：${report.billingTransactionCount} 笔，合计 ${report.totalAmount} 元\n` +
        `本次立即发送成功：${response.billingSentNow} 笔`,
      '执行完成'
    )
  } catch (e) {
    await ui.alert(e.message, '关闭注册失败')
  } finally {
    busy.value = false
  }
}

onMounted(loadStatus)
</script>
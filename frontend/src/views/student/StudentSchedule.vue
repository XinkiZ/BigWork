<!-- 学生：我的课表（含周课表视图与选课明细） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>课表信息</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <SemesterSelect v-model="semesterId" label="学期" @change="load" />
        <span class="badge info">{{ schedule.semesterName || '—' }}</span>
        <span class="badge">课表状态：{{ scheduleText }}</span>
        <span class="badge" :class="schedule.registrationStatus === 'OPEN' ? 'ok' : 'warn'">
          注册{{ schedule.registrationStatus === 'OPEN' ? '开放中' : '已关闭' }}
        </span>
        <span class="badge">总学分：{{ totalCredits }}</span>
        <Win95Button label="刷新" size="small" @click="load" />
      </div>
      <div v-if="!isCurrentSemester" class="hint">
        ⏳ 正在查看历史学期课表（只读）：历史学期不支持修改，如需调整当前选课请切换到当前学期。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>周课表</legend>
      <div class="win-table-wrap">
        <table class="win-table">
          <thead>
            <tr>
              <th style="width: 110px">时间段</th>
              <th v-for="day in dayNames" :key="day">{{ day }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="slot in slots" :key="slot">
              <td class="mono">{{ slot }}</td>
              <td v-for="(day, index) in dayNames" :key="day" style="height: 46px">
                <div v-for="cell in cellsAt(index + 1, slot)" :key="cell.rowKey" class="hint" style="margin: 0">
                  <span class="badge" :class="cell.itemType === 'PRIMARY' ? 'info' : ''">
                    {{ cell.offering.courseNo }}
                  </span>
                  {{ cell.offering.title }}
                  <div class="muted">{{ cell.offering.room }} · {{ cell.offering.professorName || '待定' }}</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="hint">说明：只有「已注册」的课程会出现在周课表中；备选课程在关闭注册时按顺序自动补位。</div>
    </fieldset>

    <fieldset class="win-group">
      <legend>选课明细</legend>
      <Win95Table :columns="columns" :rows="items" row-key="rowKey" max-height="260px" :empty-text="isCurrentSemester ? '当前学期还没有课表' : '该学期没有已注册课程记录'">
        <template #itemType="{ row }">
          <span class="badge" :class="row.itemType === 'PRIMARY' ? 'info' : ''">
            {{ row.itemType === 'PRIMARY' ? '主选' : '备选' }}
          </span>
        </template>
        <template #status="{ row }">
          <span class="badge" :class="row.status === 'REGISTERED' ? 'ok' : 'warn'">
            {{ itemStatusText(row.status) }}{{ row.promoted ? '（备选补位）' : '' }}
          </span>
        </template>
      </Win95Table>
    </fieldset>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { studentApi } from '../../api'
import { useSemestersStore } from '../../stores/semesters'
import SemesterSelect from '../../components/SemesterSelect.vue'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const semestersStore = useSemestersStore()
const dayNames = ['周一', '周二', '周三', '周四', '周五']
const slots = ['08:00-09:40', '10:00-11:40']
const semesterId = ref('')
const schedule = ref({ items: [], status: 'NONE' })

const isCurrentSemester = computed(() => !semesterId.value || semesterId.value === semestersStore.currentId)

const columns = [
  { key: 'itemType', label: '类型', width: '66px' },
  { key: 'sortOrder', label: '顺序', width: '48px', align: 'center' },
  { key: 'courseNo', label: '课程号', width: '80px' },
  { key: 'title', label: '课程名称', width: '150px' },
  { key: 'credits', label: '学分', width: '48px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '90px' },
  { key: 'timeText', label: '上课时间', width: '160px' },
  { key: 'room', label: '教室', width: '80px' },
  { key: 'status', label: '状态', width: '130px' }
]

const items = computed(() =>
  (schedule.value.items || [])
    .filter((i) => i.status !== 'REMOVED')
    .map((i) => ({
      itemId: i.itemId,
      rowKey: i.itemId ?? `H-${i.offering.offeringId}`,
      itemType: i.itemType,
      sortOrder: i.sortOrder,
      status: i.status,
      promoted: i.promoted,
      courseNo: i.offering.courseNo,
      title: i.offering.title,
      credits: i.offering.credits,
      professorName: i.offering.professorName,
      timeText: i.offering.timeText,
      room: i.offering.room,
      dayOfWeek: i.offering.dayOfWeek,
      startTime: i.offering.startTime,
      offering: i.offering
    }))
)

const registered = computed(() => items.value.filter((i) => i.status === 'REGISTERED'))
const totalCredits = computed(() => registered.value.reduce((sum, i) => sum + i.credits, 0))
const scheduleText = computed(
  () =>
    ({ NONE: '未建立', DRAFT: '暂存（未提交）', SUBMITTED: '已提交', HISTORY: '历史记录（只读）' })[schedule.value.status] ||
    schedule.value.status
)

function cellsAt(dayOfWeek, slot) {
  const start = slot.split('-')[0]
  return registered.value.filter((i) => i.dayOfWeek === dayOfWeek && i.startTime === start)
}

function itemStatusText(status) {
  return { SELECTED: '已选择', REGISTERED: '已注册', REMOVED: '已移除' }[status] || status
}

async function load() {
  schedule.value = await studentApi.schedule(semesterId.value || undefined)
}

onMounted(async () => {
  await semestersStore.load()
  semesterId.value = semestersStore.currentId || ''
  await load()
})
</script>
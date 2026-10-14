<!-- 学生工作台：课程注册（Register for Courses） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>当前学期与选课状态</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <span class="badge info">{{ schedule.semesterName || '—' }}</span>
        <span class="badge" :class="registrationOpen ? 'ok' : 'warn'">
          注册状态：{{ registrationOpen ? '开放中' : '已关闭' }}
        </span>
        <span class="badge" :class="adjustmentOpen ? 'ok' : ''">
          选课调整期：{{ adjustmentOpen ? '进行中（' + (schedule.adjustmentWindow || '') + '）' : '未开放' }}
        </span>
        <span class="badge">主选 {{ primaryCount }}/4</span>
        <span class="badge">备选 {{ backupCount }}/2</span>
        <span class="badge">预选学分 {{ selectedCredits }}</span>
      </div>
      <div class="hint">
        规则：可选 4 门主选课程 + 2 门备选课程（主选无法选入时按顺序启用备选）；单门课程班上限 10 人、不足 3 人将取消；
        选课过程中若课程班已满，系统将实时提示。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>课程目录（来自遗留课程目录系统）</legend>
      <div class="toolbar">
        <input v-model.trim="keyword" class="win-input" placeholder="按课程号/课程名检索" style="width: 190px" />
        <select v-model="filterDay" class="win-select">
          <option value="">全部上课日</option>
          <option v-for="(day, index) in dayNames" :key="index" :value="index + 1">{{ day }}</option>
        </select>
        <select v-model="filterDept" class="win-select">
          <option value="">全部开课单位</option>
          <option v-for="dept in departments" :key="dept" :value="dept">{{ dept }}</option>
        </select>
        <label><input v-model="onlyAvailable" type="checkbox" /> 仅显示未满课程</label>
        <Win95Button label="刷新目录" size="small" @click="loadCatalog" />
      </div>

      <Win95Table
        :columns="catalogColumns"
        :rows="filteredCatalog"
        row-key="offeringId"
        :selected-key="selectedOfferingId"
        max-height="290px"
        empty-text="课程目录为空或目录系统不可用"
        @row-click="(row) => (selectedOfferingId = row.offeringId)"
        @row-dblclick="addAsPrimary"
      >
        <template #full="{ row }">
          <span class="badge" :class="row.full ? 'err' : 'ok'">
            {{ row.enrolledCount }}/{{ row.capacity }}{{ row.full ? ' 已满' : '' }}
          </span>
        </template>
        <template #prerequisites="{ row }">
          <template v-if="row.prerequisites && row.prerequisites.length">
            <span
              v-for="item in row.prerequisites"
              :key="item.courseNo"
              class="badge"
              :class="item.passed === true ? 'ok' : item.passed === false ? 'err' : ''"
              :title="item.title"
            >
              {{ item.passed === true ? '✓' : item.passed === false ? '✗' : '•' }} {{ item.courseNo }}
            </span>
          </template>
          <span v-else class="muted">无</span>
        </template>
        <template #status="{ row }">
          <span class="badge" :class="row.status === 'CONFIRMED' ? 'ok' : 'warn'">
            {{ statusText(row.status) }}
          </span>
        </template>
      </Win95Table>

      <div class="toolbar" style="margin-top: 8px; margin-bottom: 0">
        <Win95Button label="加为主选" :disabled="!selectedOfferingId" @click="addAsPrimary" />
        <Win95Button label="加为备选" :disabled="!selectedOfferingId" @click="addAsBackup" />
        <span class="muted">提示：双击目录中的课程可直接加为主选</span>
      </div>
    </fieldset>
    <div class="grid-2">
      <fieldset class="win-group">
        <legend>主选课程（最多 4 门）</legend>
        <Win95Table :columns="selectionColumns" :rows="primaryLines" row-key="key" max-height="190px" empty-text="尚未选择主选课程">
          <template #actions="{ row }">
            <Win95Button label="移除" size="small" @click="removeLine(row)" />
          </template>
        </Win95Table>
        <div class="toolbar" style="margin: 6px 0 0 0">
          <Win95Button label="上移" size="small" @click="moveLine(primaryLines, -1)" />
          <Win95Button label="下移" size="small" @click="moveLine(primaryLines, 1)" />
        </div>
      </fieldset>

      <fieldset class="win-group">
        <legend>备选课程（最多 2 门，按顺序启用）</legend>
        <Win95Table :columns="selectionColumns" :rows="backupLines" row-key="key" max-height="190px" empty-text="尚未选择备选课程">
          <template #actions="{ row }">
            <Win95Button label="移除" size="small" @click="removeLine(row)" />
          </template>
        </Win95Table>
        <div class="toolbar" style="margin: 6px 0 0 0">
          <Win95Button label="上移" size="small" @click="moveLine(backupLines, -1)" />
          <Win95Button label="下移" size="small" @click="moveLine(backupLines, 1)" />
        </div>
      </fieldset>
    </div>

    <div class="toolbar">
      <Win95Button label="暂存课表" :disabled="!registrationOpen || busy" @click="saveDraft" />
      <Win95Button label="提交注册" is-default :disabled="!registrationOpen || busy" @click="submit" />
      <Win95Button label="删除课表" :disabled="!registrationOpen || busy || schedule.status === 'NONE'" @click="removeSchedule" />
      <Win95Button label="重新载入" @click="loadAll" />
    </div>

    <fieldset class="win-group">
      <legend>已提交课表（选课结果）</legend>
      <Win95Table :columns="resultColumns" :rows="resultRows" row-key="itemId" max-height="230px" empty-text="当前学期还没有课表">
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
        <template #actions="{ row }">
          <Win95Button
            v-if="adjustmentMode && row.status === 'REGISTERED'"
            label="退选"
            size="small"
            @click="adjustmentDrop(row.offering)"
          />
          <span v-else class="muted">—</span>
        </template>
      </Win95Table>
      <div v-if="adjustmentMode" class="hint">
        选课调整期：可对已生效课程班进行加退选，系统将自动生成差额账单（加课补收、退课退还）。
      </div>
      <div v-else class="hint">注册关闭后进入选课调整期，届时可在此处加退选。</div>
    </fieldset>

    <fieldset v-if="adjustmentMode" class="win-group">
      <legend>调整期可选课程（点击「加选」提交）</legend>
      <Win95Table :columns="adjustColumns" :rows="adjustableOfferings" row-key="offeringId" max-height="190px" empty-text="没有可加选的课程">
        <template #actions="{ row }">
          <Win95Button label="加选" size="small" @click="adjustmentAdd(row)" />
        </template>
      </Win95Table>
    </fieldset>

    <div v-if="message" class="hint">ℹ️ {{ message }}</div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { studentApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const dayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const catalog = ref([])
const schedule = ref({ status: 'NONE', items: [], registrationStatus: 'OPEN' })
const primaryLines = ref([])
const backupLines = ref([])
const selectedOfferingId = ref(null)
const keyword = ref('')
const filterDay = ref('')
const filterDept = ref('')
const onlyAvailable = ref(false)
const busy = ref(false)
const message = ref('')

const registrationOpen = computed(() => (schedule.value.registrationStatus || 'OPEN') === 'OPEN')
const adjustmentOpen = computed(() => !!schedule.value.adjustmentOpen)
const adjustmentMode = computed(() => !registrationOpen.value && adjustmentOpen.value)

const catalogColumns = [
  { key: 'courseNo', label: '课程号', width: '78px' },
  { key: 'title', label: '课程名称', width: '130px' },
  { key: 'credits', label: '学分', width: '46px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '84px' },
  { key: 'timeText', label: '上课时间', width: '150px' },
  { key: 'room', label: '教室', width: '66px' },
  { key: 'full', label: '选课人数', width: '100px' },
  { key: 'prerequisites', label: '先修课程（达成情况）', width: '160px' },
  { key: 'status', label: '状态', width: '76px' }
]

const selectionColumns = [
  { key: 'title', label: '课程名称' },
  { key: 'courseNo', label: '课程号', width: '78px' },
  { key: 'credits', label: '学分', width: '46px', align: 'center' },
  { key: 'timeText', label: '上课时间', width: '150px' },
  { key: 'actions', label: '操作', width: '70px' }
]

const resultColumns = [
  { key: 'itemType', label: '类型', width: '66px' },
  { key: 'sortOrder', label: '顺序', width: '46px', align: 'center' },
  { key: 'courseNo', label: '课程号', width: '78px' },
  { key: 'title', label: '课程名称', width: '130px' },
  { key: 'credits', label: '学分', width: '46px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '84px' },
  { key: 'timeText', label: '上课时间', width: '150px' },
  { key: 'status', label: '状态', width: '116px' },
  { key: 'actions', label: '调整期操作', width: '96px' }
]

const adjustColumns = [
  { key: 'courseNo', label: '课程号', width: '78px' },
  { key: 'title', label: '课程名称', width: '140px' },
  { key: 'credits', label: '学分', width: '46px', align: 'center' },
  { key: 'timeText', label: '上课时间', width: '150px' },
  { key: 'enrolledCount', label: '人数', width: '70px' },
  { key: 'actions', label: '操作', width: '70px' }
]

const departments = computed(() => [...new Set(catalog.value.map((c) => c.department).filter(Boolean))])

const filteredCatalog = computed(() =>
  catalog.value.filter((c) => {
    if (keyword.value) {
      const kw = keyword.value.toLowerCase()
      if (!c.courseNo.toLowerCase().includes(kw) && !c.title.toLowerCase().includes(kw)) return false
    }
    if (filterDay.value && c.dayOfWeek !== Number(filterDay.value)) return false
    if (filterDept.value && c.department !== filterDept.value) return false
    if (onlyAvailable.value && c.full) return false
    return true
  })
)

const primaryCount = computed(() => primaryLines.value.length)
const backupCount = computed(() => backupLines.value.length)
const selectedCredits = computed(() =>
  [...primaryLines.value, ...backupLines.value].reduce((sum, line) => sum + (line.credits || 0), 0)
)
const resultRows = computed(() =>
  (schedule.value.items || [])
    .filter((i) => i.status !== 'REMOVED')
    .map((i) => ({
      itemId: i.itemId,
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
      offering: i.offering
    }))
)
const adjustableOfferings = computed(() => {
  const chosen = new Set(resultRows.value.map((i) => i.offering.offeringId))
  return catalog.value.filter(
    (o) => !o.full && o.status === 'CONFIRMED' && !chosen.has(o.offeringId)
  )
})
function statusText(status) {
  return { PROPOSED: '待确认', CONFIRMED: '已生效', CANCELLED: '已取消' }[status] || status
}

function itemStatusText(status) {
  return { SELECTED: '已选择', REGISTERED: '已注册', REMOVED: '已移除' }[status] || status
}

function toLine(offering, itemType) {
  return {
    key: `${itemType}-${offering.offeringId}`,
    offeringId: offering.offeringId,
    itemType,
    courseNo: offering.courseNo,
    title: offering.title,
    credits: offering.credits,
    timeText: offering.timeText
  }
}

function findOffering(offeringId) {
  return catalog.value.find((c) => c.offeringId === offeringId)
}

function isChosen(offeringId) {
  return [...primaryLines.value, ...backupLines.value].some((l) => l.offeringId === offeringId)
}

function addAsPrimary() {
  const offering = selectedOfferingId.value ? findOffering(selectedOfferingId.value) : null
  if (!offering) return
  if (primaryLines.value.length >= 4) {
    ui.alert('主选课程最多 4 门，请先移除其他课程或改为备选。', '达到上限')
    return
  }
  if (isChosen(offering.offeringId)) {
    ui.alert('该课程班已在课表中。', '重复选择')
    return
  }
  primaryLines.value.push(toLine(offering, 'PRIMARY'))
}

function addAsBackup() {
  const offering = selectedOfferingId.value ? findOffering(selectedOfferingId.value) : null
  if (!offering) return
  if (backupLines.value.length >= 2) {
    ui.alert('备选课程最多 2 门。', '达到上限')
    return
  }
  if (isChosen(offering.offeringId)) {
    ui.alert('该课程班已在课表中。', '重复选择')
    return
  }
  backupLines.value.push(toLine(offering, 'BACKUP'))
}

function removeLine(row) {
  primaryLines.value = primaryLines.value.filter((l) => l.key !== row.key)
  backupLines.value = backupLines.value.filter((l) => l.key !== row.key)
}

/** 上移/下移：主选与备选均为循环调整，用于设定备选启用顺序 */
function moveLine(lines, direction) {
  if (lines.length < 2) return
  if (direction === -1) {
    lines.unshift(lines.pop())
  } else {
    lines.push(lines.shift())
  }
}

function payload() {
  return [
    ...primaryLines.value.map((l) => ({ offeringId: l.offeringId, itemType: 'PRIMARY' })),
    ...backupLines.value.map((l) => ({ offeringId: l.offeringId, itemType: 'BACKUP' }))
  ]
}

async function loadCatalog() {
  try {
    catalog.value = await studentApi.catalog()
    message.value = `课程目录共 ${catalog.value.length} 个课程班（来源：遗留课程目录系统）`
  } catch (e) {
    message.value = e.message
    await ui.alert(e.message, e.code === 'CATALOG_UNAVAILABLE' ? '课程目录不可用' : '加载失败')
  }
}

async function loadSchedule() {
  try {
    schedule.value = await studentApi.schedule()
    const items = (schedule.value.items || []).filter((i) => i.status !== 'REMOVED')
    primaryLines.value = items
      .filter((i) => i.itemType === 'PRIMARY')
      .map((i) => toLine(i.offering, 'PRIMARY'))
    backupLines.value = items
      .filter((i) => i.itemType === 'BACKUP')
      .map((i) => toLine(i.offering, 'BACKUP'))
  } catch (e) {
    message.value = e.message
  }
}

async function loadAll() {
  await Promise.all([loadCatalog(), loadSchedule()])
}

async function guard(action, label) {
  if (busy.value) return
  busy.value = true
  try {
    await action()
  } catch (e) {
    await ui.alert(`【${label}】失败：${e.message}`, '操作未完成')
  } finally {
    busy.value = false
  }
}

async function saveDraft() {
  await guard(async () => {
    schedule.value = await studentApi.saveDraft(payload())
    await loadSchedule()
    await ui.alert('课表已暂存（课程标记为「已选择」，不占用课程班名额）。', '暂存成功')
  }, '暂存')
}

async function submit() {
  await guard(async () => {
    schedule.value = await studentApi.submit(payload())
    await Promise.all([loadSchedule(), loadCatalog()])
    await ui.alert('课表提交成功，主选课程已标记为「已注册」。', '提交成功')
  }, '提交')
}

async function removeSchedule() {
  const ok = await ui.confirm('确定要删除当前课表吗？删除后你将同时从相关课程班名单中移除。', '删除课表')
  if (!ok) return
  await guard(async () => {
    await studentApi.remove()
    primaryLines.value = []
    backupLines.value = []
    await Promise.all([loadSchedule(), loadCatalog()])
    await ui.alert('课表已删除，相关选课记录已移除。', '删除成功')
  }, '删除')
}

async function adjustmentAdd(offering) {
  await guard(async () => {
    schedule.value = await studentApi.adjustmentAdd(offering.offeringId)
    await Promise.all([loadSchedule(), loadCatalog()])
    await ui.alert(schedule.value.notice || `已加选【${offering.title}】，系统已生成差额账单。`, '加选成功')
  }, '加选')
}

async function adjustmentDrop(offering) {
  const ok = await ui.confirm(`确定要退选【${offering.title}】吗？系统将生成退费差额账单。`, '退选确认')
  if (!ok) return
  await guard(async () => {
    schedule.value = await studentApi.adjustmentDrop(offering.offeringId)
    await Promise.all([loadSchedule(), loadCatalog()])
    await ui.alert(schedule.value.notice || `已退选【${offering.title}】。`, '退选成功')
  }, '退选')
}

onMounted(loadAll)
</script>
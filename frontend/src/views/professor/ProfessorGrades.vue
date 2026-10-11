<!-- 教授：提交成绩（Submit Grades）—— 仅可录入上一学期本人所授课程班成绩 -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>选择上学期课程班</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <select v-model="selectedId" class="win-select" style="min-width: 320px" @change="loadSheet">
          <option :value="null">— 请选择上一学期课程班 —</option>
          <option v-for="o in previous" :key="o.offeringId" :value="o.offeringId">
            {{ o.courseNo }} {{ o.title }}（{{ o.timeText }}）
          </option>
        </select>
        <Win95Button label="重新载入" size="small" :disabled="!selectedId" @click="loadSheet" />
        <span class="badge">学生数：{{ rows.length }}</span>
      </div>
      <div class="hint">
        成绩等级：A / B / C / D / F / I，可留空表示尚未录入；支持后续补录与修改。仅授课教授本人可录入。
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>成绩录入</legend>
      <Win95Table :columns="columns" :rows="rows" row-key="studentId" max-height="360px"
        empty-text="请选择上学期课程班，或该课程班暂无学生">
        <template #input="{ row }">
          <select v-model="row.editValue" class="win-select" style="min-width: 96px">
            <option value="">（留空）</option>
            <option v-for="g in gradeOptions" :key="g" :value="g">{{ g }}</option>
          </select>
        </template>
        <template #saved="{ row }">
          <span v-if="row.gradeValue" class="badge ok">已录入 {{ row.gradeValue }}</span>
          <span v-else class="badge warn">未录入</span>
        </template>
      </Win95Table>
      <div class="toolbar" style="margin: 8px 0 0 0">
        <Win95Button label="保存成绩" is-default :disabled="!selectedId || busy" @click="save" />
        <Win95Button label="全部留空" :disabled="!selectedId" @click="clearAll" />
        <span class="muted">保存时仅提交当前列表中的学生成绩。</span>
      </div>
    </fieldset>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { professorApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const previous = ref([])
const selectedId = ref(null)
const rows = ref([])
const busy = ref(false)
const gradeOptions = ['A', 'B', 'C', 'D', 'F', 'I']

const columns = [
  { key: 'studentNo', label: '学号', width: '120px' },
  { key: 'name', label: '姓名', width: '110px' },
  { key: 'saved', label: '当前成绩', width: '120px' },
  { key: 'input', label: '录入 / 修改', width: '150px' }
]

async function loadPrevious() {
  previous.value = await professorApi.previousOfferings()
  if (!previous.value.length) {
    await ui.alert('你没有上学期授课记录，无法录入成绩。', '无授课记录')
    return
  }
  if (previous.value.length === 1) {
    selectedId.value = previous.value[0].offeringId
    await loadSheet()
  }
}

async function loadSheet() {
  if (!selectedId.value) {
    rows.value = []
    return
  }
  try {
    const sheet = await professorApi.gradeSheet(selectedId.value)
    rows.value = sheet.map((r) => ({ ...r, editValue: r.gradeValue || '' }))
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

function clearAll() {
  rows.value = rows.value.map((r) => ({ ...r, editValue: '' }))
}

async function save() {
  if (busy.value) return
  busy.value = true
  try {
    const payload = rows.value.map((r) => ({
      studentId: r.studentId,
      gradeValue: r.editValue ? r.editValue : null
    }))
    const saved = await professorApi.saveGrades(selectedId.value, payload)
    rows.value = saved.map((r) => ({ ...r, editValue: r.gradeValue || '' }))
    await ui.alert('成绩已保存。', '保存成功')
  } catch (e) {
    await ui.alert(e.message, '保存失败')
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    await loadPrevious()
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
})
</script>
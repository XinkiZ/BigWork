<!-- 学生：查看成绩单（View Report Card） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>成绩单查询</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <label>学期</label>
        <select v-model="semesterId" class="win-select">
          <option value="">上一学期（默认）</option>
          <option value="2">当前学期（2026-2027 学年第一学期）</option>
        </select>
        <Win95Button label="查询成绩单" is-default @click="load" />
        <span class="muted">成绩数据仅本人可见，系统已做访问控制。</span>
      </div>
    </fieldset>

    <fieldset class="win-group">
      <legend>成绩单</legend>
      <div v-if="report" class="hint">
        <span class="badge info">{{ report.semesterName }}</span>
        <span class="badge">{{ report.studentNo }} {{ report.studentName }}</span>
        <span class="badge">已修学分：{{ totalCredits }}</span>
        <span class="badge">平均绩点参考：{{ averageText }}</span>
      </div>
      <Win95Table
        v-if="report && report.hasData"
        :columns="columns"
        :rows="report.rows"
        row-key="courseNo"
        max-height="320px"
      >
        <template #grade="{ row }">
          <span class="badge" :class="gradeClass(row.grade)">{{ row.grade }}</span>
        </template>
      </Win95Table>
      <div v-else class="win-table-wrap">
        <div class="win-table-empty">
          {{ report ? '该学期暂无成绩数据（可能尚未录入或未选课）' : '请点击「查询成绩单」' }}
        </div>
      </div>
    </fieldset>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { studentApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const semesterId = ref('')
const report = ref(null)

const columns = [
  { key: 'courseNo', label: '课程号', width: '90px' },
  { key: 'title', label: '课程名称', width: '190px' },
  { key: 'credits', label: '学分', width: '60px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '110px' },
  { key: 'grade', label: '成绩', width: '90px', align: 'center' }
]

const gradePoints = { A: 4, B: 3, C: 2, D: 1, F: 0 }

const totalCredits = computed(() =>
  report.value ? report.value.rows.reduce((sum, r) => sum + (r.credits || 0), 0) : 0
)

const averageText = computed(() => {
  if (!report.value || !report.value.rows.length) return '—'
  const rows = report.value.rows.filter((r) => gradePoints[r.grade] !== undefined)
  if (!rows.length) return '—'
  const creditSum = rows.reduce((sum, r) => sum + r.credits, 0)
  if (!creditSum) return '—'
  const weighted = rows.reduce((sum, r) => sum + gradePoints[r.grade] * r.credits, 0)
  return (weighted / creditSum).toFixed(2)
})

function gradeClass(grade) {
  if (grade === 'F') return 'err'
  if (grade === 'I' || grade === '未录入') return 'warn'
  return 'ok'
}

async function load() {
  try {
    report.value = await studentApi.reportCard(semesterId.value || undefined)
    if (!report.value.hasData) {
      await ui.alert('该学期没有对应的成绩数据。', '无成绩数据')
    }
  } catch (e) {
    await ui.alert(e.message, '查询失败')
  }
}

onMounted(load)
</script>
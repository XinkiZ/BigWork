<!-- 学生：学业记录（全部学期成绩按学期分组汇总） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>学业记录</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <Win95Button label="刷新" @click="load" />
        <span v-if="transcript" class="badge info">{{ transcript.studentNo }} {{ transcript.studentName }}</span>
        <span v-if="transcript" class="badge">累计课程：{{ transcript.totalCourses }} 门</span>
        <span v-if="transcript" class="badge">累计学分：{{ transcript.totalCredits }}</span>
        <span v-if="transcript" class="badge" :class="transcript.overallGpa != null ? 'ok' : 'warn'">
          总加权 GPA：{{ transcript.overallGpa != null ? transcript.overallGpa.toFixed(2) : '暂无（无已录入成绩）' }}
        </span>
      </div>
      <div class="hint">
        学业记录按学期分组展示全部已产生成绩的课程；等级制换算：A=4.0、B=3.0、C=2.0、D=1.0、F=0；
        「I（未完成）」与「未录入」不计入 GPA，但计入修读学分。
      </div>
    </fieldset>

    <fieldset v-for="section in sections" :key="section.semesterId" class="win-group">
      <legend>📚 {{ section.name }}（{{ section.code }}）</legend>
      <div class="toolbar" style="margin-bottom: 6px">
        <span class="badge">学期学分：{{ section.creditsTotal }}</span>
        <span class="badge" :class="section.gpa != null ? 'ok' : 'warn'">
          学期 GPA：{{ section.gpa != null ? section.gpa.toFixed(2) : '暂无' }}
        </span>
      </div>
      <Win95Table :columns="columns" :rows="section.rows" row-key="courseNo" max-height="220px" empty-text="该学期暂无成绩记录">
        <template #grade="{ row }">
          <span class="badge" :class="gradeClass(row.grade)">{{ row.grade }}</span>
        </template>
      </Win95Table>
    </fieldset>

    <fieldset v-if="!loading && !sections.length" class="win-group">
      <legend>学业记录</legend>
      <div class="win-table-wrap">
        <div class="win-table-empty">暂无学业记录（尚无任何学期的成绩数据）。</div>
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
const transcript = ref(null)
const loading = ref(true)

const columns = [
  { key: 'courseNo', label: '课程号', width: '90px' },
  { key: 'title', label: '课程名称', width: '190px' },
  { key: 'credits', label: '学分', width: '60px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '110px' },
  { key: 'grade', label: '成绩', width: '90px', align: 'center' }
]

const sections = computed(() => (transcript.value ? transcript.value.semesters : []))

function gradeClass(grade) {
  if (grade === 'F') return 'err'
  if (grade === 'I' || grade === '未录入') return 'warn'
  return 'ok'
}

async function load() {
  loading.value = true
  try {
    transcript.value = await studentApi.transcript()
  } catch (e) {
    await ui.alert(e.message, '查询失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
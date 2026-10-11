<!-- 教授：查看选课名单（Roster，支持按学期查看） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>选择学期与课程班</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <SemesterSelect v-model="semesterId" label="学期" @change="loadOfferings" />
        <select v-model="selectedId" class="win-select" style="min-width: 320px" @change="loadRoster">
          <option :value="null">— 请选择授课课程班 —</option>
          <option v-for="o in offerings" :key="o.offeringId" :value="o.offeringId">
            {{ o.courseNo }} {{ o.title }}（{{ o.timeText }}）
          </option>
        </select>
        <Win95Button label="刷新名单" size="small" :disabled="!selectedId" @click="loadRoster" />
        <span class="badge">人数：{{ roster.length }}/{{ capacityText }}</span>
      </div>
      <div v-if="!isCurrentSemester" class="hint">⏳ 正在查看历史学期选课名单（只读）。</div>
    </fieldset>

    <fieldset class="win-group">
      <legend>选课名单</legend>
      <Win95Table :columns="columns" :rows="roster" row-key="studentId" max-height="360px"
        empty-text="请选择课程班，或该课程班暂无学生">
        <template #grade="{ row }">
          <span v-if="row.grade" class="badge ok">{{ row.grade }}</span>
          <span v-else class="muted">未录入</span>
        </template>
      </Win95Table>
    </fieldset>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { professorApi } from '../../api'
import { useSemestersStore } from '../../stores/semesters'
import { useUiStore } from '../../stores/ui'
import SemesterSelect from '../../components/SemesterSelect.vue'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const semestersStore = useSemestersStore()
const semesterId = ref('')
const offerings = ref([])
const selectedId = ref(null)
const roster = ref([])

const columns = [
  { key: 'studentNo', label: '学号', width: '120px' },
  { key: 'name', label: '姓名', width: '120px' },
  { key: 'grade', label: '该课程班成绩', width: '120px' }
]

const capacityText = computed(() => {
  const offering = offerings.value.find((o) => o.offeringId === selectedId.value)
  return offering ? `${offering.capacity}（上限 10 人）` : '10'
})
const isCurrentSemester = computed(() => !semesterId.value || semesterId.value === semestersStore.currentId)

async function loadOfferings() {
  selectedId.value = null
  roster.value = []
  try {
    offerings.value = await professorApi.myOfferings(semesterId.value || undefined)
    if (offerings.value.length === 1) {
      selectedId.value = offerings.value[0].offeringId
      await loadRoster()
    }
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

async function loadRoster() {
  if (!selectedId.value) {
    roster.value = []
    return
  }
  try {
    roster.value = await professorApi.roster(selectedId.value)
  } catch (e) {
    await ui.alert(e.message, '加载名单失败')
  }
}

onMounted(async () => {
  await semestersStore.load()
  semesterId.value = semestersStore.currentId || ''
  await loadOfferings()
})
</script>
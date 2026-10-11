<!-- 注册管理员：课程班管理（开课、编辑、删除；课程主数据来自遗留课程目录） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>课程班管理</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <SemesterSelect v-model="semesterId" label="学期" @change="loadOfferings" />
        <Win95Button label="刷新" @click="loadOfferings" />
        <Win95Button label="新建课程班" is-default :disabled="!semesterId" @click="startCreate" />
        <span class="badge">课程班：{{ offerings.length }} 个</span>
        <span class="badge info">课程主数据只读引用：遗留课程目录系统</span>
      </div>
      <div class="hint">
        新开课程班固定为「已提出（PROPOSED）」状态，关闭注册时统一确认或取消；容量上限 10 人、最少开课人数默认 3 人。
        已取消的课程班不可编辑；存在选课、课表或成绩引用的课程班不可删除。
      </div>
    </fieldset>

    <div class="grid-2">
      <fieldset class="win-group">
        <legend>课程班列表</legend>
        <Win95Table
          :columns="columns"
          :rows="offerings"
          row-key="offeringId"
          :selected-key="form.id"
          max-height="380px"
          empty-text="该学期暂无课程班"
          @row-click="pick"
        >
          <template #timeText="{ row }">
            <span>{{ row.dayText }} {{ row.startTime }}-{{ row.endTime }}</span>
          </template>
          <template #enrolled="{ row }">
            <span class="badge" :class="row.full ? 'err' : 'ok'">{{ row.enrolledCount }}/{{ row.capacity }}</span>
          </template>
          <template #status="{ row }">
            <span class="badge" :class="row.status === 'CONFIRMED' ? 'ok' : row.status === 'CANCELLED' ? 'err' : 'warn'">
              {{ statusText(row.status) }}
            </span>
          </template>
          <template #actions="{ row }">
            <Win95Button label="编辑" size="small" :disabled="row.status === 'CANCELLED'" @click.stop="pick(row)" />
            <Win95Button label="删除" size="small" @click.stop="remove(row)" />
          </template>
        </Win95Table>
      </fieldset>

      <fieldset class="win-group">
        <legend>{{ form.id ? '编辑课程班（ID：' + form.id + '）' : '新建课程班' }}</legend>
        <div class="win-field">
          <label>课程 *</label>
          <select v-model="form.courseNo" class="win-select" style="flex: 1" :disabled="!!form.id">
            <option value="">— 请选择课程目录中的课程 —</option>
            <option v-for="course in courses" :key="course.courseNo" :value="course.courseNo">
              {{ course.courseNo }} {{ course.title }}（{{ course.credits }} 学分 · {{ course.department }}）
            </option>
          </select>
        </div>
        <div class="win-field">
          <label>授课教授</label>
          <select v-model="form.professorId" class="win-select" style="flex: 1">
            <option :value="null">— 暂不分配（后续可编辑） —</option>
            <option v-for="professor in professors" :key="professor.id" :value="professor.id">
              {{ professor.professorNo }} {{ professor.name }}
            </option>
          </select>
        </div>
        <div class="win-field">
          <label>上课星期 *</label>
          <select v-model.number="form.dayOfWeek" class="win-select" style="flex: 1">
            <option v-for="(day, index) in dayNames" :key="index" :value="index + 1">{{ day }}</option>
          </select>
        </div>
        <div class="win-field">
          <label>开始时间 *</label>
          <input v-model="form.startTime" class="win-input" style="flex: 1" type="time" />
        </div>
        <div class="win-field">
          <label>结束时间 *</label>
          <input v-model="form.endTime" class="win-input" style="flex: 1" type="time" />
        </div>
        <div class="win-field">
          <label>教室</label>
          <input v-model.trim="form.room" class="win-input" style="flex: 1" />
        </div>
        <div class="win-field">
          <label>容量（≤10）</label>
          <input v-model.number="form.capacity" class="win-input" style="flex: 1" type="number" min="1" max="10" />
        </div>
        <div class="win-field">
          <label>最少开课人数</label>
          <input v-model.number="form.minStudents" class="win-input" style="flex: 1" type="number" min="1" :max="form.capacity || 10" />
        </div>
        <div class="hint">编辑时课程与学期不可变更；容量不得低于当前已选人数（{{ enrolledOfEditing }} 人）。</div>
        <div class="toolbar" style="justify-content: flex-end; margin-bottom: 0">
          <Win95Button label="保存" is-default :disabled="busy" @click="save" />
          <Win95Button label="清空表单" @click="resetForm" />
        </div>
      </fieldset>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { registrarApi } from '../../api'
import { useSemestersStore } from '../../stores/semesters'
import { useUiStore } from '../../stores/ui'
import SemesterSelect from '../../components/SemesterSelect.vue'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const semestersStore = useSemestersStore()
const dayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const semesterId = ref('')
const offerings = ref([])
const courses = ref([])
const professors = ref([])
const busy = ref(false)

const columns = [
  { key: 'offeringId', label: 'ID', width: '46px', align: 'center' },
  { key: 'courseNo', label: '课程号', width: '78px' },
  { key: 'title', label: '课程名称', width: '130px' },
  { key: 'credits', label: '学分', width: '46px', align: 'center' },
  { key: 'professorName', label: '授课教授', width: '90px' },
  { key: 'timeText', label: '上课时间', width: '150px' },
  { key: 'room', label: '教室', width: '70px' },
  { key: 'enrolled', label: '已选/容量', width: '90px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'actions', label: '操作', width: '120px' }
]

const emptyForm = {
  id: null,
  courseNo: '',
  professorId: null,
  dayOfWeek: 1,
  startTime: '08:00',
  endTime: '09:40',
  room: '',
  capacity: 10,
  minStudents: 3
}
const form = reactive({ ...emptyForm })

const enrolledOfEditing = computed(() => {
  const row = offerings.value.find((o) => o.offeringId === form.id)
  return row ? row.enrolledCount : 0
})

function statusText(status) {
  return { PROPOSED: '已提出', CONFIRMED: '已确认', CANCELLED: '已取消' }[status] || status
}

async function loadCourses() {
  try {
    courses.value = await registrarApi.courses()
  } catch (e) {
    courses.value = []
    await ui.alert(e.message, e.code === 'CATALOG_UNAVAILABLE' ? '课程目录不可用' : '课程目录加载失败')
  }
}

async function loadProfessors() {
  try {
    professors.value = await registrarApi.professors()
  } catch (e) {
    professors.value = []
  }
}

async function loadOfferings() {
  if (!semesterId.value) {
    offerings.value = []
    return
  }
  try {
    offerings.value = await registrarApi.offerings(semesterId.value)
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

function startCreate() {
  resetForm()
}

function resetForm() {
  Object.assign(form, emptyForm)
}

function pick(row) {
  Object.assign(form, {
    id: row.offeringId,
    courseNo: row.courseNo,
    professorId: row.professorId,
    dayOfWeek: row.dayOfWeek,
    startTime: (row.startTime || '').slice(0, 5),
    endTime: (row.endTime || '').slice(0, 5),
    room: row.room || '',
    capacity: row.capacity,
    minStudents: row.minStudents
  })
}

async function save() {
  if (!form.courseNo) {
    await ui.alert('请先选择课程目录中的课程。', '输入不完整')
    return
  }
  if (!form.startTime || !form.endTime) {
    await ui.alert('请填写上课的开始与结束时间。', '输入不完整')
    return
  }
  busy.value = true
  try {
    const payload = {
      semesterId: semesterId.value || null,
      courseNo: form.courseNo,
      dayOfWeek: form.dayOfWeek,
      startTime: form.startTime,
      endTime: form.endTime,
      room: form.room || null,
      capacity: form.capacity,
      minStudents: form.minStudents,
      professorId: form.professorId || null
    }
    if (form.id) {
      const updated = await registrarApi.updateOffering(form.id, payload)
      await ui.alert(`课程班【${updated.title}】已更新。`, '修改成功')
    } else {
      const created = await registrarApi.createOffering(payload)
      Object.assign(form, { id: created.offeringId })
      await ui.alert(`课程班【${created.title} ${created.timeText}】创建成功（状态：已提出）。`, '新建成功')
    }
    await loadOfferings()
  } catch (e) {
    await ui.alert(e.message, e.code === 'TIME_CONFLICT' ? '授课时间冲突' : '保存失败')
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  const ok = await ui.confirm(
    `确定要删除课程班【${row.courseNo} ${row.title}（${row.timeText}）】吗？\n仅当该课程班没有任何选课、课表或成绩引用时才能删除。此操作不可撤销。`,
    '删除确认'
  )
  if (!ok) {
    await ui.alert('已取消删除操作。', '操作取消')
    return
  }
  const again = await ui.confirm('请再次确认删除该课程班。', '二次确认')
  if (!again) {
    await ui.alert('已取消删除操作。', '操作取消')
    return
  }
  try {
    await registrarApi.deleteOffering(row.offeringId)
    await ui.alert('课程班已删除。', '删除成功')
    if (form.id === row.offeringId) resetForm()
    await loadOfferings()
  } catch (e) {
    await ui.alert(e.message, '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadProfessors()])
  await semestersStore.load()
  semesterId.value = semestersStore.currentId || (semestersStore.list[0] ? semestersStore.list[0].id : '')
  await loadOfferings()
})
</script>
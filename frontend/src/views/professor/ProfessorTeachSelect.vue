<!-- 教授：选择授课课程（Select Courses to Teach） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>授课资格课程（来自遗留课程目录系统）</legend>
      <div class="hint">
        教授只能从本人具备授课资格的课程中选择本学期课程班；选中课程班后系统将校验授课时间是否冲突。
      </div>
      <Win95Table
        :columns="courseColumns"
        :rows="courses"
        row-key="courseNo"
        max-height="300px"
        empty-text="没有可选择的授课课程"
      >
        <template #prerequisiteTitles="{ row }">
          <span v-if="row.prerequisiteTitles.length">{{ row.prerequisiteTitles.join('、') }}</span>
          <span v-else class="muted">无</span>
        </template>
        <template #offerings="{ row }">
          <div v-for="offering in row.offerings" :key="offering.offeringId" style="margin-bottom: 3px">
            <span class="badge" :class="offering.mine ? 'ok' : offering.selectionable ? 'info' : ''">
              {{ offering.timeText }} · {{ offering.room }} · 人数 {{ offering.enrolledCount }}/{{ offering.capacity }}
              <template v-if="offering.professorName"> · 授课：{{ offering.professorName }}</template>
              <template v-else> · 未分配教授</template>
            </span>
            <Win95Button
              v-if="!offering.mine"
              label="选择"
              size="small"
              :disabled="offering.professorId != null && !offering.mine"
              @click="select(offering)"
            />
            <Win95Button v-else label="取消选择" size="small" @click="unselect(offering)" />
          </div>
        </template>
      </Win95Table>
    </fieldset>

    <fieldset class="win-group">
      <legend>本学期我已选择的授课课程</legend>
      <Win95Table :columns="myColumns" :rows="myOfferings" row-key="offeringId" max-height="220px" empty-text="本学期尚未选择授课课程">
        <template #action="{ row }">
          <Win95Button label="取消选择" size="small" @click="unselect(row)" />
        </template>
      </Win95Table>
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
const courses = ref([])
const myOfferings = ref([])

const courseColumns = [
  { key: 'courseNo', label: '课程号', width: '84px' },
  { key: 'title', label: '课程名称', width: '150px' },
  { key: 'credits', label: '学分', width: '50px', align: 'center' },
  { key: 'department', label: '开课单位', width: '90px' },
  { key: 'prerequisiteTitles', label: '先修课程', width: '150px' },
  { key: 'offerings', label: '本学期课程班（时间 / 教室 / 人数 / 操作）' }
]

const myColumns = [
  { key: 'courseNo', label: '课程号', width: '84px' },
  { key: 'title', label: '课程名称', width: '150px' },
  { key: 'timeText', label: '上课时间', width: '160px' },
  { key: 'room', label: '教室', width: '80px' },
  { key: 'enrolledCount', label: '已选人数', width: '90px' },
  { key: 'action', label: '操作', width: '100px' }
]

async function load() {
  try {
    const [qualified, mine] = await Promise.all([professorApi.qualifiedCourses(), professorApi.myOfferings()])
    const mineIds = new Set(mine.map((o) => o.offeringId))
    courses.value = qualified.map((c) => ({
      ...c,
      offerings: c.offerings.map((o) => ({ ...o, mine: mineIds.has(o.offeringId) }))
    }))
    myOfferings.value = mine
    if (!courses.value.length) {
      await ui.alert('你没有具备授课资格的课程，请联系注册管理员。', '无可用课程')
    }
  } catch (e) {
    await ui.alert(e.message, '加载失败')
  }
}

async function select(offering) {
  try {
    await professorApi.select(offering.offeringId)
    await load()
    await ui.alert(`已选择【${offering.title} ${offering.timeText}】作为本学期授课课程。`, '选择成功')
  } catch (e) {
    await ui.alert(e.message, e.code === 'TIME_CONFLICT' ? '授课时间冲突' : '选择失败')
  }
}

async function unselect(offering) {
  const ok = await ui.confirm(`确定不再教授【${offering.title}】（${offering.timeText}）吗？`, '取消选择')
  if (!ok) return
  try {
    await professorApi.unselect(offering.offeringId)
    await load()
  } catch (e) {
    await ui.alert(e.message, '取消失败')
  }
}

onMounted(load)
</script>
<!-- 注册管理员：学期管理（新建/编辑学期、设为当前学期、重新开启注册） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>学期管理</legend>
      <div class="toolbar" style="margin-bottom: 4px">
        <Win95Button label="刷新列表" @click="load" />
        <Win95Button label="新建学期" is-default @click="startCreate" />
        <span class="badge info">当前学期：{{ currentSemester ? currentSemester.name : '未设置' }}</span>
        <span class="badge">共 {{ semesters.length }} 个学期</span>
      </div>
      <div class="hint">
        「重新开启注册」= 将学期注册状态改为「开放中」；如需正式关闭注册，建议使用「关闭注册」工作流
        （会同步执行课程班取消、备选补位与学费计算）。任意学期均可设为当前学期，学生选课与教授选授课仅作用于当前学期。
      </div>
    </fieldset>

    <div class="grid-2">
      <fieldset class="win-group">
        <legend>学期列表</legend>
        <Win95Table :columns="columns" :rows="semesters" row-key="id" :selected-key="form.id" max-height="360px" @row-click="pick">
          <template #registrationStatus="{ row }">
            <span class="badge" :class="row.registrationStatus === 'OPEN' ? 'ok' : 'warn'">
              {{ row.registrationStatus === 'OPEN' ? '开放中' : '已关闭' }}
            </span>
          </template>
          <template #currentFlag="{ row }">
            <span class="badge" :class="row.currentFlag ? 'ok' : ''">{{ row.currentFlag ? '当前学期' : '—' }}</span>
          </template>
          <template #actions="{ row }">
            <Win95Button label="编辑" size="small" @click.stop="pick(row)" />
            <Win95Button
              label="设为当前"
              size="small"
              :disabled="row.currentFlag || busy"
              @click.stop="setCurrent(row)"
            />
          </template>
        </Win95Table>
      </fieldset>

      <fieldset class="win-group">
        <legend>{{ form.id ? '编辑学期（ID：' + form.id + '）' : '新建学期' }}</legend>
        <div class="win-field">
          <label>学期代码 *</label>
          <input v-model.trim="form.code" class="win-input" style="flex: 1" placeholder="例如 2027-2028-1" />
        </div>
        <div class="win-field">
          <label>学期名称 *</label>
          <input v-model.trim="form.name" class="win-input" style="flex: 1" placeholder="例如 2027-2028学年第一学期" />
        </div>
        <div class="win-field">
          <label>开始日期 *</label>
          <input v-model="form.startDate" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="win-field">
          <label>结束日期 *</label>
          <input v-model="form.endDate" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="win-field">
          <label>调整期起</label>
          <input v-model="form.adjustmentStart" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="win-field">
          <label>调整期止</label>
          <input v-model="form.adjustmentEnd" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="win-field">
          <label>注册状态</label>
          <select v-model="form.registrationStatus" class="win-select" style="flex: 1">
            <option value="OPEN">开放中（可重新开启注册）</option>
            <option value="CLOSED">已关闭</option>
          </select>
        </div>
        <div v-if="form.registrationStatus === 'CLOSED'" class="hint">
          ⚠️ 手动改为「已关闭」不会执行课程班取消、备选补位与学费计算，建议在「关闭注册」页面执行正式关闭。
        </div>
        <div class="hint">说明：新建学期默认「注册开放中」且不会自动成为当前学期；暂不支持删除学期。</div>
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
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const semestersStore = useSemestersStore()
const semesters = ref([])
const busy = ref(false)

const columns = [
  { key: 'id', label: 'ID', width: '46px', align: 'center' },
  { key: 'code', label: '学期代码', width: '104px' },
  { key: 'name', label: '学期名称', width: '180px' },
  { key: 'startDate', label: '开始日期', width: '100px' },
  { key: 'endDate', label: '结束日期', width: '100px' },
  { key: 'registrationStatus', label: '注册状态', width: '90px' },
  { key: 'adjustmentStart', label: '调整期起', width: '100px' },
  { key: 'adjustmentEnd', label: '调整期止', width: '100px' },
  { key: 'currentFlag', label: '当前', width: '84px' },
  { key: 'actions', label: '操作', width: '150px' }
]

const emptyForm = {
  id: null,
  code: '',
  name: '',
  startDate: '',
  endDate: '',
  adjustmentStart: '',
  adjustmentEnd: '',
  registrationStatus: 'OPEN'
}
const form = reactive({ ...emptyForm })

const currentSemester = computed(() => semesters.value.find((s) => s.currentFlag) || null)

async function load() {
  try {
    semesters.value = await registrarApi.semesters()
    await semestersStore.load(true)
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
    id: row.id,
    code: row.code,
    name: row.name,
    startDate: row.startDate || '',
    endDate: row.endDate || '',
    adjustmentStart: row.adjustmentStart || '',
    adjustmentEnd: row.adjustmentEnd || '',
    registrationStatus: row.registrationStatus || 'OPEN'
  })
}

async function setCurrent(row) {
  const messages = [`确定将【${row.name}】设为当前学期吗？`]
  const original = currentSemester.value
  if (original && original.registrationStatus === 'OPEN' && original.id !== row.id) {
    messages.push(`原当前学期【${original.name}】注册仍处于开放中，切换后学生端选课将作用于新学期。`)
  }
  const ok = await ui.confirm(messages.join('\n'), '设置当前学期')
  if (!ok) return
  busy.value = true
  try {
    await registrarApi.setCurrentSemester(row.id)
    await load()
    await ui.alert(`【${row.name}】已设为当前学期。`, '设置成功')
  } catch (e) {
    await ui.alert(e.message, '设置失败')
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!form.code || !form.name || !form.startDate || !form.endDate) {
    await ui.alert('学期代码、名称与起止日期均为必填项。', '输入不完整')
    return
  }
  if (form.registrationStatus === 'CLOSED') {
    const ok = await ui.confirm(
      '你正在手动关闭注册：该操作不会执行课程班取消、备选补位与学费计算。\n确定继续吗？',
      '关闭注册确认'
    )
    if (!ok) return
  }
  busy.value = true
  try {
    const payload = {
      code: form.code,
      name: form.name,
      startDate: form.startDate,
      endDate: form.endDate,
      adjustmentStart: form.adjustmentStart || null,
      adjustmentEnd: form.adjustmentEnd || null,
      registrationStatus: form.registrationStatus
    }
    if (form.id) {
      const updated = await registrarApi.updateSemester(form.id, payload)
      await ui.alert(`学期【${updated.name}】已更新（注册状态：${updated.registrationStatus === 'OPEN' ? '开放中' : '已关闭'}）。`, '修改成功')
    } else {
      const created = await registrarApi.createSemester(payload)
      Object.assign(form, { id: created.id })
      await ui.alert(`学期【${created.name}】创建成功，注册状态为「开放中」。`, '新建成功')
    }
    await load()
  } catch (e) {
    await ui.alert(e.message, '保存失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
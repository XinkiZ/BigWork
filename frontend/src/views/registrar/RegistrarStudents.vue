<!-- 注册管理员：维护学生信息（Maintain Student Information） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>查询</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <input v-model.trim="keyword" class="win-input" placeholder="按学号或姓名检索" style="width: 200px" />
        <Win95Button label="查询" @click="load" />
        <Win95Button label="显示全部" @click="showAll" />
        <span class="badge">共 {{ students.length }} 名学生</span>
      </div>
    </fieldset>

    <div class="grid-2">
      <fieldset class="win-group">
        <legend>学生列表</legend>
        <Win95Table
          :columns="columns"
          :rows="students"
          row-key="id"
          :selected-key="selectedRow ? selectedRow.id : null"
          max-height="330px"
          @row-click="pick"
        >
          <template #status="{ row }">
            <span class="badge" :class="row.status === 'ACTIVE' ? 'ok' : 'warn'">{{ statusText(row.status) }}</span>
          </template>
        </Win95Table>
      </fieldset>

      <fieldset class="win-group">
        <legend>{{ form.id ? '修改学生信息（ID：' + form.id + '）' : '新增学生（学号由系统自动生成）' }}</legend>
        <div class="win-field">
          <label>学号</label>
          <input class="win-input" style="flex: 1" :value="form.id ? form.studentNo : '（保存后自动生成）'" disabled />
        </div>
        <div class="win-field">
          <label>姓名 *</label>
          <input v-model.trim="form.name" class="win-input" style="flex: 1" />
        </div>
        <div class="win-field">
          <label>出生日期</label>
          <input v-model="form.birthDate" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="win-field">
          <label>社保号</label>
          <input v-model.trim="form.ssn" class="win-input" style="flex: 1" />
        </div>
        <div class="win-field">
          <label>状态</label>
          <select v-model="form.status" class="win-select" style="flex: 1">
            <option value="ACTIVE">在读（ACTIVE）</option>
            <option value="LEAVE">休学（LEAVE）</option>
            <option value="GRADUATED">已毕业（GRADUATED）</option>
          </select>
        </div>
        <div class="win-field">
          <label>毕业日期</label>
          <input v-model="form.graduationDate" class="win-input" style="flex: 1" type="date" />
        </div>
        <div class="toolbar" style="justify-content: flex-end; margin-bottom: 0">
          <Win95Button label="保存" is-default :disabled="busy" @click="save" />
          <Win95Button label="清空表单" @click="resetForm" />
          <Win95Button label="删除学生" :disabled="!form.id || busy" @click="remove" />
        </div>
      </fieldset>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { registrarApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const students = ref([])
const keyword = ref('')
const selectedRow = ref(null)
const busy = ref(false)

const columns = [
  { key: 'id', label: 'ID', width: '52px', align: 'center' },
  { key: 'studentNo', label: '学号', width: '96px' },
  { key: 'name', label: '姓名', width: '84px' },
  { key: 'birthDate', label: '出生日期', width: '104px' },
  { key: 'ssn', label: '社保号', width: '150px' },
  { key: 'status', label: '状态', width: '82px' },
  { key: 'graduationDate', label: '毕业日期', width: '104px' }
]

const form = reactive({ id: null, studentNo: '', name: '', birthDate: '', ssn: '', status: 'ACTIVE', graduationDate: '' })

function statusText(status) {
  return { ACTIVE: '在读', LEAVE: '休学', GRADUATED: '已毕业' }[status] || status
}

async function load() {
  students.value = await registrarApi.students(keyword.value || undefined)
}

async function showAll() {
  keyword.value = ''
  await load()
}

function pick(row) {
  selectedRow.value = row
  Object.assign(form, {
    id: row.id,
    studentNo: row.studentNo,
    name: row.name,
    birthDate: row.birthDate || '',
    ssn: row.ssn || '',
    status: row.status || 'ACTIVE',
    graduationDate: row.graduationDate || ''
  })
}

function resetForm() {
  selectedRow.value = null
  Object.assign(form, { id: null, studentNo: '', name: '', birthDate: '', ssn: '', status: 'ACTIVE', graduationDate: '' })
}

async function save() {
  if (!form.name) {
    await ui.alert('姓名不能为空。', '输入不完整')
    return
  }
  busy.value = true
  try {
    const payload = {
      name: form.name,
      birthDate: form.birthDate || null,
      ssn: form.ssn || null,
      status: form.status,
      graduationDate: form.graduationDate || null
    }
    if (form.id) {
      const updated = await registrarApi.updateStudent(form.id, payload)
      Object.assign(form, { studentNo: updated.studentNo })
      await ui.alert(`学生【${updated.name}】信息已更新。`, '修改成功')
    } else {
      const created = await registrarApi.createStudent(payload)
      Object.assign(form, { id: created.id, studentNo: created.studentNo })
      await ui.alert(`新增学生成功，系统生成学号：${created.studentNo}\n初始口令为 123456，请提醒学生及时修改。`, '新增成功')
    }
    await load()
  } catch (e) {
    await ui.alert(e.message, '保存失败')
  } finally {
    busy.value = false
  }
}

async function remove() {
  const ok = await ui.confirm(
    `确定要删除学生【${form.name}（${form.studentNo}）】吗？\n此操作不可撤销，请再次确认。`,
    '删除确认'
  )
  if (!ok) {
    await ui.alert('已取消删除操作。', '操作取消')
    return
  }
  busy.value = true
  try {
    await registrarApi.deleteStudent(form.id)
    await ui.alert('学生记录已删除。', '删除成功')
    resetForm()
    await load()
  } catch (e) {
    await ui.alert(e.message, '删除失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
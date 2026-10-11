<!-- 注册管理员：维护教授信息（Maintain Professor Information） -->
<template>
  <div>
    <fieldset class="win-group">
      <legend>查询</legend>
      <div class="toolbar" style="margin-bottom: 0">
        <input v-model.trim="keyword" class="win-input" placeholder="按教授编号或姓名检索" style="width: 200px" />
        <Win95Button label="查询" @click="load" />
        <Win95Button label="显示全部" @click="showAll" />
        <span class="badge">共 {{ professors.length }} 名教授</span>
      </div>
    </fieldset>

    <div class="grid-2">
      <fieldset class="win-group">
        <legend>教授列表</legend>
        <Win95Table
          :columns="columns"
          :rows="pageRows"
          row-key="id"
          :selected-key="selectedRow ? selectedRow.id : null"
          max-height="330px"
          @row-click="pick"
        >
          <template #status="{ row }">
            <span class="badge" :class="row.status === 'ACTIVE' ? 'ok' : 'warn'">{{ statusText(row.status) }}</span>
          </template>
        </Win95Table>
        <div class="toolbar" style="margin: 6px 0 0 0">
          <Win95Button label="上一页" size="small" :disabled="page <= 1" @click="page--" />
          <Win95Button label="下一页" size="small" :disabled="page >= totalPages" @click="page++" />
          <span class="badge">第 {{ page }} / {{ totalPages }} 页（每页 20 条，共 {{ professors.length }} 条）</span>
        </div>
      </fieldset>

      <fieldset class="win-group">
        <legend>{{ form.id ? '修改教授信息（ID：' + form.id + '）' : '新增教授（编号由系统自动生成）' }}</legend>
        <div class="win-field">
          <label>教授编号</label>
          <input class="win-input" style="flex: 1" :value="form.id ? form.professorNo : '（保存后自动生成）'" disabled />
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
            <option value="ACTIVE">在职（ACTIVE）</option>
            <option value="LEAVE">休假（LEAVE）</option>
            <option value="RETIRED">退休（RETIRED）</option>
          </select>
        </div>
        <div class="win-field">
          <label>所属院系</label>
          <input v-model.trim="form.department" class="win-input" style="flex: 1" />
        </div>
        <div class="toolbar" style="justify-content: flex-end; margin-bottom: 0">
          <Win95Button label="保存" is-default :disabled="busy" @click="save" />
          <Win95Button label="清空表单" @click="resetForm" />
          <Win95Button label="删除教授" :disabled="!form.id || busy" @click="remove" />
        </div>
      </fieldset>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { registrarApi } from '../../api'
import { useUiStore } from '../../stores/ui'
import Win95Button from '../../components/Win95Button.vue'
import Win95Table from '../../components/Win95Table.vue'

const ui = useUiStore()
const professors = ref([])
const keyword = ref('')
const selectedRow = ref(null)
const busy = ref(false)

const pageSize = 20
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(professors.value.length / pageSize)))
const pageRows = computed(() => professors.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const columns = [
  { key: 'id', label: 'ID', width: '52px', align: 'center' },
  { key: 'professorNo', label: '编号', width: '76px' },
  { key: 'name', label: '姓名', width: '84px' },
  { key: 'birthDate', label: '出生日期', width: '104px' },
  { key: 'department', label: '所属院系', width: '112px' },
  { key: 'status', label: '状态', width: '82px' }
]

const form = reactive({
  id: null,
  professorNo: '',
  name: '',
  birthDate: '',
  ssn: '',
  status: 'ACTIVE',
  department: ''
})

function statusText(status) {
  return { ACTIVE: '在职', LEAVE: '休假', RETIRED: '退休' }[status] || status
}

async function load() {
  professors.value = await registrarApi.professors(keyword.value || undefined)
  page.value = 1
}

async function showAll() {
  keyword.value = ''
  await load()
}

function pick(row) {
  selectedRow.value = row
  Object.assign(form, {
    id: row.id,
    professorNo: row.professorNo,
    name: row.name,
    birthDate: row.birthDate || '',
    ssn: row.ssn || '',
    status: row.status || 'ACTIVE',
    department: row.department || ''
  })
}

function resetForm() {
  selectedRow.value = null
  Object.assign(form, { id: null, professorNo: '', name: '', birthDate: '', ssn: '', status: 'ACTIVE', department: '' })
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
      department: form.department || null
    }
    if (form.id) {
      const updated = await registrarApi.updateProfessor(form.id, payload)
      await ui.alert(`教授【${updated.name}】信息已更新。`, '修改成功')
    } else {
      const created = await registrarApi.createProfessor(payload)
      Object.assign(form, { id: created.id, professorNo: created.professorNo })
      await ui.alert(`新增教授成功，系统生成编号：${created.professorNo}\n初始口令为 123456。`, '新增成功')
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
    `确定要删除教授【${form.name}（${form.professorNo}）】吗？\n删除后其课程班将变为未分配教授，请再次确认。`,
    '删除确认'
  )
  if (!ok) {
    await ui.alert('已取消删除操作。', '操作取消')
    return
  }
  busy.value = true
  try {
    await registrarApi.deleteProfessor(form.id)
    await ui.alert('教授记录已删除。', '删除成功')
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
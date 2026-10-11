<!-- 学期下拉选择器：options 来自 semesters store，支持"全部学期"与筛选口径 -->
<template>
  <span class="semester-select">
    <label v-if="label">{{ label }}</label>
    <select
      class="win-select"
      :style="{ minWidth: minWidth || '230px' }"
      :value="normalizedValue"
      :disabled="disabled"
      @change="onChange"
    >
      <option v-if="includeAll" value="">{{ allLabel }}</option>
      <option v-if="placeholder" value="">{{ placeholder }}</option>
      <option v-for="option in options" :key="option.id" :value="String(option.id)">
        {{ optionLabel(option) }}
      </option>
    </select>
    <span v-if="showStatus" class="badge" :class="selectedSemester && selectedSemester.registrationStatus === 'OPEN' ? 'ok' : 'warn'">
      注册{{ selectedSemester && selectedSemester.registrationStatus === 'OPEN' ? '开放中' : '已关闭' }}
    </span>
  </span>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useSemestersStore } from '../stores/semesters'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  includeAll: { type: Boolean, default: false },
  allLabel: { type: String, default: '全部学期' },
  placeholder: { type: String, default: '' },
  /** 仅列出"已开始且非当前"的学期（成绩录入口径） */
  onlyGradeable: { type: Boolean, default: false },
  minWidth: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  showStatus: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const store = useSemestersStore()

const source = computed(() => (props.onlyGradeable ? store.gradeable : store.list))

const options = computed(() =>
  source.value.map((s) => ({
    ...s,
    label: `${s.name}${s.currentFlag ? '（当前学期）' : ''}`
  }))
)

const normalizedValue = computed(() => (props.modelValue == null ? '' : String(props.modelValue)))
const selectedSemester = computed(() => store.byId(Number(props.modelValue)) || null)

function optionLabel(option) {
  return option.label
}

function onChange(event) {
  const raw = event.target.value
  const value = raw === '' ? '' : Number(raw)
  emit('update:modelValue', value)
  emit('change', value)
}

onMounted(() => {
  store.load()
})
</script>
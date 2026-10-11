<!-- Win95 列表视图表格：columns=[{key,label,width,align}] -->
<template>
  <div class="win-table-wrap" :style="{ maxHeight: maxHeight }">
    <table class="win-table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="{ width: col.width, textAlign: col.align || 'left' }">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, index) in rows"
          :key="rowKey ? row[rowKey] : index"
          :class="{ selected: selectedKey != null && row[rowKey] === selectedKey }"
          @click="$emit('row-click', row)"
          @dblclick="$emit('row-dblclick', row)"
        >
          <td v-for="col in columns" :key="col.key" :style="{ textAlign: col.align || 'left' }">
            <slot :name="col.key" :row="row" :value="row[col.key]">{{ row[col.key] }}</slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!rows || rows.length === 0" class="win-table-empty">{{ emptyText }}</div>
  </div>
</template>

<script setup>
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  selectedKey: { type: [String, Number, null], default: null },
  maxHeight: { type: String, default: '360px' },
  emptyText: { type: String, default: '（没有可显示的数据）' }
})
defineEmits(['row-click', 'row-dblclick'])
</script>
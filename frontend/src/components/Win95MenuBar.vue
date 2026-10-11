<!-- Win95 菜单栏：menus=[{label, items:[{label, action, disabled, separator}]}] -->
<template>
  <div class="win-menubar" @mouseleave="openIndex = -1">
    <div
      v-for="(menu, index) in menus"
      :key="menu.label"
      class="menu"
      :class="{ open: openIndex === index }"
      @click="toggle(index)"
      @mouseenter="openIndex >= 0 && (openIndex = index)"
    >
      {{ menu.label }}
      <div v-if="openIndex === index" class="win-menu-popup">
        <template v-for="(item, itemIndex) in menu.items" :key="itemIndex">
          <div v-if="item.separator" class="win-menu-sep"></div>
          <div
            v-else
            class="win-menu-item"
            :class="{ disabled: item.disabled }"
            @click.stop="run(item)"
          >
            {{ item.label }}
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({ menus: { type: Array, default: () => [] } })
const emit = defineEmits(['command'])

const openIndex = ref(-1)

function toggle(index) {
  openIndex.value = openIndex.value === index ? -1 : index
}

function run(item) {
  if (item.disabled) return
  openIndex.value = -1
  emit('command', item)
  if (typeof item.action === 'function') item.action()
}
</script>
<!-- Win95 窗口：标题栏（含最小化/最大化/关闭按钮）+ 内容区 + 状态栏插槽 -->
<template>
  <section class="win-window">
    <div class="win-titlebar">
      <span class="title-icon">{{ icon }}</span>
      <span class="title">{{ title }}</span>
      <div class="win-titlebar-buttons">
        <button type="button" title="最小化" @click="$emit('minimize')">_</button>
        <button type="button" title="最大化" @click="$emit('maximize')">□</button>
        <button type="button" title="关闭" @click="$emit('close')">✕</button>
      </div>
    </div>
    <div class="win-window-body">
      <slot />
    </div>
    <div v-if="$slots.status" class="win-statusbar">
      <slot name="status" />
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '未命名窗口' },
  icon: { type: String, default: '🗔' }
})
defineEmits(['close', 'minimize', 'maximize'])
</script>
<!-- Win95 消息框 / 确认框（全局单例，由 stores/ui.js 驱动） -->
<template>
  <div v-if="ui.dialog.visible" class="win-overlay">
    <div class="win-dialog">
      <div class="win-titlebar">
        <span class="title-icon">{{ iconText }}</span>
        <span class="title">{{ ui.dialog.title }}</span>
      </div>
      <div class="win-dialog-body">
        <div class="win-dialog-icon">{{ glyph }}</div>
        <div class="win-dialog-text">{{ ui.dialog.message }}</div>
      </div>
      <div class="win-dialog-buttons">
        <Win95Button
          v-for="btn in ui.dialog.buttons"
          :key="String(btn.value)"
          :label="btn.label"
          :is-default="!!btn.default"
          @click="ui.close(btn.value)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUiStore } from '../stores/ui'
import Win95Button from './Win95Button.vue'

const ui = useUiStore()

const glyph = computed(() => ({ info: 'ℹ️', warn: '⚠️', error: '⛔', question: '❓' })[ui.dialog.icon] || 'ℹ️')
const iconText = computed(() => ui.dialog.title)
</script>
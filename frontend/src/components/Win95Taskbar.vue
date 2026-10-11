<!-- 任务栏：开始菜单 + 窗口按钮 + 系统时间 -->
<template>
  <footer class="win-taskbar">
    <button type="button" class="win-btn start-btn" @click="startOpen = !startOpen">🪟 开始</button>
    <div v-if="startOpen" class="win-menu-popup" style="bottom: 34px; top: auto">
      <div class="win-menu-item" @click="go('home')">🏠 返回工作台</div>
      <div class="win-menu-item" @click="go('refresh')">🔄 刷新当前页面</div>
      <div class="win-menu-sep"></div>
      <div class="win-menu-item disabled">关于 Wylie College 选课注册系统 v1.0</div>
      <div class="win-menu-sep"></div>
      <div class="win-menu-item" @click="go('logout')">🚪 注销登录</div>
    </div>
    <div class="task-divider"></div>
    <button
      v-for="item in windows"
      :key="item.path"
      type="button"
      class="win-btn task-button"
      :class="{ active: item.active }"
      @click="$emit('activate', item)"
    >
      {{ item.icon }} {{ item.title }}
    </button>
    <div class="tray">
      <span>{{ auth.roleText }}：{{ auth.user?.name }}</span>
      <span class="mono">{{ clock }}</span>
    </div>
  </footer>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'

defineProps({ windows: { type: Array, default: () => [] } })
const emit = defineEmits(['activate', 'command'])

const auth = useAuthStore()
const startOpen = ref(false)
const now = ref(new Date())
let timer = null

const clock = computed(() =>
  now.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
)

function go(command) {
  startOpen.value = false
  emit('command', command)
}

onMounted(() => {
  timer = setInterval(() => (now.value = new Date()), 1000 * 20)
})
onBeforeUnmount(() => timer && clearInterval(timer))
</script>
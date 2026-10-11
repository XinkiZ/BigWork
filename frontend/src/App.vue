<!-- 应用外壳：Win95 桌面 + 主窗口（菜单栏 + 路由页面）+ 任务栏 + 全局弹窗 -->
<template>
  <div v-if="isLogin" class="login-screen">
    <router-view />
    <Win95Dialog />
  </div>

  <div v-else class="desktop">
    <div class="desktop-icons">
      <div
        v-for="item in icons"
        :key="item.path"
        class="desktop-icon"
        :class="{ active: route.path === item.path, disabled: item.disabled }"
        @click="open(item)"
      >
        <span class="glyph">{{ item.icon }}</span>
        <span class="caption">{{ item.label }}</span>
      </div>
    </div>

    <Win95Window :title="windowTitle" icon="🗔" @close="onClose">
      <Win95MenuBar :menus="menus" @command="onMenuCommand" />
      <router-view />
      <template #status>
        <span class="cell fixed">👤 {{ auth.roleText }}：{{ auth.user?.name }}（{{ auth.user?.username }}）</span>
        <span class="cell">{{ statusText }}</span>
        <span class="cell fixed">学期：{{ currentSemesterName }}</span>
        <span class="cell fixed">注册状态：{{ registrationStatusText }}</span>
      </template>
    </Win95Window>

    <Win95Taskbar :windows="taskbarWindows" @activate="onActivate" @command="onTaskbarCommand" />
    <Win95Dialog />
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useSemestersStore } from './stores/semesters'
import { useUiStore } from './stores/ui'
import Win95Window from './components/Win95Window.vue'
import Win95MenuBar from './components/Win95MenuBar.vue'
import Win95Taskbar from './components/Win95Taskbar.vue'
import Win95Dialog from './components/Win95Dialog.vue'

const auth = useAuthStore()
const semestersStore = useSemestersStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

/** 顶栏「学期/注册状态」实时取自 /api/semesters，避免登录快照过期 */
const currentSemesterName = computed(() => {
  const current = semestersStore.current
  return current ? current.name : auth.user?.currentSemesterName || '—'
})
const registrationStatusText = computed(() => {
  const current = semestersStore.current
  const status = current ? current.registrationStatus : auth.user?.registrationStatus
  return status === 'CLOSED' ? '已关闭' : '开放中'
})

async function refreshSemesters() {
  if (!auth.loggedIn) return
  try {
    await semestersStore.load(true)
  } catch (e) {
    /* 顶栏静默失败，不打断当前页面 */
  }
}

watch(
  () => auth.loggedIn,
  (logged) => {
    if (logged) refreshSemesters()
  }
)
router.afterEach(() => {
  refreshSemesters()
})

const isLogin = computed(() => route.path === '/login')
const windowTitle = computed(() => `Wylie College 学生选课注册系统 —— ${route.meta.title || '工作台'}`)

const STUDENT_ICONS = [
  { label: '课程注册', icon: '📝', path: '/student' },
  { label: '我的课表', icon: '📅', path: '/student/schedule' },
  { label: '电子成绩单', icon: '📄', path: '/student/report-card' },
  { label: '学业记录', icon: '📊', path: '/student/transcript' },
  { label: '我的账单', icon: '🧾', path: '/student/billing' }
]

const PROFESSOR_ICONS = [
  { label: '选择授课', icon: '🎓', path: '/professor' },
  { label: '选课名单', icon: '👥', path: '/professor/roster' },
  { label: '成绩录入', icon: '✍️', path: '/professor/grades' },
  { label: '系统面板', icon: '⚙️', path: '/system' }
]

const REGISTRAR_ICONS = [
  { label: '学生信息', icon: '🧑‍🎓', path: '/registrar' },
  { label: '教授信息', icon: '🧑‍🏫', path: '/registrar/professors' },
  { label: '学期管理', icon: '📖', path: '/registrar/semesters' },
  { label: '课程班管理', icon: '🏫', path: '/registrar/offerings' },
  { label: '关闭注册', icon: '🔒', path: '/registrar/close-registration' },
  { label: '计费监控', icon: '💰', path: '/registrar/billing' },
  { label: '系统面板', icon: '⚙️', path: '/system' }
]

const icons = computed(() => {
  if (auth.isStudent) return STUDENT_ICONS
  if (auth.isProfessor) return PROFESSOR_ICONS
  if (auth.isRegistrar) return REGISTRAR_ICONS
  return []
})

const menus = computed(() => [
  {
    label: '文件(F)',
    items: [
      ...icons.value.map((i) => ({ label: i.label, path: i.path })),
      { separator: true },
      { label: '注销登录', command: 'logout' }
    ]
  },
  {
    label: '视图(V)',
    items: [
      { label: '刷新当前页面', command: 'refresh' },
      { separator: true },
      { label: '当前学期：' + currentSemesterName.value, disabled: true },
      { label: '注册状态：' + registrationStatusText.value, disabled: true }
    ]
  },
  {
    label: '帮助(H)',
    items: [
      { label: '系统演示面板' + (auth.isRegistrar ? '' : '（只读）'), path: '/system' },
      { label: '关于本系统', command: 'about' }
    ]
  }
])

const taskbarWindows = computed(() =>
  icons.value.map((i) => ({ ...i, title: i.label, active: route.path === i.path }))
)

const loading = ref(false)
const statusText = computed(() => `${route.meta.title || ''}${loading.value ? '（正在处理…）' : '　就绪'}`)

async function open(item) {
  if (item.disabled) return
  if (route.path === item.path) return
  await router.push(item.path)
}

function onActivate(item) {
  router.push(item.path)
}

async function onMenuCommand(item) {
  if (item.path) return open(item)
  if (item.command === 'logout') return logout()
  if (item.command === 'refresh') return router.go(0)
  if (item.command === 'about') {
    return ui.alert(
      'Wylie College 学生选课注册系统 v2.0（多学期迭代）\n' +
        '软件工程课程设计 —— 小组项目\n' +
        '技术栈：Vue3 + Spring Boot 3 + MySQL 8\n' +
        '界面规范：Windows 95/98 桌面交互规范',
      '关于本系统'
    )
  }
}

async function onTaskbarCommand(command) {
  if (command === 'home') return router.push(auth.homePath)
  if (command === 'refresh') return router.go(0)
  if (command === 'logout') return logout()
}

async function logout() {
  const ok = await ui.confirm('确定要注销当前登录吗？', '注销登录')
  if (!ok) return
  auth.logout()
  router.push('/login')
}

async function onClose() {
  await ui.alert('这是系统主窗口，如需退出请使用任务栏「开始 → 注销登录」。', '提示')
}

// 令牌过期时由 HTTP 拦截器调用，直接回到登录页
function onUnauthorized(message) {
  if (route.path === '/login') return
  auth.logout()
  ui.alert(message + '（请重新登录）', '登录状态失效')
  router.push('/login')
}

function onUnauthorizedEvent(event) {
  onUnauthorized(event.detail)
}

onMounted(() => {
  window.addEventListener('wylie:unauthorized', onUnauthorizedEvent)
  refreshSemesters()
})
onBeforeUnmount(() => {
  window.removeEventListener('wylie:unauthorized', onUnauthorizedEvent)
})
</script>
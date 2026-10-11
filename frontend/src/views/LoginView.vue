<!-- 登录（Login）：三种角色共用，账号自动识别角色 -->
<template>
  <Win95Window title="Wylie College 学生选课注册系统 —— 登录" icon="🔑">
    <div class="login-banner">
      <h1>WYLIE COLLEGE</h1>
      <div>学生选课注册系统（Course Registration System）</div>
    </div>

    <form class="win-group" style="margin: 0 auto; max-width: 460px" @submit.prevent="doLogin">
      <legend>用户登录</legend>
      <div class="win-field">
        <label for="username">用户名</label>
        <input
          id="username"
          v-model.trim="form.username"
          class="win-input"
          style="flex: 1"
          placeholder="学号 / 教授编号 / 管理员账号"
          autocomplete="username"
        />
      </div>
      <div class="win-field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          class="win-input"
          style="flex: 1"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </div>
      <div v-if="error" class="hint" style="color: #a00000">⛔ {{ error }}</div>
      <div class="toolbar" style="justify-content: flex-end; margin-bottom: 0">
        <Win95Button label="登录" is-default :disabled="busy" @click="doLogin" />
        <Win95Button label="清空" @click="reset" />
      </div>
    </form>

    <fieldset class="win-group" style="max-width: 620px; margin: 12px auto 0 auto">
      <legend>演示账号（点击可自动填入）</legend>
      <div class="grid-3">
        <div v-for="account in accounts" :key="account.username" class="hint">
          <a href="#" @click.prevent="fill(account)">{{ account.label }}</a>
          <div class="muted mono">{{ account.username }} / {{ account.password }}</div>
        </div>
      </div>
    </fieldset>

    <template #status>
      <span class="cell">受限软件：仅限 Wylie College 校内使用</span>
      <span class="cell fixed">{{ version }}</span>
    </template>
  </Win95Window>
  <div style="text-align: center; color: #fff; margin-top: 8px">
    Copyright © 2026 Wylie College · 软件工程课程设计
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import Win95Window from '../components/Win95Window.vue'
import Win95Button from '../components/Win95Button.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const form = reactive({ username: '', password: '' })
const error = ref('')
const busy = ref(false)
const version = 'v1.0.0'

const accounts = [
  { label: '学生：陈晨', username: '2026001', password: '123456' },
  { label: '学生：郑爽（备选补位演示）', username: '2026010', password: '123456' },
  { label: '教授：张伟（计算机）', username: 'P001', password: '123456' },
  { label: '教授：王强（计算机）', username: 'P003', password: '123456' },
  { label: '注册管理员', username: 'registrar', password: 'admin123' }
]

function fill(account) {
  form.username = account.username
  form.password = account.password
  error.value = ''
}

function reset() {
  form.username = ''
  form.password = ''
  error.value = ''
}

async function doLogin() {
  if (busy.value) return
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  busy.value = true
  try {
    const data = await auth.login(form.username, form.password)
    await router.push(auth.homePath)
    await ui.alert(
      `登录成功！\n欢迎您，${data.name}（${auth.roleText}）\n当前学期：${data.currentSemesterName || '—'}`,
      '登录成功'
    )
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    busy.value = false
  }
}
</script>
import { defineStore } from 'pinia'
import { authApi } from '../api'

const TOKEN_KEY = 'wylie_token'
const USER_KEY = 'wylie_user'

/** 登录态（JWT + 角色），角色决定进入哪一端工作台 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    loggedIn: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    isStudent: (state) => state.user?.role === 'STUDENT',
    isProfessor: (state) => state.user?.role === 'PROFESSOR',
    isRegistrar: (state) => state.user?.role === 'REGISTRAR',
    roleText: (state) =>
      ({ STUDENT: '学生', PROFESSOR: '教授', REGISTRAR: '注册管理员' })[state.user?.role] || '未登录',
    homePath: (state) =>
      ({ STUDENT: '/student', PROFESSOR: '/professor', REGISTRAR: '/registrar' })[state.user?.role] || '/login'
  },
  actions: {
    async login(username, password) {
      const data = await authApi.login({ username, password })
      this.token = data.token
      this.user = data
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data))
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
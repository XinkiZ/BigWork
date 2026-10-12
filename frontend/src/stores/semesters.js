import { defineStore } from 'pinia'
import { semesterApi } from '../api'

/** 本地日期（YYYY-MM-DD），与后端 LocalDate 文本一致 */
function localToday() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function byStartAsc(a, b) {
  return (a.startDate || '').localeCompare(b.startDate || '') || a.id - b.id
}

/** 学期先后：候选学期是否早于基准学期（日期缺失时退回按 id 比较），与后端 isBefore 口径一致 */
function isBefore(candidate, base) {
  if (candidate.startDate && base.startDate) {
    return candidate.startDate < base.startDate
  }
  return candidate.id < base.id
}

/** 全角色共用的学期列表缓存：顶栏状态、各页面学期下拉均从这里取数 */
export const useSemestersStore = defineStore('semesters', {
  state: () => ({
    list: [],
    loaded: false,
    loadingPromise: null
  }),
  getters: {
    current: (state) => state.list.find((s) => s.currentFlag) || null,
    currentId() {
      return this.current ? this.current.id : null
    },
    /** 上一学期：当前学期之前最近一个学期（无当前学期时退回最新已开始学期） */
    previous(state) {
      const sorted = [...state.list].sort(byStartAsc)
      const current = sorted.find((s) => s.currentFlag)
      if (current) {
        const before = sorted.filter((s) => isBefore(s, current))
        return before.length ? before[before.length - 1] : null
      }
      const started = sorted.filter((s) => s.startDate && s.startDate <= localToday())
      return started.length ? started[started.length - 1] : null
    },
    /** 可录入成绩的学期：当前学期之前的已开始学期（按开始日期倒序，最近的在前；无前序学期时为空列表） */
    gradeable(state) {
      const today = localToday()
      const current = state.list.find((s) => s.currentFlag)
      return state.list
        .filter((s) => !s.currentFlag && s.startDate && s.startDate <= today)
        .filter((s) => (current ? isBefore(s, current) : true))
        .sort((a, b) => byStartAsc(b, a))
    }
  },
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return this.list
      if (this.loadingPromise) return this.loadingPromise
      this.loadingPromise = semesterApi
        .list()
        .then((list) => {
          this.list = list
          this.loaded = true
          return list
        })
        .finally(() => {
          this.loadingPromise = null
        })
      return this.loadingPromise
    },
    byId(id) {
      return this.list.find((s) => s.id === id) || null
    }
  }
})
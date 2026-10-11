import { defineStore } from 'pinia'

let resolver = null

/** 全局弹窗（Win95 风格消息框/确认框）状态 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    dialog: {
      visible: false,
      title: '',
      message: '',
      icon: 'info',
      buttons: []
    }
  }),
  actions: {
    open(title, message, icon, buttons) {
      return new Promise((resolve) => {
        resolver = resolve
        this.dialog = { visible: true, title, message, icon, buttons }
      })
    },
    alert(message, title = '提示') {
      return this.open(title, message, 'info', [{ label: '确定', value: true, default: true }])
    },
    confirm(message, title = '确认') {
      return this.open(title, message, 'question', [
        { label: '确定', value: true, default: true },
        { label: '取消', value: false }
      ])
    },
    choose(title, message, options) {
      return this.open(title, message, 'question', options)
    },
    close(value) {
      const fn = resolver
      resolver = null
      this.dialog.visible = false
      if (fn) fn(value)
    }
  }
})

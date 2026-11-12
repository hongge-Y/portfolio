/**
 * 应用级共享状态：未读消息数等"全站都要看"的数字。
 *
 * 顶部导航、底部 tab、首页、消息页都读这里，避免各自调接口导致数字不一致。
 */
import { defineStore } from 'pinia'
import { messageApi } from '@/api/message'

export const useAppStore = defineStore('app', {
  state: () => ({
    unread: 0,
    unreadLoaded: false
  }),
  getters: {
    unreadText: (s) => (s.unread > 99 ? '99+' : String(s.unread)),
    hasUnread: (s) => s.unread > 0
  },
  actions: {
    /** 拉一次未读数；失败静默（红点错了不影响主流程） */
    async refreshUnread() {
      try {
        const res = await messageApi.unread()
        this.unread = res?.total ?? 0
        this.unreadLoaded = true
      } catch {
        /* 静默：未读数不是关键路径 */
      }
    },
    /** 读了一封邮件：本地先减，避免为了一个红点再请求一次 */
    decrease(step = 1) {
      this.unread = Math.max(0, this.unread - step)
    },
    /** 全部已读 / 换账号 */
    reset(value = 0) {
      this.unread = value
      this.unreadLoaded = false
    }
  }
})

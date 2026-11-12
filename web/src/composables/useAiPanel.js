import { ref } from 'vue'

/**
 * AI 抽屉的共享状态。
 *
 * open    —— 开关；
 * pending —— 待发送的问题：首页快捷提问卡点了之后写进来，
 *            AiAssistant 打开时取走并自动发送，不用把 send 暴露成全局方法。
 */
const open = ref(false)
const pending = ref('')

export function useAiPanel() {
  return {
    open,
    pending,
    show() {
      open.value = true
    },
    hide() {
      open.value = false
    },
    toggle() {
      open.value = !open.value
    },
    /** 打开抽屉并带上一个问题 */
    ask(question) {
      pending.value = question
      open.value = true
    },
    /** 取走待发送问题（取完即清空，避免重复发送） */
    takePending() {
      const q = pending.value
      pending.value = ''
      return q
    }
  }
}

/**
 * 确认弹窗（Promise 版）
 *
 *   const { confirm } = useConfirm()
 *   if (await confirm({ content: '确定取消这条请假吗？', tone: 'danger' })) { ... }
 *
 * 用状态驱动 <ConfirmHost>（挂在 App.vue，全局一个），
 * 比原生 confirm() 好在：样式统一、移动端不会被浏览器拦、可以带副标题。
 */
import { reactive } from 'vue'

const state = reactive({
  visible: false,
  title: '请确认',
  content: '',
  confirmText: '确定',
  cancelText: '取消',
  tone: 'primary',
  _resolve: null
})

export function useConfirm() {
  function confirm(options = {}) {
    // 前一个还没关就被再次调用：先按取消结算，避免 Promise 永远懸着
    if (state._resolve) {
      state._resolve(false)
      state._resolve = null
    }
    const {
      title = '请确认',
      content = '',
      confirmText = '确定',
      cancelText = '取消',
      tone = 'primary'
    } = options
    state.title = title
    state.content = content
    state.confirmText = confirmText
    state.cancelText = cancelText
    state.tone = tone
    state.visible = true
    return new Promise((resolve) => {
      state._resolve = resolve
    })
  }

  function settle(result) {
    state.visible = false
    const resolve = state._resolve
    state._resolve = null
    resolve?.(result)
  }

  return { confirm, state, settle, confirmState: state }
}

/** 组件里直接拿状态用（ConfirmHost 用这个，避免再包一层） */
export const confirmState = state
export const settleConfirm = (result) => {
  state.visible = false
  const resolve = state._resolve
  state._resolve = null
  resolve?.(result)
}

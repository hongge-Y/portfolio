/**
 * 单资源加载状态机（loading / error / empty / ready）。
 *
 * 页面里不再手写 loading.value = true → try/catch → errorText.value = ...，
 * 只留一句：const { data, status, error, run } = useAsyncData(() => api.xxx())
 *
 * 关键点：
 * - status 直接喂给 <DataState>，四个分支只写一次；
 * - 竞态保护：连续切换筛选时，只有最后一次请求的结果会落到 data；
 * - emptyWhen 可自定义"什么算空"，默认空数组 / null / undefined 都算空。
 */
import { ref } from 'vue'
import { humanMessage } from '@/utils/crypto'

function defaultIsEmpty(value) {
  if (value === null || value === undefined) return true
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object' && 'list' in value) return !value.list?.length
  return false
}

export function useAsyncData(loader, options = {}) {
  const { immediate = true, emptyWhen = defaultIsEmpty, fallbackError = '加载失败，请稍后重试' } =
    options

  const data = ref(null)
  const status = ref(immediate ? 'loading' : 'ready')
  const error = ref('')
  let seq = 0

  async function run() {
    const mine = ++seq
    status.value = 'loading'
    error.value = ''
    try {
      const res = await loader()
      if (mine !== seq) return data.value
      data.value = res
      status.value = emptyWhen(res) ? 'empty' : 'ready'
      return res
    } catch (e) {
      if (mine !== seq) return null
      data.value = null
      error.value = humanMessage(e?.message, fallbackError)
      status.value = 'error'
      return null
    }
  }

  if (immediate) void run()

  return { data, status, error, run, refresh: run }
}

/**
 * 分页列表状态机。
 *
 * 之前每个列表页都复制一遍 pageNum / total / loading / errorText / load()，
 * 现在统一在这里。页面只提供 fetcher：
 *
 *   const { items, total, status, error, load, next, prev } = usePagedList(
 *     ({ pageNum, pageSize }) => contentApi.newsPortal(pageNum, pageSize)
 *   )
 *
 * 约定的返回结构：{ list | records, total }；直接给数组也支持（视为单页）。
 */
import { computed, ref } from 'vue'
import { humanMessage } from '@/utils/crypto'

function pickList(res) {
  if (Array.isArray(res)) return res
  return res?.list ?? res?.records ?? res?.rows ?? []
}

export function usePagedList(fetcher, options = {}) {
  const {
    pageSize = 10,
    immediate = true,
    /** 是否在页面上显示分页条（数据不足一页时自动隐藏） */
    fallbackError = '加载失败，请稍后重试'
  } = options

  const items = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const status = ref('loading')
  const error = ref('')
  let seq = 0

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
  const hasPrev = computed(() => pageNum.value > 1)
  const hasNext = computed(() => pageNum.value < totalPages.value)
  const rangeText = computed(() => {
    if (!total.value) return '0 条'
    const from = (pageNum.value - 1) * pageSize + 1
    const to = Math.min(pageNum.value * pageSize, total.value)
    return `${from}-${to} / ${total.value}`
  })

  async function load({ reset = false, page = null } = {}) {
    if (reset) pageNum.value = 1
    if (page) pageNum.value = page
    const mine = ++seq
    status.value = 'loading'
    error.value = ''
    try {
      const res = await fetcher({ pageNum: pageNum.value, pageSize })
      if (mine !== seq) return
      const list = pickList(res)
      items.value = list
      total.value = res?.total ?? list.length
      status.value = list.length ? 'ready' : 'empty'
    } catch (e) {
      if (mine !== seq) return
      items.value = []
      total.value = 0
      error.value = humanMessage(e?.message, fallbackError)
      status.value = 'error'
    }
  }

  function next() {
    if (!hasNext.value) return
    void load({ page: pageNum.value + 1 })
  }

  function prev() {
    if (!hasPrev.value) return
    void load({ page: pageNum.value - 1 })
  }

  if (immediate) void load({ reset: true })

  return {
    items,
    total,
    pageNum,
    pageSize,
    status,
    error,
    totalPages,
    hasPrev,
    hasNext,
    rangeText,
    load,
    refresh: () => load(),
    next,
    prev
  }
}

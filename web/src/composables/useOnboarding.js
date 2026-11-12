/**
 * 新手引导的跨页状态（选宠 → 开礼盒 两页共用）。
 *
 * 为什么要共享：奖励是在"选宠"那一步由后端发的（+100 光粒 / +1 礼盒），
 * 礼盒页只负责演出。演出要显示真实增量，就得把选宠前后的账户快照带过去。
 * 用模块级 reactive，刷新页面会丢（丢了大不了按无增量显示，不报错）。
 */
import { reactive } from 'vue'

export const onboarding = reactive({
  /** 选中的小宠 id（1~5） */
  pet: 0,
  /** 选宠前的账户快照 { coin, gift } */
  before: null,
  /** 选宠后的账户快照 { coin, gift } */
  after: null
})

export function useOnboarding() {
  function reset() {
    onboarding.pet = 0
    onboarding.before = null
    onboarding.after = null
  }

  /** 选宠成功后调用：带上前后快照，礼盒页据此算增量 */
  function complete({ pet, before, after }) {
    onboarding.pet = pet
    onboarding.before = before
    onboarding.after = after
  }

  /** 光粒 / 礼盒的真实增量；拿不到快照就退回固定奖励 */
  function delta(fallback = { coin: 0, gift: 0 }) {
    if (!onboarding.before || !onboarding.after) return fallback
    return {
      coin: Math.max(0, (onboarding.after.coin ?? 0) - (onboarding.before.coin ?? 0)),
      gift: Math.max(0, (onboarding.after.gift ?? 0) - (onboarding.before.gift ?? 0))
    }
  }

  return { onboarding, reset, complete, delta }
}

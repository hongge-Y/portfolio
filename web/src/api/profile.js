import { request } from '@/utils/request'

/**
 * 个人中心接口（对应后端 ProfileController /v1/profile/*）。
 * 后端身份一律从 token 里取，这里不需要传 userId。
 */
export const profileApi = {
  /** 我的资料：showName/gender/avatar/coin/gift/pet/roles/permissions/loginCount… */
  me: () => request({ url: '/v1/profile/me', method: 'get' }),

  /** 修改资料：showName / nickName / birthday / gender / pet / petHide / avatar */
  update: (data) => request({ url: '/v1/profile/me', method: 'put', data }),

  /** 修改密码：旧密码 + 新密码（6-20 位且同时含字母和数字） */
  password: (oldPassword, newPassword) =>
    request({ url: '/v1/profile/password', method: 'post', data: { oldPassword, newPassword } }),

  /** 绑定手机(type='mobile') / 邮箱(type='mail')，需要当前密码确认 */
  bind: (type, value, password) =>
    request({ url: '/v1/profile/bind', method: 'post', data: { type, value, password } }),

  /** 组织与角色：employee + units + roles + permissions */
  org: () => request({ url: '/v1/profile/org', method: 'get' }),

  /** 我的风险预警（学业/退学预警等），无风险返回空数组 */
  risks: () => request({ url: '/v1/profile/risks', method: 'get' }),

  /** 近期登录记录（含失败） */
  loginLogs: () => request({ url: '/v1/profile/login-logs', method: 'get' }),

  /** 今日任务 */
  today: () => request({ url: '/v1/profile/today', method: 'get' }),

  /** 提交今日任务 */
  submitToday: (id, text) =>
    request({ url: `/v1/profile/today/${id}/submit`, method: 'post', data: { text } }),

  /** 完成新手引导（选小宠 pet: 1~5） */
  guide: (pet) => request({ url: '/v1/profile/guide', method: 'post', data: { pet } })
}

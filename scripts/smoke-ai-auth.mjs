// 后端新增接口冒烟：验证码 → 发码 → 验证码登录 → AI 各场景
const BASE = 'http://127.0.0.1:8106/api'

const post = async (p, body, token) => {
  const res = await fetch(BASE + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body || {}),
  })
  return res.json()
}
const get = async (p, token) => {
  const res = await fetch(BASE + p, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
  return res.json()
}

const ok = (name, cond, extra) => console.log(`${cond ? '[PASS]' : '[FAIL]'} ${name}${extra ? ' :: ' + extra : ''}`)

;(async () => {
  // ---- A. 公钥 + 图形码
  const pk = await get('/v1/auth/public-key')
  ok('GET /v1/auth/public-key', pk.code === 200 && pk.data.publicKey.includes('BEGIN PUBLIC KEY'), pk.data?.keySize)

  const cap = await get('/v1/auth/captcha')
  const svg = Buffer.from(cap.data.svg, 'base64').toString('utf8')
  const chars = [...svg.matchAll(/<text[^>]*>([^<])<\/text>/g)].map((m) => m[1]).join('')
  ok('GET /v1/auth/captcha', cap.code === 200 && chars.length === 4, '识别出图形码=' + chars)

  // ---- B. 手机验证码登录
  const target = '13800138000' // 林同学
  const sent = await post('/v1/auth/code/send', {
    target, type: 'MOBILE', captchaId: cap.data.captchaId, captchaCode: chars,
  })
  ok('POST /v1/auth/code/send(手机)', sent.code === 200 && /^\d{6}$/.test(sent.data?.demoCode || ''),
    'demoCode=' + sent.data?.demoCode + ' 发往=' + sent.data?.target)

  const codeLogin = await post('/v1/auth/login/code', { target, type: 'MOBILE', code: sent.data.demoCode })
  ok('POST /v1/auth/login/code(手机)', codeLogin.code === 200 && !!codeLogin.data?.accessToken,
    codeLogin.code === 200 ? '登录=' + codeLogin.data.showName + ' 角色=' + codeLogin.data.roles : codeLogin.message)

  // 同一验证码重复用必须失败（一次性）
  const reuse = await post('/v1/auth/login/code', { target, type: 'MOBILE', code: sent.data.demoCode })
  ok('验证码一次性（重复使用被拒）', reuse.code !== 200, reuse.message)

  // ---- C. 邮箱验证码登录
  const cap2 = (await get('/v1/auth/captcha')).data
  const svg2 = Buffer.from(cap2.svg, 'base64').toString('utf8')
  const chars2 = [...svg2.matchAll(/<text[^>]*>([^<])<\/text>/g)].map((m) => m[1]).join('')
  const mail = '2025000001@example.edu'
  const sentMail = await post('/v1/auth/code/send', {
    target: mail, type: 'MAIL', captchaId: cap2.captchaId, captchaCode: chars2,
  })
  ok('POST /v1/auth/code/send(邮箱)', sentMail.code === 200, 'demoCode=' + sentMail.data?.demoCode)
  const mailLogin = await post('/v1/auth/login/code', { target: mail, type: 'MAIL', code: sentMail.data.demoCode })
  ok('POST /v1/auth/login/code(邮箱)', mailLogin.code === 200 && !!mailLogin.data?.accessToken)

  // 图形码错误时要被拦
  const badCap = await post('/v1/auth/code/send', { target, type: 'MOBILE', captchaId: 'x', captchaCode: 'zzzz' })
  ok('图形码错误拦截发码', badCap.code === 400, badCap.message)

  const token = codeLogin.data.accessToken

  // ---- D. AI
  const st = await get('/v1/ai/status', token)
  ok('GET /v1/ai/status', st.code === 200 && st.data.enabled === true, st.data?.provider + ' / ' + st.data?.model)

  const chat = await post('/v1/ai/chat', { message: '我明天要请一天病假，需要准备什么材料？用两句话回答' }, token)
  ok('POST /v1/ai/chat', chat.code === 200 && (chat.data?.text || '').length > 10,
    chat.code === 200 ? `${chat.data.costMs}ms :: ${chat.data.text.slice(0, 60)}…` : chat.message)

  const draft = await post('/v1/ai/leave-draft', {
    type: 1, startTime: '2026-11-02 08:00', endTime: '2026-11-03 18:00', days: 2, remark: '发烧需要就医',
  }, token)
  ok('POST /v1/ai/leave-draft', draft.code === 200 && (draft.data?.text || '').length > 20,
    draft.code === 200 ? `${draft.data.costMs}ms :: ${draft.data.text.slice(0, 60)}…` : draft.message)

  // 流式
  const t0 = Date.now()
  const res = await fetch(BASE + '/v1/ai/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify({ message: '用三句话介绍这个平台能帮我做什么' }),
  })
  const reader = res.body.getReader()
  const dec = new TextDecoder()
  let text = '', buf = '', events = [], first = 0
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += dec.decode(value, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop()
    for (const line of lines) {
      const s = line.trim()
      if (s.startsWith('event:')) events.push(s.slice(6).trim())
      if (s.startsWith('data:')) {
        try {
          const j = JSON.parse(s.slice(5).trim())
          if (j.d) { if (!first) first = Date.now() - t0; text += j.d }
        } catch {}
      }
    }
  }
  ok('POST /v1/ai/chat/stream(SSE)', text.length > 10 && events.includes('done'),
    `首字=${first}ms 总=${Date.now() - t0}ms 事件=${[...new Set(events)].join(',')} 字数=${text.length}`)

  // 辅导员：审批建议
  const counselor = await post('/v1/auth/login/password', { account: '1001001', password: '123456' })
  const ctoken = counselor.data.accessToken
  const pend = await post('/v1/leave/approve/search', { pageNum: 1, pageSize: 5, searchModel: {} }, ctoken)
  const leaveId = pend.data?.list?.[0]?.id
  if (leaveId) {
    const sug = await post('/v1/ai/approve-suggest', { leaveId }, ctoken)
    ok('POST /v1/ai/approve-suggest', sug.code === 200 && (sug.data?.text || '').length > 10,
      sug.code === 200 ? sug.data.text.replace(/\n/g, ' | ').slice(0, 80) : sug.message)
  } else {
    console.log('[SKIP] 没有待审批假条')
  }
  // 学生调审批建议必须 403
  const forbid = await post('/v1/ai/approve-suggest', { leaveId: 1 }, token)
  ok('学生调审批建议被 403', forbid.code === 403, forbid.message)

  // 新闻摘要
  const news = await get('/v1/publish/news/portal?pageNum=1&pageSize=1', token)
  const nid = news.data?.list?.[0]?.id
  const sum = await post('/v1/ai/news-summary', { newsId: nid }, token)
  ok('POST /v1/ai/news-summary', sum.code === 200 && (sum.data?.text || '').length > 10,
    sum.code === 200 ? sum.data.text.replace(/\n/g, ' | ').slice(0, 90) : sum.message)
})()

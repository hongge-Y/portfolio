import { request } from "@/utils/request";
const aiApi = {
  status: () => request({ url: "/v1/ai/status", method: "get", silentToast: true }),
  /** 一次性对话（不支持流式的兜底路径） */
  chat: (message, history = []) => request({
    url: "/v1/ai/chat",
    method: "post",
    data: { message, history },
    timeout: 12e4,
    silentToast: true
  }),
  leaveDraft: (data) => request({ url: "/v1/ai/leave-draft", method: "post", data, timeout: 12e4, silentToast: true }),
  approveSuggest: (leaveId) => request({
    url: "/v1/ai/approve-suggest",
    method: "post",
    data: { leaveId },
    timeout: 12e4,
    silentToast: true
  }),
  newsSummary: (newsId) => request({
    url: "/v1/ai/news-summary",
    method: "post",
    data: { newsId },
    timeout: 12e4,
    silentToast: true
  })
};
export {
  aiApi
};

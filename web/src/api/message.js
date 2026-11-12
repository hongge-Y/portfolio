import { request } from "@/utils/request";
const messageApi = {
  inbox: (body) => request({ url: "/v1/message/inbox/search", method: "post", data: body }),
  sent: (body) => request({ url: "/v1/message/search", method: "post", data: body }),
  inboxDetail: (id) => request({ url: `/v1/message/inbox/${id}`, method: "get" }),
  sentDetail: (id) => request({ url: `/v1/message/${id}`, method: "get" }),
  unread: () => request({ url: "/v1/message/unread-count", method: "get" }),
  senders: () => request({ url: "/v1/message/senders", method: "get" }),
  read: (id) => request({ url: `/v1/message/inbox/${id}/read`, method: "post" }),
  removeInbox: (id) => request({ url: `/v1/message/inbox/${id}`, method: "delete" })
};
/** 消息等级：实现放在 features/message/level.js，这里保持旧导出名兼容 */
export { levelClass, levelText } from '@/features/message/level'
export { messageApi }

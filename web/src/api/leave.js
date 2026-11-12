import { request } from "@/utils/request";
const search = (url, body) => request({ url, method: "post", data: body });
const leaveApi = {
  rule: () => request({ url: "/v1/leave/rule", method: "get" }),
  apply: (data) => request({ url: "/v1/leave/apply", method: "post", data }),
  cancel: (id) => request({ url: `/v1/leave/${id}/cancel`, method: "post" }),
  returnLeave: (id, endTime) => request({ url: `/v1/leave/${id}/return`, method: "post", data: endTime ? { endTime } : {} }),
  extend: (id, endTime) => request({ url: `/v1/leave/${id}/extend`, method: "post", data: { endTime } }),
  personal: (body) => search("/v1/leave/personal/search", body),
  classPage: (body) => search("/v1/leave/class/search", body),
  approvePage: (body) => search("/v1/leave/approve/search", body),
  detail: (id) => request({ url: `/v1/leave/${id}`, method: "get" }),
  approve: (data) => request({ url: "/v1/leave/approve", method: "post", data })
};
export {
  leaveApi
};

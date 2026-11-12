import { request } from "@/utils/request";
const get = (url) => request({ url, method: "get" });
const relativeApi = {
  apply: (employeeNo, remark) => request({ url: "/v1/relative/apply", method: "post", data: { employeeNo, remark } }),
  applyByStudent: (parentMobile) => request({ url: "/v1/relative/apply-by-student", method: "post", data: { parentMobile } }),
  search: (status) => request({
    url: "/v1/relative/search",
    method: "post",
    data: { pageNum: 1, pageSize: 50, searchModel: status === void 0 ? {} : { status } }
  }),
  agree: (id) => request({ url: `/v1/relative/${id}/agree`, method: "post" }),
  reject: (id, remark) => request({ url: `/v1/relative/${id}/reject`, method: "post", data: { remark } }),
  unbind: (id) => request({ url: `/v1/relative/${id}/unbind`, method: "post" }),
  children: () => get("/v1/relative/children"),
  pending: () => get("/v1/relative/pending-count")
};
export {
  relativeApi
};

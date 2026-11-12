import { request } from "@/utils/request";
const get = (url) => request({ url, method: "get" });
const post = (url, data) => request({ url, method: "post", data: data ?? {} });
const scholarshipApi = {
  plans: () => get("/v1/scholarship/plan/available"),
  planDetail: (id) => get(`/v1/scholarship/plan/${id}`),
  apply: (data) => post("/v1/scholarship/apply", data),
  myApplies: () => post("/v1/scholarship/apply/my", { pageNum: 1, pageSize: 50 }),
  approvePage: () => post("/v1/scholarship/approve/search", { pageNum: 1, pageSize: 50 }),
  approve: (applyId, result, reason) => post("/v1/scholarship/approve", { applyId, result, reason }),
  publicity: (planId) => get(
    `/v1/scholarship/publicity${planId ? `?planId=${planId}` : ""}`
  ),
  applyDetail: (id) => get(`/v1/scholarship/apply/${id}`),
  cancel: (id) => post(`/v1/scholarship/apply/${id}/cancel`)
};
export {
  scholarshipApi
};

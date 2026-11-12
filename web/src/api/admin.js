import { request } from "@/utils/request";
const get = (url) => request({ url, method: "get" });
const post = (url, data) => request({ url, method: "post", data: data ?? {} });
const del = (url) => request({ url, method: "delete" });
const adminApi = {
  stats: () => get("/v1/admin/stats"),
  employeeLookup: (employeeNo) => get(
    `/v1/admin/employee-lookup?employeeNo=${encodeURIComponent(employeeNo)}`
  ),
  orgOptions: () => get("/v1/admin/org-options"),
  // 新闻
  newsSearch: (body) => post("/v1/publish/news/search", body),
  saveNews: (body) => post("/v1/publish/news", body),
  deleteNews: (id) => del(`/v1/publish/news/${id}`),
  newsDetail: (id) => get(`/v1/publish/news/portal/${id}`),
  // 广告
  adSearch: (body) => post("/v1/publish/ad/search", body),
  saveAd: (body) => post("/v1/publish/ad", body),
  deleteAd: (id) => del(`/v1/publish/ad/${id}`),
  // 赛事
  matchSearch: (body) => post("/v1/publish/match/search", body),
  saveMatch: (body) => post("/v1/publish/match", body),
  deleteMatch: (id) => del(`/v1/publish/match/${id}`),
  // 红黑榜
  rankSearch: (body) => post("/v1/publish/rank/search", body),
  saveRank: (body) => post("/v1/publish/rank", body),
  deleteRank: (id) => del(`/v1/publish/rank/${id}`),
  rankLabels: () => get("/v1/publish/rank/label"),
  // 课表导入
  importTimetable: (body) => post("/v1/edu/timetable/import", body),
  // 奖学金
  planSearch: (body) => post("/v1/scholarship/plan/search", body),
  savePlan: (body) => post("/v1/scholarship/plan", body),
  rankPlan: (id, quota) => post(`/v1/scholarship/plan/${id}/rank`, quota ? { quota } : {}),
  publicityPlan: (id) => post(`/v1/scholarship/plan/${id}/publicity`, {}),
  grantPlan: (id) => post(`/v1/scholarship/plan/${id}/grant`, {}),
  publicity: (planId) => get(`/v1/scholarship/publicity?planId=${planId}`)
};
export {
  adminApi
};

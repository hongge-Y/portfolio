import { request } from "@/utils/request";
const get = (url) => request({ url, method: "get" });
const contentApi = {
  adPortal: () => get("/v1/publish/ad/portal"),
  newsPortal: (pageNum = 1, pageSize = 10) => get(`/v1/publish/news/portal?pageNum=${pageNum}&pageSize=${pageSize}`),
  newsDetail: (id) => get(`/v1/publish/news/portal/${id}`),
  rankLabels: (red) => get(`/v1/publish/rank/label${red === void 0 ? "" : `?red=${red}`}`),
  rankPortal: (red, pageNum = 1, pageSize = 10) => get(
    `/v1/publish/rank/portal?pageNum=${pageNum}&pageSize=${pageSize}${red === void 0 ? "" : `&red=${red}`}`
  ),
  rankDetail: (id) => get(
    `/v1/publish/rank/portal/${id}`
  ),
  matchPortal: (pageNum = 1, pageSize = 10) => get(`/v1/publish/match/portal?pageNum=${pageNum}&pageSize=${pageSize}`),
  matchDetail: (id) => get(`/v1/publish/match/portal/${id}`),
  registerMatch: (id, data) => request({ url: `/v1/publish/match/${id}/register`, method: "post", data }),
  like: (targetType, targetId, liked) => request({
    url: "/v1/publish/like",
    method: "post",
    data: { targetType, targetId, liked }
  })
};
export {
  contentApi
};

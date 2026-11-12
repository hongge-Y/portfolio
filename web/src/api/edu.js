import { request } from "@/utils/request";
const get = (url) => request({ url, method: "get" });
const eduApi = {
  term: () => get("/v1/edu/term/current"),
  timetable: (term, weekNo) => get(
    `/v1/edu/timetable${term || weekNo ? "?" : ""}${term ? `term=${term}` : ""}${term && weekNo ? "&" : ""}${weekNo ? `weekNo=${weekNo}` : ""}`
  ),
  evalOptions: (term) => get(`/v1/edu/eval/options${term ? `?term=${term}` : ""}`),
  submitEval: (data) => request({ url: "/v1/edu/eval", method: "post", data }),
  myEvals: (term) => get(`/v1/edu/eval/my${term ? `?term=${term}` : ""}`)
};
export {
  eduApi
};

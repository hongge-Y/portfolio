import { leaveStatusClass } from "@/features/leave/status";

function toApiDateTime(v) {
  if (!v) return "";
  const s = v.replace("T", " ");
  return s.length === 16 ? `${s}:00` : s;
}
function toInputDateTime(v) {
  if (!v) return "";
  return v.replace(" ", "T").slice(0, 16);
}
function statusClass(status) {
  // 实现统一在 features/leave/status.js，这里保留旧函数名给历史页面用
  return leaveStatusClass(status)
}
function calcDays(start, end) {
  if (!start || !end) return 0;
  const s = new Date(start).getTime();
  const e = new Date(end).getTime();
  if (Number.isNaN(s) || Number.isNaN(e) || e <= s) return 0;
  return Math.ceil((e - s) / 864e5);
}
export {
  calcDays,
  statusClass,
  toApiDateTime,
  toInputDateTime
};

import JSEncrypt from "jsencrypt";
import { authApi } from "@/api/auth";
let cachedKey = "";
let cachedAt = 0;
const KEY_TTL = 10 * 60 * 1e3;
async function getLoginPublicKey(force = false) {
  const stale = Date.now() - cachedAt > KEY_TTL;
  if (!cachedKey || stale || force) {
    const res = await authApi.publicKey();
    cachedKey = res.publicKey;
    cachedAt = Date.now();
  }
  return cachedKey;
}
function clearKeyCache() {
  cachedKey = "";
  cachedAt = 0;
}
async function buildSign(password) {
  let key = await getLoginPublicKey();
  if (!key) throw new Error("获取登录公钥失败，请刷新页面");
  const time = Date.now();
  let sign = encrypt(key, password, time);
  if (!sign) {
    key = await getLoginPublicKey(true);
    sign = encrypt(key, password, time);
  }
  if (!sign) throw new Error("密码加密失败，请刷新页面重试");
  return { sign, time, key };
}
function encrypt(publicKey, password, timestamp) {
  const enc = new JSEncrypt();
  enc.setPublicKey(publicKey);
  return enc.encrypt(JSON.stringify({ password, timestamp }));
}
function humanMessage(msg, fallback = "操作失败，请稍后重试") {
  if (!msg) return fallback;
  const m = msg.match(/^[A-Z][A-Z0-9_]{2,30}:(.+)$/s);
  return (m ? m[1] : msg).trim() || fallback;
}
export {
  buildSign,
  clearKeyCache,
  getLoginPublicKey,
  humanMessage
};

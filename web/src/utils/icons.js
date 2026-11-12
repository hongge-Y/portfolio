import phone from "@/assets/field/phone.svg";
import mail from "@/assets/field/mail.svg";
import user from "@/assets/field/user.svg";
import lock from "@/assets/field/lock.svg";
import bolt from "@/assets/field/bolt.svg";
import eye from "@/assets/field/eye.svg";
import eyeOff from "@/assets/field/eye-off.svg";
import refresh from "@/assets/field/refresh.svg";
import appLogo from "@/assets/logo/app.png";
import wechat from "@/assets/oauth/wechat.png";
import weibo from "@/assets/oauth/weibo.png";
import alipay from "@/assets/oauth/alipay.png";
const FIELD_ICONS = {
  phone,
  mail,
  user,
  lock,
  bolt,
  eye,
  "eye-off": eyeOff,
  refresh
};
function fieldIcon(name) {
  return FIELD_ICONS[name] ?? "";
}
const OAUTH_ICONS = { wechat, weibo, alipay };
function oauthIcon(key) {
  return OAUTH_ICONS[key] ?? "";
}
const APP_LOGO = appLogo;
export {
  APP_LOGO,
  fieldIcon,
  oauthIcon
};

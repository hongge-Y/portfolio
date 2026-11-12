#!/usr/bin/env bash
# P3 冒烟：家长关联闭环 + 课表 + 课程评价
API="http://localhost:8106/api"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.2/bin/mysql"
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
no(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
body(){ printf '%s' "$1" > .smoke-body.json; }
login(){
  body "{\"account\":\"$1\",\"password\":\"123456\"}"
  curl -s -X POST "$API/v1/auth/login/password" -H "Content-Type: application/json" \
       --data-binary "@.smoke-body.json" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'
}
post(){ body "$3"; curl -s -X POST "$API$1" -H "Content-Type: application/json;charset=UTF-8" \
        -H "Authorization: Bearer $2" --data-binary "@.smoke-body.json"; }
get(){ curl -s "$API$1" -H "Authorization: Bearer $2"; }
del(){ curl -s -X DELETE "$API$1" -H "Authorization: Bearer $2"; }
jnum(){ echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }

# 清理上一次测试留下的关联（只针对测试家长账号）
"$MYSQL" -uroot -p123456 -h127.0.0.1 -P3307 portfolio -e \
  "DELETE FROM rel_relative_link WHERE parent_user_id = (SELECT id FROM sys_user WHERE account='13900001111');" 2>/dev/null

PAR=$(login 13900001111)
STU=$(login 2025309702)
ADM=$(login admin)
[ ${#PAR} -gt 100 ] && ok "家长 / 学生 / 管理员 登录成功" || no "登录失败"

STU_ME=$(get /v1/auth/me "$STU")
EMPID=$(jnum "$STU_ME" employeeId)
STUID=$(jnum "$STU_ME" userId)

echo
echo "=== 1. 家长用学号申请关联 ==="
R=$(post /v1/relative/apply "$PAR" '{"employeeNo":"2025309702","remark":"我是学生02的家长"}')
echo "  响应: $R"
LINK=$(jnum "$R" id)
[ "$(jnum "$R" status)" = "0" ] && ok "申请创建成功，状态待确认 id=$LINK" || no "申请失败"

echo
echo "=== 2. 重复申请应被拒 ==="
R=$(post /v1/relative/apply "$PAR" '{"employeeNo":"2025309702"}')
echo "$R" | grep -q "RELATIVE_EXISTS" && ok "重复申请被拦截" || no "未拦截: $R"

echo
echo "=== 3. 学生端红点（待确认数）==="
R=$(get /v1/relative/pending-count "$STU")
echo "  待确认: $R"
[ "$(jnum "$R" data)" -ge 1 ] && ok "学生收到待确认红点" || no "没有红点: $R"

echo
echo "=== 4. 学生同意 ==="
R=$(post "/v1/relative/$LINK/agree" "$STU" '{}')
echo "  响应: $R"
[ "$(jnum "$R" status)" = "1" ] && ok "关联已建立" || no "同意失败: $R"

echo
echo "=== 5. 家长可见的孩子列表 ==="
R=$(get /v1/relative/children "$PAR")
echo "  $R"
echo "$R" | grep -q "2025309702" && ok "家长能看到孩子" || no "孩子列表为空"

echo
echo "=== 6. 家长能看孩子的红榜 ==="
R=$(get "/v1/publish/rank/portal?red=true&pageNum=1&pageSize=10" "$PAR")
echo "$R" | grep -q "本学期全勤" && ok "家长看到孩子红榜" || no "看不到红榜: $R"

echo
echo "=== 7. 黑榜 relativeVisible=false 家长不可见 ==="
R=$(post /v1/publish/rank "$ADM" "{\"employeeId\":$EMPID,\"title\":\"[冒烟]家长不可见黑榜\",\"red\":false,\"labelId\":5,\"type\":5,\"updateTime\":\"2026-10-29 10:00:00\",\"status\":1,\"relativeVisible\":false,\"content\":\"<p>测试</p>\"}")
RID=$(jnum "$R" data)
[ -n "$RID" ] && ok "管理员建黑榜 id=$RID" || no "建黑榜失败: $R"
R=$(get "/v1/publish/rank/portal?red=false&pageNum=1&pageSize=10" "$PAR")
echo "$R" | grep -q "家长不可见黑榜" && no "家长看到了不该看的黑榜" || ok "家长看不到 relativeVisible=0 的黑榜"
R=$(get "/v1/publish/rank/portal?red=false&pageNum=1&pageSize=10" "$STU")
echo "$R" | grep -q "家长不可见黑榜" && ok "学生自己能看该黑榜" || no "学生看不到自己的黑榜"
[ -n "$RID" ] && del "/v1/publish/rank/$RID" "$ADM" >/dev/null

echo
echo "=== 8. 课表：家长无课表 / 学生有课表 ==="
R=$(get "/v1/edu/timetable" "$PAR")
echo "$R" | grep -q "未归属班级" && ok "家长课表为空并说明原因" || no "家长课表异常: $R"
R=$(get "/v1/edu/timetable" "$STU")
CNT=$(echo "$R" | grep -o '"courseName"' | wc -l)
[ "$CNT" -ge 1 ] && ok "学生课表 $CNT 节课" || no "学生课表为空: $R"

echo
echo "=== 9. 课程评价 ==="
R=$(get "/v1/edu/eval/options" "$STU")
N=$(echo "$R" | grep -o '"courseId"' | wc -l)
CID=$(jnum "$R" courseId)
[ "$N" -ge 1 ] && ok "可选课程 $N 门" || no "没有可评价课程: $R"
R=$(post /v1/edu/eval "$STU" "{\"courseId\":$CID,\"scoreTeach\":5,\"scoreContent\":4,\"scoreAtmos\":5,\"content\":\"讲得很清楚\",\"anonymous\":true}")
echo "  响应: $R"
echo "$R" | grep -q '"code":200' && ok "评价提交成功" || no "提交失败: $R"
R=$(get "/v1/edu/eval/my" "$STU")
echo "$R" | grep -q "讲得很清楚" && ok "可查到我的评价" || no "查不到评价: $R"
R=$(get "/v1/edu/eval/options" "$STU")
echo "$R" | grep -q '"evaluated":true' && ok "列表显示已评价" || no "已评价状态没更新"

echo
echo "=== 10. 解除关联 ==="
R=$(post "/v1/relative/$LINK/unbind" "$PAR" '{}')
echo "  响应: $R"
[ "$(jnum "$R" status)" = "3" ] && ok "已解除关联" || no "解除失败"
R=$(get /v1/relative/children "$PAR")
echo "$R" | grep -q "2025309702" && no "解除后仍能看到孩子" || ok "解除后孩子列表为空"
R=$(get "/v1/publish/rank/portal?pageNum=1&pageSize=10" "$PAR")
T=$(jnum "$R" total)
[ "${T:-0}" = "0" ] && ok "解除后家长看不到成长记录" || no "解除后仍能看到: total=$T"

echo
echo "===== 结果：PASS $PASS / FAIL $FAIL ====="
[ "$FAIL" -eq 0 ] && echo "P3 冒烟通过。" || echo "有失败项，需修复。"

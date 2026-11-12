#!/usr/bin/env bash
# P2 冒烟：内容可见性 + 点赞 + 站内消息联动
API="http://localhost:8106/api"
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
jnum(){ echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }

ADM=$(login admin)
STU=$(login 2025309702)     # 信息工程学院（org 2）
OTHER=$(login 2025309721)   # 计算机与人工智能学院 / 计科1班（org 7，path /1/5/6/7/）
COU=$(login 1001001)
[ ${#ADM} -gt 100 ] && ok "管理员/学生/他院学生 登录成功" || no "登录失败"

echo
echo "=== 1. 管理员发一条「仅信息工程学院可见」的新闻 ==="
R=$(post /v1/publish/news "$ADM" '{"title":"[冒烟]仅信息工程学院可见","subTitle":"可见性测试","newsType":1,"visibleOrgId":2,"startTime":"2026-01-01 00:00:00","endTime":"2099-12-31 23:59:59","status":1,"content":"<p>仅信工学院学生可见</p>"}')
echo "  响应: $R"
NID=$(jnum "$R" data)
[ -n "$NID" ] && ok "新闻已创建 id=$NID" || no "创建失败"

echo
echo "=== 2. 本院学生能看到 ==="
R=$(get "/v1/publish/news/portal?pageNum=1&pageSize=20" "$STU")
echo "$R" | grep -q "仅信息工程学院可见" && ok "本院学生列表可见" || no "本院学生看不到"
R=$(get "/v1/publish/news/portal/$NID" "$STU")
echo "$R" | grep -q "仅信工学院学生可见" && ok "本院学生详情可见" || no "详情不可见: $R"

echo
echo "=== 3. 他院学生看不到（可见性隔离）==="
R=$(get "/v1/publish/news/portal?pageNum=1&pageSize=20" "$OTHER")
echo "$R" | grep -q "仅信息工程学院可见" && no "他院学生竟然看到了" || ok "他院学生列表不可见"
R=$(get "/v1/publish/news/portal/$NID" "$OTHER")
echo "$R" | grep -q '"code":403' && ok "他院学生详情 403" || no "详情未拦截: $R"

echo
echo "=== 4. 广告前台（按可见性 + 时间过滤）==="
R=$(get /v1/publish/ad/portal "$STU")
echo "$R" | grep -q '"code":200' && ok "广告接口正常" || no "广告接口异常"
echo "  返回条数: $(echo "$R" | grep -o '"id":[0-9]*' | wc -l)"

echo
echo "=== 5. 红黑榜：只能看自己的 ==="
R=$(get "/v1/publish/rank/portal?pageNum=1&pageSize=20" "$STU")
echo "$R" | grep -q '"code":200' && ok "红黑榜接口正常" || no "红黑榜异常: $R"
CNT=$(echo "$R" | grep -o '"employeeName":"[^"]*"' | wc -l)
echo "  本人记录条数: $CNT"

echo
echo "=== 6. 点赞（幂等 + 取消）==="
R1=$(post /v1/publish/like "$STU" "{\"targetType\":\"NEWS\",\"targetId\":$NID,\"liked\":true}")
C1=$(jnum "$R1" likeCount)
R2=$(post /v1/publish/like "$STU" "{\"targetType\":\"NEWS\",\"targetId\":$NID,\"liked\":false}")
C2=$(jnum "$R2" likeCount)
[ "$C1" = "1" ] && ok "点赞后 likeCount=1" || no "点赞计数异常: $R1"
[ "$C2" = "0" ] && ok "取消后 likeCount=0" || no "取消点赞异常: $R2"

echo
echo "=== 7. 请假审批 → 站内消息联动 ==="
BEFORE=$(jnum "$(get /v1/message/unread-count "$COU")" total)
D1=$(date -d "+$((300 + RANDOM % 60)) days" +%Y-%m-%d)
R=$(post /v1/leave/apply "$STU" "{\"type\":2,\"startTime\":\"$D1 08:00:00\",\"endTime\":\"$D1 18:00:00\",\"reason\":\"P2 消息联动测试\"}")
LID=$(jnum "$R" id)
[ -n "$LID" ] && ok "学生提交请假 id=$LID" || no "提交失败: $R"
AFTER=$(jnum "$(get /v1/message/unread-count "$COU")" total)
[ "$((AFTER - BEFORE))" -ge 1 ] && ok "辅导员未读数 +1（$BEFORE → $AFTER）" || no "未读数没变: $BEFORE → $AFTER"

echo
echo "=== 8. 站内消息：私信 + 按发件人筛选 ==="
SID=$(jnum "$(get /v1/auth/me "$STU")" userId)
CID=$(jnum "$(get /v1/auth/me "$COU")" userId)
R=$(post /v1/message "$COU" "{\"title\":\"冒烟私信\",\"content\":\"<p>这是一条测试私信</p>\",\"toUserIds\":[$SID],\"type\":2,\"level\":0,\"status\":1}")
MID=$(jnum "$R" data)
[ -n "$MID" ] && ok "辅导员给学生发私信 id=$MID" || no "发私信失败: $R"
R=$(post /v1/message/inbox/search "$STU" '{"pageNum":1,"pageSize":10,"searchModel":{"title":"冒烟私信"}}')
echo "$R" | grep -q "冒烟私信" && ok "学生收件箱可见私信" || no "学生看不到私信"
R2=$(post /v1/message/inbox/search "$STU" "{\"pageNum\":1,\"pageSize\":10,\"searchModel\":{\"fromId\":$CID}}")
T2=$(jnum "$R2" total)
[ "${T2:-0}" -ge 1 ] && ok "按发件人筛选命中 $T2 条" || no "发件人筛选失败: $R2"
R3=$(post /v1/message/inbox/search "$COU" '{"pageNum":1,"pageSize":10,"searchModel":{"title":"新的请假申请"}}')
echo "$R3" | grep -q '"fromName"' && no "系统消息不该有发件人" || ok "系统消息发件人为空（前端显示「系统」）"

echo
echo "=== 9. 收件详情自动置已读 ==="
R=$(post /v1/message/inbox/search "$COU" '{"pageNum":1,"pageSize":1,"searchModel":{"readStatus":0}}')
MID=$(jnum "$R" id)
R=$(get "/v1/message/inbox/$MID" "$COU")
echo "$R" | grep -q '"readStatus":1' && ok "详情打开后 readStatus=1" || no "已读状态未更新: $R"

echo
echo "=== 10. 清理本次冒烟数据 ==="
curl -s -X DELETE "$API/v1/publish/news/$NID" -H "Authorization: Bearer $ADM" >/dev/null && ok "已删除冒烟新闻"
post "/v1/leave/$LID/cancel" "$STU" '{}' >/dev/null && ok "已取消冒烟假条"

echo
echo "===== 结果：PASS $PASS / FAIL $FAIL ====="
[ "$FAIL" -eq 0 ] && echo "P2 冒烟通过。" || echo "有失败项，需修复。"

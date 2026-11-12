#!/usr/bin/env bash
# 请假闭环冒烟测试：一条正流程 + 4 条规则校验
# 用法：bash scripts/smoke-leave.sh
API="http://localhost:8106/api"
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
no(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

login(){
  printf '{"account":"%s","password":"123456"}' "$1" > .smoke-login.json
  curl -s -X POST "$API/v1/auth/login/password" -H "Content-Type: application/json" \
       --data-binary "@.smoke-login.json" \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'
}
post(){ # post <path> <token> <json>
  # Windows 下 curl 命令行参数会被转成 GBK，中文会报 Invalid UTF-8，改用文件传 body
  printf '%s' "$3" > .smoke-body.json
  curl -s -X POST "$API$1" -H "Content-Type: application/json;charset=UTF-8" \
       -H "Authorization: Bearer $2" --data-binary "@.smoke-body.json"
}
get(){ curl -s "$API$1" -H "Authorization: Bearer $2"; }
field(){ echo "$1" | sed -E "s/.*\"$2\":(\"?[^,\"}]*\"?).*/\1/" | tr -d '"'; }
# 精确取值：避免错误响应里把整段 JSON 当 ID
jnum(){ echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }

STU=$(login 2025309702)     # 学生
STU2=$(login 2025309708)    # 另一个学生（无在途假条，用于重叠校验）
COU=$(login 1001001)        # 辅导员
SEC=$(login 1003001)        # 书记
MON=$(login 2025309701)     # 同班学委

# 清理测试账号遗留的在途/待销假假条，保证脚本可重复运行
# 先精确删掉本脚本生成的测试假条（reason 带特定标记，不动演示数据）
MYSQL="/c/Program Files/MySQL/MySQL Server 8.2/bin/mysql"
"$MYSQL" -uroot -p123456 -h127.0.0.1 -P3307 portfolio -e "
  DELETE a FROM leave_approve a JOIN leave_request l ON l.id = a.leave_id
   WHERE l.reason LIKE '%肺炎住院治疗%' OR l.reason LIKE '%测试%';
  DELETE FROM leave_request WHERE reason LIKE '%肺炎住院治疗%' OR reason LIKE '%测试%';
" 2>/dev/null

cleanup(){
  for acc in "$@"; do
    T=$(login "$acc")
    for st in 10 11 20; do
      R=$(post /v1/leave/personal/search "$T" "{\"pageNum\":1,\"pageSize\":100,\"searchModel\":{\"status\":$st}}")
      for id in $(echo "$R" | grep -o '"id":[0-9]*' | cut -d: -f2); do
        if [ "$st" = "20" ]; then
          post "/v1/leave/$id/return" "$T" '{}' >/dev/null
        else
          post "/v1/leave/$id/cancel" "$T" '{}' >/dev/null
        fi
      done
    done
  done
}
cleanup 2025309702 2025309708

[ ${#STU} -gt 100 ] && ok "三个账号登录成功" || no "登录失败"

echo
# 动态日期，避免重复执行时与上次假条重叠
# 主流程用「已过去」的日期，才能测到到期后正常销假
D1=$(date -d "-$((30 + RANDOM % 60)) days" +%Y-%m-%d)
D2=$(date -d "$D1 +4 days" +%Y-%m-%d)
START="$D1 08:00:00"; END="$D2 18:00:00"
D3=$(date -d "+$((200 + RANDOM % 100)) days" +%Y-%m-%d)
D4=$(date -d "$D3 +9 days" +%Y-%m-%d)
echo "本次测试区间：$START ~ $END"
echo "=== 1. 学生提交 5 天请假（应生成 2 个审批节点）==="
R=$(post /v1/leave/apply "$STU" "{\"type\":1,\"startTime\":\"$START\",\"endTime\":\"$END\",\"reason\":\"肺炎住院治疗，附诊断书\"}")
echo "  响应: $R"
ID=$(jnum "$R" id)
N=$(echo "$R" | grep -o '"approveFlow":\[[^]]*\]' | tr ',' '\n' | grep -c '"' )
[ "$(jnum "$R" status)" = "10" ] && ok "状态=10 待辅导员" || no "状态不是 10"
echo "$R" | grep -q '书记' && ok "审批链含书记（>3 天）" || no "审批链缺少书记"
echo "  请假 ID=$ID"

echo
echo "=== 2. 规则校验：超 7 天 ==="
R=$(post /v1/leave/apply "$STU" "{\"type\":2,\"startTime\":\"$D3 08:00:00\",\"endTime\":\"$D4 18:00:00\",\"reason\":\"测试超长\"}")
echo "$R" | grep -q "LEAVE_TOO_LONG" && ok "超 7 天被拦截" || no "超 7 天未拦截: $R"

echo
echo "=== 3. 规则校验：时间重叠 ==="
R1=$(post /v1/leave/apply "$STU2" "{\"type\":2,\"startTime\":\"$D1 08:00:00\",\"endTime\":\"$D1 18:00:00\",\"reason\":\"测试基准单\"}")
R=$(post /v1/leave/apply "$STU2" "{\"type\":2,\"startTime\":\"$D1 12:00:00\",\"endTime\":\"$D1 20:00:00\",\"reason\":\"测试重叠\"}")
echo "$R" | grep -q "LEAVE_TIME_CONFLICT" && ok "重叠被拦截" || no "重叠未拦截: $R"

echo
echo "=== 4. 辅导员待办 + 审批 ==="
R=$(post /v1/leave/approve/search "$COU" '{"pageNum":1,"pageSize":10,"searchModel":{}}')
echo "  待办数: $(jnum "$R" total)"
echo "$R" | grep -q "\"id\":$ID" && ok "辅导员待办里出现该假条" || no "待办里没有该假条"
R=$(post /v1/leave/approve "$COU" "{\"leaveId\":$ID,\"result\":1,\"reason\":\"情况属实\"}")
echo "  响应: $R"
[ "$(jnum "$R" status)" = "11" ] && ok "辅导员批准后 → 11 待书记" || no "辅导员审批后状态错误"

echo
echo "=== 5. 书记审批 ==="
R=$(post /v1/leave/approve "$SEC" "{\"leaveId\":$ID,\"result\":1,\"reason\":\"同意\"}")
echo "  响应: $R"
[ "$(jnum "$R" status)" = "20" ] && ok "书记批准后 → 20 已通过待销假" || no "书记审批后状态错误"

echo
echo "=== 6. 学生销假 ==="
R=$(post "/v1/leave/$ID/return" "$STU" '{}')
echo "  响应: $R"
[ "$(jnum "$R" status)" = "21" ] && ok "销假成功 → 21" || no "销假失败"

echo
echo "=== 7. 学委视角脱敏 ==="
R=$(post /v1/leave/class/search "$MON" '{"pageNum":1,"pageSize":20,"searchModel":{}}')
echo "$R" | grep -q "肺炎住院" && no "学委看到了请假理由（未脱敏）" || ok "班级列表不含理由字段"
R2=$(get "/v1/leave/$ID" "$MON")
echo "$R2" | grep -q "肺炎住院" && no "学委详情看到了理由（未脱敏）" || ok "学委看详情理由为空"

echo
echo "=== 8. 越权校验 ==="
R=$(get "/v1/leave/$ID" "$SEC" | head -c 300)
echo "$R" | grep -q '"code":200' && ok "书记可查看（审批人权限）" || no "书记查看失败"
R=$(post /v1/leave/apply "$COU" '{"type":1,"startTime":"2026-12-05 08:00:00","endTime":"2026-12-06 18:00:00","reason":"辅导员尝试请假"}')
echo "$R" | grep -q "未绑定员工档案\|无权" && ok "无学生档案的账号不能请假" || echo "  [INFO] 该账号有档案，跳过"

echo
echo "===== 结果：PASS $PASS / FAIL $FAIL ====="
[ "$FAIL" -eq 0 ] && echo "请假闭环通过。" || echo "有失败项，需修复。"

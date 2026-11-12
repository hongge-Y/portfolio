#!/usr/bin/env bash
# P4 冒烟：奖学金 计划 → 申请 → 三级审批 → 排名 → 公示 → 发放
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
jnum(){ echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }

# 重置：清掉测试学生的申请，并把计划恢复到报名中
"$MYSQL" -uroot -p123456 -h127.0.0.1 -P3307 portfolio -e "
DELETE s FROM sch_apply s JOIN org_employee e ON e.id = s.employee_id WHERE e.employee_no = '2025309702';
DELETE FROM sch_approve WHERE apply_id NOT IN (SELECT id FROM sch_apply);
UPDATE sch_plan SET status = 1 WHERE name LIKE '%国家励志奖学金%';
DELETE FROM sch_publicity;
" 2>/dev/null

STU=$(login 2025309702)
COU=$(login 1001001)
SEC=$(login 1003001)
ADM=$(login admin)
[ ${#STU} -gt 100 ] && ok "学生 / 辅导员 / 书记 / 管理员 登录成功" || no "登录失败"

echo
echo "=== 1. 学生看到可申请的计划 ==="
R=$(get /v1/scholarship/plan/available "$STU")
echo "  $R" | head -c 300; echo
PLAN=$(jnum "$R" id)
echo "$R" | grep -q '"canApply":true' && ok "计划在报名期，可申请 id=$PLAN" || no "计划不可申请"

echo
echo "=== 2. 学生提交申请（GPA 3.6，含红黑榜加减分）==="
R=$(post /v1/scholarship/apply "$STU" "{\"planId\":$PLAN,\"gpa\":3.6,\"statement\":\"本学年成绩稳定，参加校赛获奖\",\"materials\":[\"/static/mat/1.pdf\"]}")
echo "  响应: $R"
AID=$(jnum "$R" id)
[ "$(jnum "$R" status)" = "10" ] && ok "申请已提交，状态待辅导员" || no "提交失败"
TOTAL=$(echo "$R" | sed -E 's/.*"totalScore":([0-9.]+).*/\1/')
echo "  计分：gpa*6 + 红榜*2 - 黑榜*3 = $TOTAL"

echo
echo "=== 3. 重复申请应被拒 ==="
R=$(post /v1/scholarship/apply "$STU" "{\"planId\":$PLAN,\"gpa\":3.6}")
echo "$R" | grep -q "已经提交过" && ok "重复申请被拦截" || no "未拦截: $R"

echo
echo "=== 4. 越级审批（书记直接审批第 1 级）应被拒 ==="
R=$(post /v1/scholarship/approve "$SEC" "{\"applyId\":$AID,\"result\":1}")
echo "$R" | grep -q '"code":403' && ok "书记不能替辅导员审批" || no "越级未被拦截: $R"

echo
echo "=== 5. 三级审批 ==="
R=$(post /v1/scholarship/approve/search "$COU" '{"pageNum":1,"pageSize":10}')
echo "$R" | grep -q "\"id\":$AID" && ok "辅导员待办里出现该申请" || no "辅导员待办没有: $R"
R=$(post /v1/scholarship/approve "$COU" "{\"applyId\":$AID,\"result\":1}")
[ "$(jnum "$R" status)" = "20" ] && ok "辅导员通过 → 20 待院系" || no "第 1 级失败: $R"
R=$(post /v1/scholarship/approve "$SEC" "{\"applyId\":$AID,\"result\":1}")
[ "$(jnum "$R" status)" = "30" ] && ok "院系通过 → 30 待学生处" || no "第 2 级失败: $R"
R=$(post /v1/scholarship/approve "$ADM" "{\"applyId\":$AID,\"result\":1}")
[ "$(jnum "$R" status)" = "40" ] && ok "学生处通过 → 40 审批通过" || no "第 3 级失败: $R"

echo
echo "=== 6. 生成排名 ==="
R=$(post "/v1/scholarship/plan/$PLAN/rank" "$ADM" '{}')
echo "$R" | head -c 320; echo
echo "$R" | grep -q '"rankNo":1' && ok "排名已生成" || no "排名失败: $R"

echo
echo "=== 7. 发起公示 ==="
R=$(post "/v1/scholarship/plan/$PLAN/publicity" "$ADM" '{}')
echo "$R" | head -c 320; echo
echo "$R" | grep -q "2025309702" && ok "公示名单含该学生" || no "公示名单异常: $R"
R=$(get "/v1/scholarship/publicity?planId=$PLAN" "$STU")
echo "$R" | grep -q "2025309702" && ok "公示接口可查" || no "公示接口异常"

echo
echo "=== 8. 公示后学生状态 ==="
R=$(post /v1/scholarship/apply/my "$STU" '{"pageNum":1,"pageSize":5}')
echo "$R" | grep -q '"status":60' && ok "申请进入公示状态 60" || no "状态错误: $R"

echo
echo "=== 9. 发放 ==="
R=$(post "/v1/scholarship/plan/$PLAN/grant" "$ADM" '{}')
echo "  $R"
echo "$R" | grep -q '"granted":1' && ok "发放 1 人" || no "发放失败: $R"
R=$(post /v1/scholarship/apply/my "$STU" '{"pageNum":1,"pageSize":5}')
echo "$R" | grep -q '"status":70' && ok "申请状态 → 70 已发放" || no "发放状态错误"

echo
echo "=== 10. 计划结束后不能再申请 ==="
R=$(post /v1/scholarship/apply "$STU" "{\"planId\":$PLAN,\"gpa\":3.6}")
echo "$R" | grep -qE "SCH_PLAN_CLOSED|已经提交过" && ok "已结束计划拒绝申请" || no "未拦截: $R"

echo
echo "===== 结果：PASS $PASS / FAIL $FAIL ====="
[ "$FAIL" -eq 0 ] && echo "P4 冒烟通过。" || echo "有失败项，需修复。"

#!/usr/bin/env bash
# 权限 / 越权回归：角色边界 + orgPath 行级数据范围
# 用法：bash scripts/test-permissions.sh        （后端需在跑）
# 依赖演示数据：class 4 = 软件工程2025级1班（/1/2/3/4/），class 7 = 计算机2025级1班（/1/5/6/7/）
set -u
BASE="${BASE:-http://localhost:8106/api}"
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf "  ✅ %s\n" "$1"; }
ng()   { FAIL=$((FAIL+1)); printf "  ❌ %s  （实际：%s）\n" "$1" "$2"; }
check(){ local name="$1" want="$2" got="$3"; [ "$want" = "$got" ] && ok "$name" || ng "$name" "$got"; }
body() { printf '%s' "$2" > .perm.json; curl -s -m 20 -X "$1" "$BASE$3" -H "Content-Type: application/json; charset=utf-8" ${4:+-H "Authorization: Bearer $4"} --data-binary @.perm.json; }
get()  { curl -s -m 20 "$BASE$1" ${2:+-H "Authorization: Bearer $2"}; }
code() { python -c "import sys,json;print(json.load(sys.stdin).get('code'))"; }
field(){ python -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }

login() {
  printf '{"account":"%s","password":"123456"}' "$1" > .perm_login.json
  curl -s -m 20 -X POST "$BASE/v1/auth/login/password" -H "Content-Type: application/json" \
    --data-binary @.perm_login.json | field "['data']['accessToken']"
}

STU=$(login 2025309701)      # 学生01，class 4，兼学委
STU4=$(login 2025309704)     # 学生04，class 4，纯学生
STU7=$(login 2025309721)     # 学生21，class 7（外院），兼学委
COU=$(login 1001001)         # 辅导员王老师，/1/2/3/4/
COU5=$(login 1002001)        # 辅导员李老师，/1/5/
SEC=$(login 1003001)         # 学院书记陈老师，/1/2/
ADM=$(login admin)           # 管理员，全校
PAR=$(login 13900001111)     # 家长
LX=$(login 2025000001)       # 林同学，class 4，名下有奖学金申请（第三节用）
for t in "$STU" "$COU" "$SEC" "$ADM"; do
  [ -z "$t" ] && { echo "登录失败，先确认后端已启动且演示账号可用"; exit 1; }
done

echo "== 一、未登录 =="
check "无 token 查班级请假应 401" 401 "$(body POST '{"pageNum":1,"pageSize":5}' /v1/leave/class/search | code)"
check "无 token 查后台统计应 401" 401 "$(get /v1/admin/stats | code)"

echo "== 二、角色边界 =="
check "纯学生查班级请假应 403" 403 "$(body POST '{"pageNum":1,"pageSize":5}' /v1/leave/class/search "$STU4" | code)"
check "纯学生查后台统计应 403" 403 "$(get /v1/admin/stats "$STU4" | code)"
check "学委查本班应 200" 200 "$(body POST '{"pageNum":1,"pageSize":20,"searchModel":{"status":10}}' /v1/leave/class/search "$STU" | code)"
check "辅导员查本班应 200" 200 "$(body POST '{"pageNum":1,"pageSize":20,"searchModel":{"status":10}}' /v1/leave/class/search "$COU" | code)"
check "管理员查全校应 200（修复前是 403 未归属班级）" 200 "$(body POST '{"pageNum":1,"pageSize":20,"searchModel":{"status":10}}' /v1/leave/class/search "$ADM" | code)"

# 外院基线：脚本自己造一张 class 7 的待审假条，不依赖历史数据（reset-demo 会把旧测试单清掉）
D_BASE=$(date -d "+45 days" +%Y-%m-%d 2>/dev/null || date -v+45d +%Y-%m-%d)
# 时间段随机：固定时刻会和上一轮残留单撞"时间重叠"规则，导致脚本自己造不出来
H=$(( (RANDOM % 10) + 7 ))
printf '{"type":2,"startTime":"%s %02d:00:00","endTime":"%s %02d:00:00","reason":"权限基线：外院假条（脚本自动创建）"}' \
  "$D_BASE" "$H" "$D_BASE" "$((H + 2))" > .perm_base.json
BASE_RESP=$(curl -s -X POST "$BASE/v1/leave/apply" -H "Content-Type: application/json;charset=UTF-8" \
                 -H "Authorization: Bearer $STU7" --data-binary @.perm_base.json)
BASE_ID=$(echo "$BASE_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ -n "$BASE_ID" ] && echo "  · 已造外院基线假条 id=$BASE_ID（class 7）"

echo "== 三、orgPath 行级范围（书记 = 本学院 / 管理员 = 全校）=="
SEC_TOTAL=$(body POST '{"pageNum":1,"pageSize":50,"searchModel":{"status":10}}' /v1/leave/class/search "$SEC" | field "['data']['total']")
ADM_TOTAL=$(body POST '{"pageNum":1,"pageSize":50,"searchModel":{"status":10}}' /v1/leave/class/search "$ADM" | field "['data']['total']")
SEC_HAS7=$(body POST '{"pageNum":1,"pageSize":50,"searchModel":{"status":10}}' /v1/leave/class/search "$SEC" | grep -c '"classOrgId":7' || true)
ADM_HAS7=$(body POST '{"pageNum":1,"pageSize":50,"searchModel":{"status":10}}' /v1/leave/class/search "$ADM" | grep -c '"classOrgId":7' || true)
printf "  · 书记可见 %s 条 / 管理员可见 %s 条\n" "${SEC_TOTAL:-?}" "${ADM_TOTAL:-?}"
check "管理员能看到外院(class 7)记录" 1 "$([ "${ADM_HAS7:-0}" -ge 1 ] && echo 1 || echo 0)"
check "书记看不到外院(class 7)记录" 0 "$([ "${SEC_HAS7:-0}" -eq 0 ] && echo 0 || echo 1)"
check "辅导员查外院列表应为空（total=0）" 0 "$(body POST '{"pageNum":1,"pageSize":5}' /v1/leave/class/search "$COU5" | field "['data']['total']")"

echo "== 四、详情越权（按 id 翻别院学生的理由与附件）=="
LEAVE4=$(body POST '{"pageNum":1,"pageSize":1,"searchModel":{"status":10}}' /v1/leave/class/search "$COU" | field "['data']['list'][0]['id']")
echo "  · 用 class 4 的假条 id=$LEAVE4 做基线"
check "本班辅导员看本班详情应 200" 200 "$(get "/v1/leave/$LEAVE4" "$COU" | code)"
check "本班学委看本班详情应 200" 200 "$(get "/v1/leave/$LEAVE4" "$STU" | code)"
check "外院辅导员看别班详情应 403（修复前是 200）" 403 "$(get "/v1/leave/$LEAVE4" "$COU5" | code)"
check "书记看本院详情应 200" 200 "$(get "/v1/leave/$LEAVE4" "$SEC" | code)"
check "管理员看任意详情应 200" 200 "$(get "/v1/leave/$LEAVE4" "$ADM" | code)"
check "同班普通学生看他人详情应 403" 403 "$(get "/v1/leave/$LEAVE4" "$STU4" | code)"

echo "== 五、家长只看自己孩子 =="
check "家长查孩子列表应 200" 200 "$(get /v1/relative/children "$PAR" | code)"
check "家长看无关假条详情应 403" 403 "$(get "/v1/leave/$LEAVE4" "$PAR" | code)"

echo "== 六、奖学金详情同样按 orgPath 收口 =="
# 申请 id 不能写死：冒烟脚本会清理自己造的申请，写死必然 404
APPLY_ID=$(body POST '{"pageNum":1,"pageSize":1}' /v1/scholarship/apply/my "$LX" | field "['data']['list'][0]['id']")
if [ -n "${APPLY_ID:-}" ]; then
  echo "  · 用 class 4 的奖学金申请 id=$APPLY_ID 做基线"
  check "本班辅导员看本班奖学金申请应 200" 200 "$(get "/v1/scholarship/apply/$APPLY_ID" "$COU" | code)"
  check "外院辅导员看别班奖学金申请应 403（修复前是 200）" 403 "$(get "/v1/scholarship/apply/$APPLY_ID" "$COU5" | code)"
  check "书记看本院奖学金申请应 200" 200 "$(get "/v1/scholarship/apply/$APPLY_ID" "$SEC" | code)"
else
  echo "  ⚠ 演示账号名下没有奖学金申请，跳过本节（先跑 smoke-scholarship.sh 造一单）"
fi

# 撤掉脚本自己造的外院基线（不删数据，留痕给 reset-demo 统一清理）
if [ -n "${BASE_ID:-}" ]; then
  printf '{}' > .perm_base.json
  curl -s -X POST "$BASE/v1/leave/$BASE_ID/cancel" -H "Content-Type: application/json" \
       -H "Authorization: Bearer $STU7" --data-binary @.perm_base.json >/dev/null 2>&1
  echo "  · 已撤销外院基线假条 id=$BASE_ID"
fi

echo "------------------------------------------------"
echo "结果：PASS $PASS / FAIL $FAIL"
[ "$FAIL" -eq 0 ] || exit 1

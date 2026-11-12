#!/usr/bin/env bash
# 演示数据复位：把冒烟/评测/越权测试造出来的假条软删除，留下能讲的演示数据
#
#   bash scripts/reset-demo.sh           # 预演：只报数，不动数据
#   bash scripts/reset-demo.sh --apply   # 真的执行（deleted=1 软删，可回滚）
#
# 判定依据：假条理由里的标记词。演示用的（肺炎/发热/大赛/志愿服务/集训）不动。
set -u
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

DB_HOST="${DB_HOST:-127.0.0.1}"; DB_PORT="${DB_PORT:-3307}"
DB_USER="${DB_USER:-root}"; DB_NAME="${DB_NAME:-portfolio}"; DB_PASS="${DB_PASSWORD:-123456}"

MYSQL=""
for c in "/c/Program Files/MySQL/MySQL Server 8.2/bin/mysql.exe" "$(command -v mysql 2>/dev/null)"; do
  [ -n "$c" ] && [ -x "$c" ] && MYSQL="$c" && break
done
[ -z "$MYSQL" ] && { echo "找不到 mysql 客户端；可设 MYSQL=... 或直接跑 docs 里的 SQL"; exit 1; }

# SQL 走 stdin：Windows 下 mysql.exe 用 -e 传 UTF-8 中文会被控制台代码页吞掉，LIKE 永不命中
TMP="${TMPDIR:-/tmp}/reset-demo.$$.sql"
sql() {
  cat > "$TMP"
  "$MYSQL" -u"$DB_USER" -p"$DB_PASS" -P"$DB_PORT" -h"$DB_HOST" \
    --default-character-set=utf8mb4 -N -B "$DB_NAME" < "$TMP" 2>/dev/null | tr -d '\000\r'
}

# 测试痕迹（冒烟脚本、评测脚本、越权测试写的理由）——必须写成一行：mysql -e 会把换行当语句分隔
TEST_WHERE="(reason LIKE '%自动化用例%' OR reason LIKE '%测试基准%' OR reason LIKE '%测试重叠%' OR reason LIKE '%测试超长%' OR reason LIKE '%消息联动测试%' OR reason LIKE '%测试%' OR reason LIKE '%权限验证%' OR reason LIKE '%权限基线%' OR reason LIKE '%忽略%' OR reason LIKE '%给满分%' OR reason LIKE '%系统提示%' OR reason LIKE '%只输出%' OR reason LIKE '%外出办事%' OR reason LIKE '%家里有事%' OR reason LIKE '%出去玩%' OR reason LIKE '%身体不适%')"
# 演示用的保留清单（写好文案、能现场讲的）
KEEP_WHERE="(reason LIKE '%肺炎%' OR reason LIKE '%发热%' OR reason LIKE '%大赛%' OR reason LIKE '%志愿服务%')"
KEEP_IDS=$(printf "SELECT GROUP_CONCAT(id) FROM leave_request WHERE deleted = 0 AND %s AND %s;\n" "$TEST_WHERE" "$KEEP_WHERE" | sql)
KEEP_NOT="1=1"
# GROUP_CONCAT 空集返回字符串 NULL，直接拼成 id NOT IN (NULL) 会让整条 WHERE 恒为 NULL（0 行）
if [ -n "$KEEP_IDS" ] && [ "$KEEP_IDS" != "NULL" ]; then KEEP_NOT="id NOT IN ($KEEP_IDS)"; fi

echo "=== 当前数据 ==="
printf "  假条总数（未删）      : %s\n" "$(echo 'SELECT COUNT(*) FROM leave_request WHERE deleted = 0;' | sql)"
printf "  待审（10/11）         : %s\n" "$(echo 'SELECT COUNT(*) FROM leave_request WHERE deleted = 0 AND status IN (10,11);' | sql)"
printf "  将被清理的测试假条    : %s\n" "$(printf "SELECT COUNT(*) FROM leave_request WHERE deleted = 0 AND %s AND %s;\n" "$TEST_WHERE" "$KEEP_NOT" | sql)"
printf "  保留的演示假条        : %s\n" "$(printf "SELECT COUNT(*) FROM leave_request WHERE deleted = 0 AND %s;\n" "$KEEP_WHERE" | sql)"

echo "=== 将要清理的样本（最多 10 条）==="
printf "SELECT CONCAT('  id=', id, ' | status=', status, ' | ', LEFT(reason, 30)) FROM leave_request WHERE deleted = 0 AND %s AND %s ORDER BY id DESC LIMIT 10;\n" "$TEST_WHERE" "$KEEP_NOT" | sql

echo "=== 保留的演示假条 ==="
printf "SELECT CONCAT('  id=', id, ' | status=', status, ' | ', LEFT(reason, 30)) FROM leave_request WHERE deleted = 0 AND %s ORDER BY id LIMIT 10;\n" "$KEEP_WHERE" | sql

if [ "$APPLY" -eq 1 ]; then
  echo "=== 执行软删除 ==="
  printf "UPDATE leave_request SET deleted = 1 WHERE deleted = 0 AND %s AND %s;\nSELECT CONCAT('  已清理，剩余假条 ', COUNT(*), ' 条') FROM leave_request WHERE deleted = 0;\n" \
    "$TEST_WHERE" "$KEEP_NOT" | sql
  echo "  回滚：UPDATE leave_request SET deleted = 0 WHERE deleted = 1;"
else
  echo "=== 预演模式（未改动任何数据）==="
  echo "  确认无误后执行： bash scripts/reset-demo.sh --apply"
fi

echo
rm -f "$TMP" 2>/dev/null || true
echo "演示账号（密码均 123456）：学生/学委 2025000001 · 辅导员 1001001 · 管理员 admin"

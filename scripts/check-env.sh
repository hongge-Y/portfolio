#!/usr/bin/env bash
# 本机环境体检：每项输出 PASS / FAIL / WARN，用于开工前自检
# 用法：bash scripts/check-env.sh
# 说明：优先读环境变量（JAVA_HOME / MAVEN_HOME / DB_PASSWORD），找不到再回退常见安装路径。

pass=0; fail=0; warn=0
ok()   { echo "  [PASS] $1"; pass=$((pass+1)); }
no()   { echo "  [FAIL] $1"; fail=$((fail+1)); }
warn_(){ echo "  [WARN] $1"; warn=$((warn+1)); }
# Windows 盘符路径转 git-bash 可执行形式
sh_path() { case "$1" in [A-Za-z]:*) printf '/%s/%s' "$(echo "$1" | cut -c1 | tr 'A-Z' 'a-z')" "$(echo "$1" | cut -c3- | tr '\\' '/')" ;; *) printf '%s' "$1" ;; esac; }

echo "===== Portfolio 本机环境体检 ====="
echo

echo "1) JDK 17"
# 目标：找到任意一个 17.x 的 java；优先 JAVA_HOME，其次常见安装位置，最后才用 PATH 上的 java
is17() { "$1" -version 2>&1 | head -1 | grep -q 'version "17\.'; }
JAVA_BIN=""; JAVA_ANY=""
for c in "$(sh_path "${JAVA_HOME:-}")/bin/java" "$(sh_path "${JAVA_HOME:-}")/bin/java.exe" \
         /d/JAVA/JDK/bin/java.exe "/c/Program Files/Java"/*17*/bin/java.exe \
         "$(command -v java 2>/dev/null)"; do
  [ -n "$c" ] && [ -x "$c" ] || continue
  [ -z "$JAVA_ANY" ] && JAVA_ANY="$c"
  if is17 "$c"; then JAVA_BIN="$c"; break; fi
done
if [ -n "$JAVA_BIN" ]; then
  ok "JDK 17 = $JAVA_BIN → $("$JAVA_BIN" -version 2>&1 | head -1)"
elif [ -n "$JAVA_ANY" ]; then
  no "没找到 JDK 17；当前 java = $JAVA_ANY（$("$JAVA_ANY" -version 2>&1 | head -1)）。本项目编译目标 17，请安装 JDK 17 并把 JAVA_HOME 指过去"
else
  no "找不到 java，请安装 JDK 17 并设置 JAVA_HOME"
fi
echo "   JAVA_HOME 当前 = ${JAVA_HOME:-<未设置>}"
case "${JAVA_HOME:-}" in
  *java1.8*|*jdk1.8*|*java-8*) warn_ "JAVA_HOME 指向 JDK 8，构建前需覆盖为 JDK 17" ;;
esac
echo

echo "2) Maven"
# Windows/git-bash 下必须调 mvn.cmd：直接跑 mvn 脚本会 classworlds ClassNotFound
MVN_BIN=""
for c in "$(command -v mvn.cmd 2>/dev/null)" "$(command -v mvn 2>/dev/null)" \
         "$(sh_path "${MAVEN_HOME:-}")/bin/mvn.cmd" "$(sh_path "${MAVEN_HOME:-}")/bin/mvn" \
         /d/SpringCloud/apache-maven-*/bin/mvn.cmd "/d/JAVA/idea/IntelliJ IDEA"*/plugins/maven/lib/maven3/bin/mvn.cmd \
         /d/SpringCloud/apache-maven-*/bin/mvn "/d/JAVA/idea/IntelliJ IDEA"*/plugins/maven/lib/maven3/bin/mvn; do
  [ -n "$c" ] && [ -f "$c" ] && MVN_BIN="$c" && break
done
if [ -n "$MVN_BIN" ]; then
  ok "找到 Maven：$MVN_BIN"
  v=$("$MVN_BIN" -v 2>/dev/null | head -3)
  if [ -z "$v" ]; then
    no "Maven 跑不起来（git-bash 里请确认调用的是 mvn.cmd）"
  else
    echo "$v" | sed 's/^/       /'
    if echo "$v" | grep -q "Java version: 17"; then ok "mvn 使用 JDK 17"
    else warn_ "mvn 未使用 JDK 17，构建前把 JAVA_HOME 指到 JDK 17"; fi
  fi
else
  no "找不到 mvn，请安装 Maven 或设置 MAVEN_HOME"
fi
echo

echo "3) Node / npm"
if command -v node >/dev/null 2>&1; then
  v=$(node -v); mj=${v#v}; mj=${mj%%.*}
  if [ "$mj" -ge 18 ] 2>/dev/null; then ok "node $v"; else no "node $v 过低，需 ≥18"; fi
  command -v npm >/dev/null 2>&1 && ok "npm $(npm -v)" || no "npm 不可用"
else
  no "node 未安装"
fi
echo

echo "4) MySQL 8.x（默认 3307）"
DBP="${DB_PASSWORD:-123456}"
MYSQL=""
for c in "$(command -v mysql 2>/dev/null)" "/c/Program Files/MySQL/MySQL Server"*/bin/mysql /usr/bin/mysql /usr/local/bin/mysql; do
  [ -n "$c" ] && [ -x "$c" ] && MYSQL="$c" && break
done
if [ -n "$MYSQL" ]; then
  ver=$("$MYSQL" -uroot -p"$DBP" -h127.0.0.1 -P3307 -N -e "select version();" 2>/dev/null)
  if [ -n "$ver" ]; then
    case "$ver" in
      8.*) ok "3307 运行中，版本 $ver" ;;
      *)   no "3307 版本为 $ver，需 MySQL 8.x" ;;
    esac
    t=$("$MYSQL" -uroot -p"$DBP" -h127.0.0.1 -P3307 -N -e "select count(*) from information_schema.tables where table_schema='portfolio';" 2>/dev/null)
    if [ "${t:-0}" -ge 40 ]; then ok "库 portfolio 已建，共 $t 张表"; else warn_ "库 portfolio 表数量为 ${t:-0}，请执行 docs/03a-schema.sql"; fi
  else
    no "3307 连不上（root / 口令取 DB_PASSWORD，默认 123456）"
  fi
else
  no "找不到 mysql 客户端"
fi
echo "   提示：3306 若为 MariaDB 5.5 不可用于本项目"
echo

echo "5) 端口占用（5173 前端 / 8106 后端；演示进行中被占用属正常）"
for p in 5173 8106; do
  if (exec 3<>/dev/tcp/127.0.0.1/$p) 2>/dev/null; then
    exec 3<&- 2>/dev/null; no "$p 已被占用"
  else
    ok "$p 空闲"
  fi
done
echo

echo "6) Redis（可选）"
if command -v redis-cli >/dev/null 2>&1 && redis-cli -p 6379 ping 2>/dev/null | grep -q PONG; then
  ok "Redis 可用（生产 profile 会用到）"
else
  warn_ "Redis 未运行 —— 开发态用 Caffeine，不影响"
fi
echo

echo "7) 项目文件"
for f in docs/03a-schema.sql docs/04-接口规范.md; do
  [ -f "$f" ] && ok "$f 存在" || warn_ "$f 缺失（只影响建库与接口文档查阅）"
done
echo

echo "===== 结果：PASS $pass / FAIL $fail / WARN $warn ====="
[ "$fail" -eq 0 ] && echo "可以开工。" || echo "请先处理 FAIL 项。"

#!/usr/bin/env bash
# 一键启动（Linux / macOS / WSL）；Windows 用 scripts\start-all.bat
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)

say() { printf '%s\n' "$*"; }
port_up() { (exec 3<>/dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1 && exec 3<&- && return 0 || return 1; }

say "============================================"
say "  Campus Platform - one click start"
say "============================================"

port_up 3307 || { say "[WARN] MySQL 3307 未监听，请先启动 MySQL 8"; exit 1; }
say "[ OK ] MySQL 3307 up"

: "${DB_PASSWORD:=123456}"
export DB_PASSWORD

if ! port_up 8106; then
  if [ -z "${JAVA_HOME:-}" ]; then
    say "[WARN] JAVA_HOME 未设置，需要 JDK 17"; exit 1
  fi
  say "[ .. ] 启动后端"
  JAR="server/portfolio-server/target/portfolio-server-1.0.0.jar"
  if [ ! -f "$JAR" ] || [ "$(wc -c <"$JAR")" -lt 5000000 ]; then
    ( cd server && mvn -q -DskipTests package ) || { say "[ERR] Maven 构建失败"; exit 1; }
  fi
  ( "$JAVA_HOME/bin/java" -Duser.timezone=Asia/Shanghai -jar "$JAR" >"$ROOT/backend.log" 2>&1 & )
else
  say "[SKIP] 后端已在 8106 运行"
fi

if ! port_up 5173; then
  say "[ .. ] 启动前端"
  ( cd web && [ -d node_modules ] || npm install ) || { say "[ERR] npm install 失败"; exit 1; }
  ( cd web && npm run dev -- --port 5173 --strictPort >"$ROOT/frontend.log" 2>&1 & )
else
  say "[SKIP] 前端已在 5173 运行"
fi

printf '[wait] 等待后端 '
for _ in $(seq 1 60); do
  port_up 8106 && break
  printf '.'
  sleep 2
done
say ""

if port_up 8106; then
  say "[ OK ] 后端 http://localhost:8106/api/health"
else
  say "[WARN] 后端未就绪，看 backend.log"
fi
if port_up 5173; then
  say "[ OK ] 前端 http://localhost:5173"
else
  say "[WARN] 前端未就绪，看 frontend.log"
fi

say ""
say "演示账号（密码 123456）：2025000001 / 1001001 / 1003001 / 13900001111 / admin"
say "日志：backend.log / frontend.log"

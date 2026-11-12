#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""AI 审批意见评测（stdlib only）

用法：
  python scripts/eval_ai_advice.py                 # 按 eval-ai-advice.csv 里的 leaveId 重跑那 10 条
  python scripts/eval_ai_advice.py --ids 91,92,93  # 指定若干 leaveId
  python scripts/eval_ai_advice.py --base http://localhost:8106/api --account 1001001

输出：表格 + 追加写入 eval-ai-advice.csv；用于 docs/14-AI意见评测.md 的复现。
"""
import argparse, csv, json, sys, time, urllib.request, urllib.error, os

def call(base, path, body=None, token=None, timeout=60):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, method="POST" if data else "GET")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"code": e.code, "message": "http error"}
    except Exception as e:
        return {"code": -1, "message": str(e)}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8106/api")
    ap.add_argument("--account", default="1001001")
    ap.add_argument("--ids", default="")
    ap.add_argument("--csv", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "eval-ai-advice.csv"))
    a = ap.parse_args()

    ids = []
    if a.ids:
        ids = [int(x) for x in a.ids.split(",") if x.strip().isdigit()]
    elif os.path.exists(a.csv):
        with open(a.csv, encoding="utf-8-sig") as f:
            for row in csv.DictReader(f):
                if str(row.get("leaveId", "")).isdigit():
                    ids.append((row.get("用例", ""), int(row["leaveId"])))
    if not ids:
        print("没有可评测的 leaveId，用 --ids 指定")
        return 1

    r = call(a.base, "/v1/auth/login/password", {"account": a.account, "password": "123456"})
    token = r.get("data", {}).get("accessToken")
    if not token:
        print("登录失败：", r.get("message"))
        return 1

    rows, ok, degraded = [], 0, 0
    for item in ids:
        name, lid = item if isinstance(item, tuple) else ("", item)
        t0 = time.time()
        r = call(a.base, "/v1/ai/approve-suggest", {"leaveId": lid}, token)
        cost = int((time.time() - t0) * 1000)
        if r.get("code") == 200:
            d = r["data"]
            full = all(d.get(k) for k in ("risk", "suggestion", "draft"))
            ok += 1 if full else 0
            rows.append([name, lid, 200, "是" if full else "缺段", d.get("costMs", cost),
                         d.get("suggestion", ""), d.get("draft", "")])
            print("%-18s id=%-4s 200 三段=%s %5sms  %s" % (name, lid, "齐全" if full else "缺段",
                                                         d.get("costMs", cost), d.get("suggestion", "")[:50]))
        else:
            degraded += 1
            rows.append([name, lid, r.get("code"), "降级", cost, r.get("message", ""), ""])
            print("%-18s id=%-4s %-4s 降级：%s" % (name, lid, r.get("code"), r.get("message", "")))
        time.sleep(0.2)

    total = len(rows)
    costs = [int(x[4]) for x in rows if x[2] == 200]
    print("-" * 60)
    print("用例 %d  格式合规 %d  降级 %d  平均耗时 %.0fms" %
          (total, ok, degraded, (sum(costs) / len(costs)) if costs else 0))
    # 全量重跑才覆盖原记录；--ids 抽查写到 -rerun.csv，避免冲掉 docs 里的 10 条评测
    out = os.path.splitext(a.csv)[0] + "-rerun.csv" if a.ids else a.csv
    with open(out, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["用例", "leaveId", "code", "三段齐全", "costMs", "建议", "意见草稿"])
        w.writerows(rows)
    print("已写入", out)
    return 0

if __name__ == "__main__":
    sys.exit(main())

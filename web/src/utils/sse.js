import { getToken } from "@/utils/request";
async function streamChat(message, history, handlers, signal) {
  const res = await fetch("/api/v1/ai/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify({ message, history }),
    signal
  });
  if (!res.ok || !res.body) {
    handlers.onError?.(`AI 服务不可用（HTTP ${res.status}）`);
    return;
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventName = "";
  let dataLines = [];
  const dispatch = () => {
    if (!dataLines.length) return;
    const raw = dataLines.join("\n");
    dataLines = [];
    let payload = null;
    try {
      payload = JSON.parse(raw);
    } catch {
      return;
    }
    if (eventName === "delta" && typeof payload?.d === "string") {
      handlers.onDelta(payload.d);
    } else if (eventName === "open") {
      handlers.onOpen?.(payload);
    } else if (eventName === "done") {
      handlers.onDone?.(payload);
    } else if (eventName === "error") {
      handlers.onError?.(payload?.message || "AI 服务异常");
    }
    eventName = "";
  };
  try {
    for (; ; ) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let nl;
      while ((nl = buffer.indexOf("\n")) >= 0) {
        let line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        if (line.endsWith("\r")) line = line.slice(0, -1);
        if (line === "") {
          dispatch();
          continue;
        }
        if (line.startsWith(":")) continue;
        const colon = line.indexOf(":");
        const field = colon === -1 ? line : line.slice(0, colon);
        let val = colon === -1 ? "" : line.slice(colon + 1);
        if (val.startsWith(" ")) val = val.slice(1);
        if (field === "event") eventName = val;
        else if (field === "data") dataLines.push(val);
      }
    }
    dispatch();
  } catch (e) {
    if (e?.name === "AbortError") return;
    handlers.onError?.(e?.message || "连接中断");
  }
}
export {
  streamChat
};

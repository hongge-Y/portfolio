let timer;
function toast(message, type = "info") {
  let el = document.getElementById("app-toast");
  if (!el) {
    el = document.createElement("div");
    el.id = "app-toast";
    el.style.cssText = [
      "position:fixed",
      "left:50%",
      "top:24px",
      "transform:translateX(-50%)",
      "padding:10px 18px",
      "border-radius:10px",
      "font-size:14px",
      "color:#fff",
      "z-index:9999",
      "max-width:80vw",
      "text-align:center",
      "box-shadow:0 6px 20px rgba(0,0,0,.18)",
      "transition:opacity .2s"
    ].join(";");
    document.body.appendChild(el);
  }
  el.style.background = type === "error" ? "#e5484d" : "#1f2937";
  el.textContent = message;
  el.style.opacity = "1";
  window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    if (el) el.style.opacity = "0";
  }, 2600);
}
export {
  toast
};

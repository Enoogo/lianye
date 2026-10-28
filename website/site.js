"use strict";

const button = document.querySelector("[data-copy-repo]");
const address = document.querySelector("#repo-url");
const status = document.querySelector("#copy-status");

if (button && address && status && navigator.clipboard?.writeText) {
  button.hidden = false;
  button.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(address.textContent.trim());
      status.textContent = "订阅地址已复制。";
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(address);
      selection?.removeAllRanges();
      selection?.addRange(range);
      status.textContent = "请长按或选中上方地址复制。";
    }
  });
}

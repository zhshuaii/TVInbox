"use strict";

const element = (id) => document.getElementById(id);
const maximumBytes = 512 * 1024 * 1024;
let activeRequest = null;

function sizeText(bytes) { return (bytes / 1048576).toFixed(1) + " MiB"; }
function finish(state, message) {
  activeRequest = null;
  element("transfer").dataset.state = state;
  element("status").textContent = message;
  element("choose").disabled = false;
  element("cancel").hidden = true;
  element("choose-label").textContent = "继续上传";
}
function reject(message) {
  element("transfer").hidden = false;
  element("filename").textContent = "无法上传";
  element("detail").textContent = "";
  element("percent").textContent = "";
  element("progress").value = 0;
  finish("error", message);
}
function upload(file) {
  if (activeRequest || !file) return;
  if (!/\.apk$/i.test(file.name)) { reject("请选择单个 .apk 文件，不支持 APKS、XAPK 或 ZIP。"); return; }
  if (file.size <= 0 || file.size > maximumBytes) { reject("文件为空或超过 512 MiB，请选择其他 APK。"); return; }
  element("transfer").hidden = false;
  element("transfer").dataset.state = "uploading";
  element("filename").textContent = file.name;
  element("detail").textContent = "0.0 MiB / " + sizeText(file.size);
  element("percent").textContent = "0%";
  element("progress").value = 0;
  element("status").textContent = "正在上传，请保持此页面在前台。";
  element("choose").disabled = true;
  element("cancel").hidden = false;
  const xhr = new XMLHttpRequest();
  activeRequest = xhr;
  xhr.open("POST", "/api/upload");
  xhr.timeout = 15 * 60 * 1000;
  xhr.setRequestHeader("Content-Type", "application/octet-stream");
  xhr.setRequestHeader("X-TVInbox-Upload", "1");
  xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name));
  xhr.upload.onprogress = (event) => {
    if (!event.lengthComputable || activeRequest !== xhr) return;
    const percent = Math.min(100, Math.floor(event.loaded * 100 / event.total));
    element("progress").value = percent;
    element("percent").textContent = percent + "%";
    element("detail").textContent = sizeText(event.loaded) + " / " + sizeText(event.total);
    if (percent === 100) element("status").textContent = "已发送，等待电视保存并检查文件…";
  };
  xhr.onload = () => {
    if (activeRequest !== xhr) return;
    let response;
    try { response = JSON.parse(xhr.responseText); } catch (_) {
      finish("error", "未收到有效确认，请先查看电视列表，再决定是否重新上传。");
      return;
    }
    if (xhr.status >= 200 && xhr.status < 300 && response.ok === true) {
      element("progress").value = 100;
      element("percent").textContent = "100%";
      element("detail").textContent = sizeText(file.size);
      finish("success", "上传完成。请用电视遥控器选择安装。");
    } else {
      finish("error", response.message || "上传失败，请查看电视端后重试。");
    }
  };
  xhr.onerror = () => { if (activeRequest === xhr) finish("error", "连接中断。文件可能已经接收，请先查看电视列表再重试。"); };
  xhr.ontimeout = () => { if (activeRequest === xhr) finish("error", "等待超时，请检查电视列表和局域网连接。"); };
  xhr.onabort = () => { if (activeRequest === xhr) finish("error", "已取消。若电视已完整接收，文件仍可能出现在列表中。"); };
  xhr.send(file);
}

element("address").textContent = location.host;
element("choose").addEventListener("click", () => element("file").click());
element("file").addEventListener("change", (event) => {
  const file = event.target.files[0];
  event.target.value = "";
  upload(file);
});
element("cancel").addEventListener("click", () => { if (activeRequest) activeRequest.abort(); });
for (const eventName of ["dragover", "drop"]) {
  document.addEventListener(eventName, (event) => event.preventDefault());
}
element("drop-zone").addEventListener("drop", (event) => {
  if (activeRequest) return;
  const files = event.dataTransfer.files;
  if (files.length !== 1) { reject("请一次上传一个 APK。"); return; }
  upload(files[0]);
});

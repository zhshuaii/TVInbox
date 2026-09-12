# 构建与固定签名

应用名称为 **轻收**，项目名 TVInbox。当前版本 `0.1`、versionCode `3`，发布包名 `io.github.zhshuaii.tvinbox`，附件 `TVInbox-v0.1.apk`。

## 一次性配置

固定 RSA-4096 私钥已经生成，不需要再次生成。私有备份由仓库所有者单独保管，仓库仅保存公钥证书的 SHA-256 指纹。

在仓库 Settings → Secrets and variables → Actions → New repository secret 创建 **TVINBOX_SIGNING_BUNDLE**，值为私有备份中 `TVINBOX_SIGNING_BUNDLE.txt` 的完整内容。只需这一个 Secret。

该文件是包含 PKCS12 密钥及密码的 Base64 编码包；Base64 不是加密。不能粘贴到聊天、Issue、README、普通 Actions 输入框或公开日志，也不能上传到仓库或 Releases。备份 ZIP 同样包含私钥和密码，需要离线安全保存。

保存后到 Actions → Android build and release → Run workflow，选择 main。新增 Secret 本身不会触发构建。

也可以在已授权的 GitHub CLI 中读取本地文件，无需把内容写入命令历史：

```sh
gh secret set TVINBOX_SIGNING_BUNDLE --repo zhshuaii/TVInbox < TVINBOX_SIGNING_BUNDLE.txt
gh workflow run android.yml --repo zhshuaii/TVInbox --ref main
```

## 构建与发布规则

只有 `.github/workflows/android.yml` 一条流水线。先做网页、签名脚本、JVM、Lint 检查并编译 unsigned Release，再在独立 job 中还原固定密钥、校验真实证书、执行 zipalign/apksigner、验证签名与证书指纹，最后发布到 Releases。

密钥只传给签名步骤，不传给 Gradle；在临时私有目录使用，用后清理，不进入缓存或构建附件。固定指纹不匹配即拒绝签名。缺少 Secret 时保留编译报告并明确提示未发布，不使用临时生成的密钥兜底。

只在本仓库 main 的 push / workflow_dispatch 上读取 Secret；PR 不读取签名密钥。官方 Actions 固定到完整提交 SHA。上传附件仅为 APK、SHA256SUMS、build-info.txt，不包含私钥。

## 0.1 迁移

此轮按用户要求保留版本 0.1。一旦固定签名验证通过，允许替换原 v0.1（原标签提交 51002951a20ed93f56441eeaaa5968645157dc55）的附件及标签，Release 名称改为“轻收 0.1”。其他已发布版本一律不自动覆盖。

未配置 Secret 或签名失败时，不触碰原 v0.1。完成迁移后，新版本需要递增 versionCode 并修改 versionName。已发布且对应同一提交时不重复覆盖；不同提交复用同一版本会被拒绝。

旧包名 io.github.zhshuaii.tvinbox.debug 与新包名不同，可以并存，收件箱不共享。后续使用新包名和同一固定密钥覆盖更新。不要丢失或重新生成密钥。

## 工具链

JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.21、Android SDK/targetSdk 35、Build Tools 35.0.0。升级前完成局域网、安装器及生命周期的真机回归。

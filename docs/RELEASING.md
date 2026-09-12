# 构建和发布

## 统一版本

应用名称为“轻收 · TVInbox”，当前版本为 `0.1`。版本名称不带额外后缀；标签为 `v0.1`，下载文件为 `TVInbox-v0.1.apk`。

提交 main 或手动运行 Android build and release，执行网页/JVM 测试、Lint、两种构建变体和签名验证。全部通过后自动创建普通 Release，附上 APK、SHA256SUMS、build-info.txt，不只保存短期 Artifacts。

同一版本已经发布时，不覆盖附件或移动标签。后续修改仅产生 Actions Artifacts；发布下一版本需递增 versionCode，并设置新的 versionName。两段版本如 `0.1` 和三段版本如 `0.1.1` 均受支持。

TVInbox artifact 保留 14 天，checks-and-reports 保留 7 天；Releases 独立保存。

## 当前签名机制

名称调整不变更应用身份。当前自动构建附件沿用 `.debug` applicationId 与 CI 临时签名，不承诺跨构建覆盖安装；签名冲突时卸载原应用会清除其收件箱。此构建方式与固定密钥签名不同。

## 固定签名

在可信环境生成并离线备份 keystore，不把 keystore、密码或 Base64 内容提交进仓库、聊天、Issue 或日志。

配置仓库 Actions Secrets：

| Secret | 含义 |
|---|---|
| TVINBOX_KEYSTORE_BASE64 | 固定 keystore 的 Base64 内容 |
| TVINBOX_STORE_PASSWORD | keystore 密码 |
| TVINBOX_KEY_ALIAS | 私钥别名 |
| TVINBOX_KEY_PASSWORD | 私钥密码 |

工作流仅在临时目录还原密钥，用后删除，不自动生成固定私钥。Base64 只是编码，不是加密。

切换固定签名发布前，应停用 android.yml 的自动发布 job，保留检查，避免两套签名流程占用同一标签。递增 versionCode，设置新版本，对经过验证的 main 提交推送匹配的 `vX.Y` 或 `vX.Y.Z` 标签。Signed release 校验标签和 Secrets，执行检查、编译、签名验证后发布 APK。缺少密钥即失败，不回退到其他签名。

固定签名包名为 `io.github.zhshuaii.tvinbox`，与现有自动构建包名不同，两者收件箱互不共享。

工作流默认 contents: read，仅发布任务授予 contents: write。外部 PR 不读取密钥，也不公开发布附件。

## SDK 兼容

当前 targetSdk / compileSdk 为 35。升级前检查局域网权限、安装及 Activity 生命周期行为，并完成真机回归。

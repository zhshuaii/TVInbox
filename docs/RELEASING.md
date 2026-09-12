# 构建和发布

## 自动发布测试 APK

提交 main 或手动运行 Android build and preview release，会执行检查和编译。通过后在 Releases 自动创建 Pre-release，附上 APK、SHA256SUMS、build-info.txt；不只是上传短期 Actions Artifacts。PR 不触发公开发布。

测试包使用 `.debug` applicationId 与 CI debug 签名，不承诺不同构建可以覆盖安装。测试签名冲突时需要卸载旧测试版，其收件箱也会删除。正式更新身份需要自己的固定签名。

`TVInbox-preview` artifact 保留 14 天；`checks-and-reports` 报告保留 7 天。Releases 附件与 Artifacts 分开保存。

## 正式固定签名

在可信环境生成并离线备份自己的 release keystore。不要把 keystore、密码或 Base64 内容提交进仓库、聊天、Issue 或构建日志。

配置仓库 Actions Secrets：

| Secret | 含义 |
|---|---|
| TVINBOX_KEYSTORE_BASE64 | 固定 release keystore 的 Base64 内容 |
| TVINBOX_STORE_PASSWORD | keystore 密码 |
| TVINBOX_KEY_ALIAS | 私钥别名 |
| TVINBOX_KEY_PASSWORD | 私钥密码 |

工作流只在临时目录还原密钥，用后删除；不自动生成正式私钥。Base64 只是编码，不是加密。

发布前递增 app/build.gradle.kts 的 versionCode，设置 versionName，然后给经过验证的提交打匹配标签，例如 v0.1.0。Signed release 会检查标签、签名 Secrets，运行网页测试、JVM 测试、Lint 和正式包编译，验证签名后自动公开到 Releases。缺少签名信息时失败，不退回测试签名冒充正式版。

所有工作流默认只有 contents: read；仅发布任务授予当前仓库 contents: write。外部 PR 不获得发布权限，也不读取签名密钥。

## 版本兼容

当前 targetSdk / compileSdk 固定为 35。升级 SDK 前单独检查局域网、安装及 Activity 生命周期行为，并执行真机回归。

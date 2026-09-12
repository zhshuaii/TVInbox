# 构建状态

以每个提交的 GitHub Actions 实际结果为准。

`build` 检查网页、签名脚本、JVM 单元测试、Android Lint，并编译 unsigned Release。`sign-and-publish` 仅在 main 上使用已有的固定签名 Secret 签名、校验并发布。

**build 成功不等于已经签名发布。** 缺少 `TVINBOX_SIGNING_BUNDLE` 时，sign-and-publish 会明确报告 Publication blocked，后续签名和发布步骤全部跳过，旧 Releases 不变。

名称为“轻收”、版本 0.1、发布包名 io.github.zhshuaii.tvinbox。证书指纹在 signing/release-cert.sha256，实际发布记录在 Release 附件 build-info.txt。

源码、签名配置或生成的私钥存在，不代表新的签名 APK 已经发布。编译检查不替代真实电视、遥控器和手机浏览器验收。

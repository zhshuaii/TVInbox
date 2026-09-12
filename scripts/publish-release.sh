#!/usr/bin/env bash
set -euo pipefail
[[ "$GH_REPO" == 'zhshuaii/TVInbox' ]]
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]
[[ "$(gh api "repos/$GH_REPO/git/ref/heads/main" --jq '.object.sha')" == "$GITHUB_SHA" ]] || { echo 'main advanced; run the current commit before publishing.'; exit 1; }
(cd dist && sha256sum --check SHA256SUMS)
TAG="v${VERSION}"
NOTES="$RUNNER_TEMP/release-notes.md"
cat > "$NOTES" <<EOF
# 轻收 ${VERSION}
手机扫码上传 APK，电视列表手动安装、删除和清空。上传不会自动安装。

下载 TVInbox-v${VERSION}.apk；Source code 压缩包不是安装包。
名称：轻收。包名：io.github.zhshuaii.tvinbox。使用固定 RSA-4096 签名。
SHA256SUMS 校验安装包，build-info.txt 记录源码提交和证书指纹。

旧包名 io.github.zhshuaii.tvinbox.debug 与本包为不同应用，安装包收件箱不共享。
此后保持同一包名、签名并递增 versionCode 发布更新。真机兼容性以实机验收为准。

Commit: ${GITHUB_SHA}
EOF
if gh api "repos/$GH_REPO/releases/tags/$TAG" > "$RUNNER_TEMP/existing-release.json" 2> "$RUNNER_TEMP/release-error.txt"; then
  TAG_SHA="$(gh api "repos/$GH_REPO/git/ref/tags/$TAG" --jq '.object.sha')"
  if [[ "$TAG_SHA" == "$GITHUB_SHA" ]]; then
    echo "Release $TAG already points to this source; keeping published assets."
    exit 0
  fi
  # One explicitly requested v0.1 identity migration; never overwrite other versions.
  [[ "$TAG" == 'v0.1' && "$TAG_SHA" == '51002951a20ed93f56441eeaaa5968645157dc55' ]] || { echo 'Version already published. Increment versionName and versionCode.'; exit 1; }
  gh release edit "$TAG" --repo "$GH_REPO" --draft=true
  gh release upload "$TAG" dist/* --repo "$GH_REPO" --clobber
  gh api --method PATCH "repos/$GH_REPO/git/refs/tags/$TAG" -f sha="$GITHUB_SHA" -F force=true > /dev/null
  gh release edit "$TAG" --repo "$GH_REPO" --draft=false --prerelease=false --latest --title "轻收 ${VERSION}" --notes-file "$NOTES"
else
  grep -q 'HTTP 404' "$RUNNER_TEMP/release-error.txt" || { cat "$RUNNER_TEMP/release-error.txt"; exit 1; }
  gh release create "$TAG" dist/* --repo "$GH_REPO" --target "$GITHUB_SHA" --latest --title "轻收 ${VERSION}" --notes-file "$NOTES"
fi
echo "Published $TAG using the fixed signing certificate." >> "$GITHUB_STEP_SUMMARY"

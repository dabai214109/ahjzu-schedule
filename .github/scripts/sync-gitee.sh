#!/usr/bin/env bash
# 把 CI 刚编出来的 APK 同步到 Gitee（Gitee = 国内下载 + App 更新清单的唯一来源）
#
#   sync-gitee.sh beta      测试通道：只更新 beta 分支上的 kcb-beta.apk
#                           每次 force-push 一个单提交分支，不留历史，
#                           避免 3MB × 每次 push 把仓库配额撑爆。
#                           App 从不读这个文件，所以只有自己下载测试用，用户完全无感。
#
#   sync-gitee.sh publish   正式通道：更新 master 上的 kcb.apk + version.json + README
#                           用户的「检查更新」只认 version.json，
#                           所以只有这一步会真正影响所有用户。
#
# 闸门由 workflow 控制：push 只走 beta，publish 只能由 Actions 页面手动触发。
set -euo pipefail

MODE="${1:-}"
case "$MODE" in
  beta|publish) ;;
  *) echo "用法：sync-gitee.sh beta|publish" >&2; exit 2 ;;
esac

if [ -z "${GITEE_TOKEN:-}" ]; then
  echo "::error::缺少 GITEE_TOKEN，请在仓库 Secrets 里配置 Gitee 私人令牌"
  exit 1
fi

ROOT="$(pwd)"
OWNER="dabai214109"
REPO="ahjzu-schedule"
REMOTE="https://${OWNER}:${GITEE_TOKEN}@gitee.com/${OWNER}/${REPO}.git"
BETA_URL="https://gitee.com/${OWNER}/${REPO}/raw/beta/kcb-beta.apk"
APK_URL="https://gitee.com/${OWNER}/${REPO}/raw/master/kcb.apk"

APK="$(ls kcb-*.apk 2>/dev/null | head -1 || true)"
if [ -z "$APK" ]; then
  echo "::error::当前目录没找到 kcb-*.apk（应在签名步骤产出）"
  exit 1
fi

VERSION="$(node .github/scripts/version.js --name)"
SUBJECT="$(git log -1 --pretty=%s)"

# 令牌只出现在 URL 里，且 GitHub 会自动打码；这里不打印任何含令牌的字符串
echo "==> 模式 $MODE · 版本 v$VERSION · 产物 $APK · 提交「$SUBJECT」"

# ── 测试通道 ────────────────────────────────────────────────
if [ "$MODE" = "beta" ]; then
  WORK="$(mktemp -d)"
  cp "$ROOT/$APK" "$WORK/kcb-beta.apk"
  cd "$WORK"
  git init -q -b beta
  git add kcb-beta.apk
  git -c user.name="github-actions" -c user.email="actions@github.com" \
      commit -qm "测试版 v$VERSION（构建 #${GITHUB_RUN_NUMBER:-?}）：$SUBJECT"
  git push -q -f "$REMOTE" beta:beta
  echo "==> 测试版已更新（单提交，无历史堆积）：$BETA_URL"
  exit 0
fi

# ── 正式通道 ────────────────────────────────────────────────
WORK="$(mktemp -d)"
git clone -q --depth 1 "$REMOTE" "$WORK/repo"
cd "$WORK/repo"

cp "$ROOT/$APK" kcb.apk
node "$ROOT/.github/scripts/version.js" --write-json version.json --notes "$SUBJECT"
if [ -f "$ROOT/.github/gitee/README.md" ]; then
  cp "$ROOT/.github/gitee/README.md" README.md
fi

git add -A
if git diff --cached --quiet; then
  echo "==> 与 Gitee 上现有内容完全一致，无需提交"
  exit 0
fi
git -c user.name="github-actions" -c user.email="actions@github.com" \
    commit -qm "发布 v$VERSION：$SUBJECT"
git push -q origin HEAD:master
echo "==> 正式版已发布：$APK_URL"
echo "==> 用户端「检查更新」现在会看到 v$VERSION"

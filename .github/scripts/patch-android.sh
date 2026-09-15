#!/usr/bin/env bash
# 在 CI 中定制 Capacitor 生成的 Android 工程：应用名 / 图标 / 版本号
# 由 .github/workflows/build-android.yml 在 `npx cap add android` 之后调用
set -euo pipefail

APP="android-app/android"
RES="$APP/app/src/main/res"
ICONS="android-app/icons"

echo "==> 应用名称"
sed -i 's|<string name="app_name">[^<]*</string>|<string name="app_name">我的课表</string>|' \
  "$RES/values/strings.xml"

echo "==> 替换启动图标"
for D in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp "$ICONS/mipmap-$D/ic_launcher.png"            "$RES/mipmap-$D/ic_launcher.png"
  cp "$ICONS/mipmap-$D/ic_launcher_round.png"      "$RES/mipmap-$D/ic_launcher_round.png"
  cp "$ICONS/mipmap-$D/ic_launcher_foreground.png" "$RES/mipmap-$D/ic_launcher_foreground.png"
done

echo "==> 自适应图标（蓝色背景 + 白色前景）"
cat > "$RES/mipmap-anydpi-v26/ic_launcher.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
EOF
cp "$RES/mipmap-anydpi-v26/ic_launcher.xml" "$RES/mipmap-anydpi-v26/ic_launcher_round.xml"

# 覆盖 Capacitor 模板自带的背景色定义（不能新建文件，否则资源重名）
cat > "$RES/values/ic_launcher_background.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#3B82F6</color>
</resources>
EOF

echo "==> 版本号 (run #${GITHUB_RUN_NUMBER})"
sed -i "s|versionCode [0-9][0-9]*|versionCode ${GITHUB_RUN_NUMBER}|" "$APP/app/build.gradle"
sed -i "s|versionName \"[^\"]*\"|versionName \"1.0.${GITHUB_RUN_NUMBER}\"|" "$APP/app/build.gradle"

echo "==> 定制完成"

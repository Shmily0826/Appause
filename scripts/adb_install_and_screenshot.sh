#!/usr/bin/env bash
set -e

# Appause 自动安装 + 启动 + 截图脚本
# 用法：在 Git Bash 中 ./scripts/adb_install_and_screenshot.sh

export JAVA_HOME=/d/Dev-Setup/jdk
export ANDROID_SDK_ROOT=/c/Users/Shmily/AppData/Local/Android/Sdk
export PATH="/d/Dev-Setup/Git/usr/bin:/c/Users/Shmily/AppData/Local/Android/Sdk/platform-tools:$PATH"

PACKAGE="com.appause.android.debug"
APK="D:/CODE/project/Appause/app/build/outputs/apk/debug/app-debug.apk"
SHOT_DIR="D:/CODE/project/Appause/output"
SHOT="$SHOT_DIR/appause_screenshot_$(date +%Y%m%d_%H%M%S).png"

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK"
  echo "Run ./gradlew assembleDebug first."
  exit 1
fi

# 找到第一个在线的设备或模拟器
DEVICE=$(adb devices -l | awk '/device product|device model/{print $1; exit}')
if [ -z "$DEVICE" ]; then
  echo "No Android device/emulator found. Start an emulator or connect a device."
  exit 1
fi
echo "Using device: $DEVICE"

echo "Installing debug APK..."
adb -s "$DEVICE" install -r -t "$APK"

echo "Launching Appause..."
adb -s "$DEVICE" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1

# 模拟器较卡，等首帧渲染完成
sleep 6

echo "Taking screenshot..."
adb -s "$DEVICE" shell screencap -p /sdcard/appause_auto_shot.png
adb -s "$DEVICE" pull /sdcard/appause_auto_shot.png "$SHOT"

echo "Screenshot saved to: $SHOT"

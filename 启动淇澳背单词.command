#!/bin/bash
set -euo pipefail
source "$(dirname "$0")/scripts/android-env.sh"
cd "$QIAO_PROJECT_DIR"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
APK="$QIAO_PROJECT_DIR/安装包/淇澳背单词-1.0.apk"
AVD="MorningWords_API_36"
SERIAL="emulator-5554"

for required in "$JAVA_HOME/bin/java" "$ADB" "$EMULATOR" "$APK"; do
    if [[ ! -f "$required" ]]; then
        echo "缺少文件：$required。请参照《重启与恢复说明.md》。" >&2
        exit 1
    fi
done
if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD"; then
    echo "找不到模拟器 $AVD，请参照《重启与恢复说明.md》恢复备份。" >&2
    exit 1
fi
if [[ "${1:-}" == "--check" ]]; then
    echo "运行环境、安装包与模拟器配置检查通过。"
    exit 0
fi
if [[ -n "${1:-}" ]]; then
    echo "用法：启动淇澳背单词.command [--check]" >&2
    exit 1
fi

"$ADB" start-server
if "$ADB" devices | grep -q "^${SERIAL}[[:space:]]"; then
    actual_avd="$("$ADB" -s "$SERIAL" emu avd name | tr -d '\r' | head -n 1)"
    if [[ "$actual_avd" != "$AVD" ]]; then
        echo "端口 5554 被其他模拟器占用，请先关闭它再重试。" >&2
        exit 1
    fi
else
    echo "正在启动模拟器，首次冷启动可能需要一两分钟……"
    nohup "$EMULATOR" -avd "$AVD" -port 5554 -no-snapshot -no-boot-anim \
        -netdelay none -netspeed full \
        > "$QIAO_PROJECT_DIR/.local-runtime/emulator.log" 2>&1 < /dev/null &
fi

booted=false
for ((attempt=0; attempt<120; attempt++)); do
    if [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" ]]; then
        booted=true
        break
    fi
    sleep 2
done
if [[ "$booted" != true ]]; then
    echo "模拟器启动超时，请查看 .local-runtime/emulator.log。不会清除任何学习数据。" >&2
    exit 1
fi

# Never uninstall or clear data. An existing installation remains untouched.
if ! "$ADB" -s "$SERIAL" shell pm path com.morningwords | grep -q '^package:'; then
    "$ADB" -s "$SERIAL" install -r "$APK"
fi
"$ADB" -s "$SERIAL" shell am start -W -n com.morningwords/.MainActivity
echo "淇澳背单词已启动，可以关闭此终端窗口。"

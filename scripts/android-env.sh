#!/bin/bash
# Source this file so builds and the emulator use persistent local dependencies.
QIAO_PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$QIAO_PROJECT_DIR/.local-runtime/jdk/jdk-17.0.20.1+1/Contents/Home"
export ANDROID_HOME="$QIAO_PROJECT_DIR/.local-runtime/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

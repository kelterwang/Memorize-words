# 淇澳背单词（MorningWords）

本机重新开机后，双击 `启动淇澳背单词.command` 即可启动模拟器和 App。保存位置、备份及构建方法见 [重启与恢复说明](重启与恢复说明.md)。

面向高中生的离线 Android 单词晨测 App。产品严格以仓库中的 `设计文档.md` 与 `技术方案文档.md` 为实现基线。

## 已实现

- 纯文本与“单词 + 释义”导入预览，批内/跨批次规范化去重
- 批次列表、安全删除与多批次组合测试
- 学生自测、家长考我两种模式
- `整轮测试 → 轮次统计 → 按需复测错题/退出` 可重复晨测流程
- Room 逐题事务持久化、进行中任务恢复与明确放弃
- 长期错词累计、复测时间范围与 `KNOW / UNKNOWN / MASTERED` 三种作答
- DataStore 偏好、系统 TTS 自动/手动发音及英文语音回退提示
- Compose Material 3 四入口界面：今日、词库、错词、我的

## 构建

需要 JDK 17 与 Android SDK 36：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

连接 Android 设备或启动模拟器后：

```bash
./gradlew connectedDebugAndroidTest
```

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 数据原则

业务事实只保存在 Room；DataStore 仅保存偏好。正常晨测只有首轮“不会”增加长期错词次数。批次删除或错词标记为已掌握都不会删除历史测试与错误记录。

错词页与首页“待复习”只统计答错时已存在、且目前仍保留的词库中的错词。删除最后一个相关词库后，该词不再显示为待复习；重新导入不会恢复已删除词库的待复习状态，之后再次答错才会重新加入。其他现有词库共享的有效错词继续保留，历史答错记录和累计次数不受影响。

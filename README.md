# 音乐链接提取器（Android）

粘贴音乐网页链接，分析页面 HTML 中公开可访问的音频资源地址，并复制结果。

## 功能
- 输入 http/https 网页链接
- 识别 MP3、M4A、AAC、OGG、OPUS、WAV、FLAC，以及 HLS（M3U8）和 DASH（MPD）播放列表地址
- 显示结果并一键复制
- 支持从其他 Android 应用分享网页链接到本应用
- 无需注册账号

## 构建
需要 JDK 17、Android SDK 和 Gradle 8.7：
`gradle assembleDebug`

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 会自动构建 APK；推送 v 开头的标签会自动创建 Release 并附上 APK。

## 限制与合规
本工具只分析用户提供且可公开访问或用户有权访问的网页内容。它不绕过 DRM、付费墙、登录验证、地域限制或其他访问控制。部分网站依赖 JavaScript、动态签名、Cookie 或受保护的媒体流，可能无法提取。提取到的地址不代表拥有下载或再分发音频的权利。

## 技术
Kotlin、Android SDK、HttpURLConnection。最低 Android 6.0（API 23）。

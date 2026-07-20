# 洛克王国 AI 助手

## 项目结构

```
luo_king_server/     ← 云服务器（Python）：全部 AI 决策
luo_king_client/     ← Android APK（Kotlin）：截图 + 上传 + 接收 + 点击
PROTOCOL.md          ← 通信协议 v1.0
```

## 快速开始

### 1. 启动服务器

```bash
pip install websockets aiohttp openai pydantic rich
export SENSENOVA_API_KEY="sk-你的Key"
python main.py server
```

服务器监听 `ws://0.0.0.0:8765`，状态接口 `http://0.0.0.0:8766/status`。

### 2. 编译 APK

**方案 A：GitHub Actions（推荐）**

提交代码到 GitHub，Actions 自动编译：
```
git push origin main
```
在 Actions 页面下载 `app-debug.apk`。

**方案 B：本地 Android Studio**

```bash
# 用 Android Studio 打开 luo_king_client/ 目录
# Build → Build APK
```

### 3. 安装运行

1. 安装 APK 到手机
2. 开启无障碍服务（设置 → 无障碍 → 洛克王国AI助手）
3. 打开 APK → 点击"连接" → 授权屏幕捕获
4. 打开洛克王国，AI 自动控制战斗

## 通信协议

详见 [PROTOCOL.md](./PROTOCOL.md)。

## 技术栈

| 层 | 语言 | 框架 |
|----|------|------|
| 云服务器 | Python | websockets, openai, aiohttp |
| 手机 APK | Kotlin | OkHttp, AndroidX, kotlinx-serialization |
| 视觉识别 | - | SenseNova 6.7 Flash-Lite |
| 通信 | - | WebSocket JSON |
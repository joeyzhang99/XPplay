# XPplay

**让安卓车机用上 CarPlay —— 无需认证盒子、无需 dongle、无需 root。**

> A CarPlay receiver for Android head units. It brings Apple's CarPlay to stereos that have no
> CarPlay of their own, in software.

本仓库针对 **小鹏 G3（D20）** 定制：Android 9 / 骁龙 820 / 3.7 GB 内存 / 固定竖屏 1080×1920 中控。

---

## 这是什么

原厂车机不带 CarPlay，想在车里用 iPhone 的导航、音乐、通话、Siri，通常只能买认证盒子或换车机。
XPplay 是一个纯软件接收端：内置 MFi 身份与完整的 iAP2 / AirPlay 协议栈，装到车机上即可工作。

本项目基于以下开源工作：

| 项目 | 作用 | 许可 |
|---|---|---|
| [xcertplay](https://github.com/shilapi/xcertplay) | iAP2 / AirPlay / MFi 协议栈（本仓库 `shared/` 模块的来源） | GPL-3.0 |
| [DiPlay](https://github.com/shihabal3amri/DiPlay) | 完整车机应用外壳，本仓库的 fork 起点 | GPL-3.0 |
| DiAuto | 界面设计来源 | AGPL-3.0 |

向上述作者致谢。

## 功能

**连接**
- 有线 CarPlay（USB 直连）
- 无线 CarPlay：同一局域网 / Wi‑Fi Direct / 车机热点 / 复用已有 Wi‑Fi
- 断线自动恢复，会话内可手动重连
- 配对信息持久化

**音频**
- 四路独立音频：媒体 / 导航提示 / 通话 / 麦克风（Siri）
- 编解码：AAC-LC、AAC-ELD、Opus、LPCM，按协商结果自适应
- 各路独立质量上限（音乐 / 助理 / 导航 / 电话 / 麦克风）
- 回声消除；通话走原车音响；缓冲音频

**显示**
- 竖屏锁定；CarPlay 按真实竖屏比例渲染（不请求旋转屏方帧）
- 视图区域 / 侧边面板 / 分屏
- 自定义安全区、圆角、缩放；日夜模式

**输入**
- 触摸回传（多点）
- 硬件按键 HID、方向盘按键映射（音量、上一首/下一首、接听、Siri）
- 车热点控制（仅需「修改系统设置」权限，无需 ADB）
- 开机自启（可选，需一次 ADB 授权）

## 相对上游的差异

- **去车厂化**：移除全部比亚迪（DiLink / HUD / I2C / CH341）相关模块与资产
- **竖屏专版**：移除旋转屏方帧画布，固定单竖屏区域
- **体积**：R8 压缩后 APK 约 5 MB
- **品牌**：应用名、CarPlay 配件名、热点名统一为 XPplay；车辆品牌标识默认留空

## 构建

需要 JDK 21+ 与 Android SDK（compileSdk 37）。

```bash
export JAVA_HOME=<你的 JDK>
./gradlew :mobile:assembleRelease
```

构建需要 MFi 身份文件（`identity.pk8` + `certificate.p7b`），**本仓库不包含**。
请通过 `DIPLAY_AUTH_ASSETS_DIR` 指向你自己合法持有的身份目录：

```bash
DIPLAY_AUTH_ASSETS_DIR=<你的身份目录> ./gradlew :mobile:assembleRelease
```

签名用 `ANDROID_KEYSTORE_PATH` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`。

质量门：

```bash
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
          :shared:lintDebug :common:lintDebug :mobile:lintRelease
```

## 已知边界

- **非 Apple 认证产品**；CarPlay、AirPlay、iPhone 等商标归 Apple Inc.，本项目与其无关联
- 不支持 CarPlay Ultra
- 不支持仪表盘 / HUD 投射（G3 仪表不可编程）
- 不支持 Android Auto
- CarPlay 只显示有 CarPlay 版本的 App（导航 / 音频 / 通信 / 消息）；抖音、淘宝等无此版本的应用在任何车机上都不会出现
- 仅适配竖屏车机

## 许可

GPL-3.0，见 [LICENSE](LICENSE)。界面部分改编自 DiAuto（AGPL-3.0）。

分发本项目的二进制时，请一并提供对应源码（GPL-3.0 要求）。

## 免责声明

个人自用项目，未获 Apple 认证，不提供任何担保。使用风险自负。

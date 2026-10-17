# XPplay v1.3.0

首个公开版本。面向小鹏 G3（D20，Android 9 / 骁龙 820 / 3.7 GB / 固定竖屏 1080×1920）的 CarPlay 接收端。

## 主要能力

- **有线 / 无线 CarPlay**：USB 直连，或同局域网 / Wi‑Fi Direct / 车机热点 / 复用已有 Wi‑Fi
- **四路音频**：媒体、导航提示、通话、麦克风（Siri），支持 AAC-LC / AAC-ELD / Opus / LPCM 自适应协商
- **竖屏专版**：锁定竖屏，按真实竖屏比例渲染，不请求旋转屏方帧
- **方向盘按键**：音量、上一首/下一首、接听挂断、Siri
- **车热点控制**：仅需「修改系统设置」权限，无需 ADB
- **诊断导出**：会话日志、崩溃记录，便于反馈问题

## 本次发布的技术要点

- 同步上游 [xcertplay](https://github.com/shilapi/xcertplay) 1.4.1 的音频质量体系，含 AAC-ELD 全链路（协商 / 解码 / 编码 / 帧参数）
- 各路音频可独立设置质量上限（音乐 / 助理 / 导航 / 电话 / 麦克风）
- 相对 [DiPlay](https://github.com/shihabal3amri/DiPlay) 移除全部比亚迪相关模块与资产
- R8 压缩后 APK 约 5 MB

## 已知边界

- 非 Apple 认证产品，与 Apple 无关联
- 不支持 CarPlay Ultra、仪表盘 / HUD 投射、Android Auto
- CarPlay 只显示有 CarPlay 版本的 App
- 仅适配竖屏车机

## 许可

GPL-3.0。基于 xcertplay（GPL-3.0）与 DiPlay（GPL-3.0），界面改编自 DiAuto（AGPL-3.0）。

## 关于本 APK

安装包内含 MFi 身份文件，仅授权个人自用。**请勿二次分发或用于商业用途** —— 身份一旦被滥用可能导致证书被吊销，届时所有使用者都会失效。

按 GPL-3.0，本仓库提供完整源码（本 tag）。

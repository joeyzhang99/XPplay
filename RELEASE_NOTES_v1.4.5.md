# XPplay v1.4.5

麦克风上行同步 xcertplay 1.4.2 的修复，并补齐仓库文档。

## 改动

**麦克风上行（对应 xcertplay 1.4.2 `fix(audio): potential issue in microphone uplink`）**

- **AAC-ELD 不再限制 16 kHz 上限**：原实现要求「单声道且采样率 ≤ 16 kHz」，而应用本身会向 iPhone 声明 44.1/48/32/24 kHz 的 ELD 单声道格式；一旦 iPhone 选中这些格式，上行会直接启动失败。现在只保留编码器真正需要的**单声道**限制
- **编码器不可用时干净降级**：协商到设备不支持的 ELD 速率时，上行会记录原因并按失败关闭，而不再把异常抛出 `start()`
- **采集线程优先级提高到 `THREAD_PRIORITY_URGENT_AUDIO`**：与渲染线程争用时优先保证麦克风取样
- 新增单元测试覆盖「所有已声明的单声道 ELD 格式均可采集」「立体声 ELD 提前拒绝」以及 Opus RTP 时钟随协商速率（48/24/16 kHz）变化

**仓库与文档**

- `AGENTS.md` 重写为 XPplay 版本：真实模块（`:shared`/`:common`/`:mobile`）、可运行的质量门命令、测试确定性的注意事项、发布检查表与 fork 专属约束
- 恢复 `docs/THIRD_PARTY_NOTICES.md`（上游许可与捆绑实验性 MFi 身份的说明），修复 `SECURITY.md` 的悬空引用
- `CHANGELOG.md` 补齐 1.4.0–1.4.4 条目

## 说明

- 运行时行为的变化只涉及麦克风上行；连接、显示、按键、更新通道均未改动
- 依旧为小鹏 G3（D20，Android 9，固定竖屏）定制；非 Apple 认证产品

## 校验和

```
289d3e12568dbd6125f9db77ce90a95f50bac30459e563fe62fbae1417cde407  XPplay-V1.4.5-release.apk
```

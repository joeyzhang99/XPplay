# XPplay v1.6.2

2026-10-24。上游检查结果：xcertplay 仍为 1.4.2（无更新）；DiPlay 没有新的 Release/tag（仍为 v0.2.21），但 `main` 已推进到 2026-10-24（`9d896330`，18 个 PR 合入）。本版挑选与 G3 相关、风险最低的两项同步。

## 本版改动

### 1. iPhone 只发心跳、不发画面时提示重启

上游 #1097。iPhone 偶尔会打开 CarPlay 的画面流，却只发 128 字节/秒的心跳包，屏幕一直黑；等待界面已经消失，所以没有任何提示。现在：

- 主画面流打开 **10 秒**仍没有收到任何一帧时，连接界面重新出现；
- 提示“CarPlay 启动后，iPhone 一直没有发送画面。请重启 iPhone（关机再开机），然后重新连接”；
- 日志记录一次 `Screen stream open 10 s with no video frame`。

只在一个会话从未收到过首帧时触发，静止画面不会误报。

### 2. 开机自启动时回到已在运行的 CarPlay

上游 #1085。车机开机时 CarPlay 可能已经被其他入口先拉起来；原来的自启动会打开 Home，Home 看到会话后显示「Open CarPlay」，需要再点一次。现在自启动使用桌面图标同一个 intent（`ACTION_MAIN` + `CATEGORY_LAUNCHER`），DiPlayActivity 会直接回到正在运行的 CarPlay，而不是压一个 Home 在上面。

### 暂不同步的上游内容

- **已知失败原因命名**（#1058/#1062）、**Home 显示上次连接问题**（#1066）：依赖上游后来新增的 `ConnectionFailureCause`/`CarPlayStatus.knownCause` 模型，我们的连接流程已分叉，需单独评估后再做；
- **导航提示申请音频焦点**（#1051，默认关）、**通话按自身采样率**（#898，实验）、**车道指引声明**（#1028，尚未绘制 UI）：属于新功能/实验项，留到 V1.7 结合实车验证；
- **音乐时钟修正后续**（#1070/#1080/#193e3fdd）：我们的时钟修正已在 V1.4.6 做过本地改造，需实车数据验证后再决定是否替换；
- **ECARX 轮速/挡位**（#1089）：G3 不是 ECARX 车机，不适用；BYD/Geely/HUD/有线 CarPlay 相关改动同样不适用。

## 版本号

- `versionCode 10602` / `versionName XPplay-V1.6.2`（两项上游移植，无其他行为变化）

## 校验和

```
ac0904e6fc6164fac5e135aaedc5234170be39b8d35fa368c5f1e421e8684844  XPplay-V1.6.2-release.apk
```

# XPplay v1.6.1

2026-10-23。承接 V1.6.0 的车机实测（`diplay-report(6).txt`）：升级、诊断报告与位置上报均正常；本补丁把“位置上报是否真的在工作”变成可核查的数据，并补充设置说明。

## 先说结论

- 1.6.0 的诊断升级和位置上报都已实测生效；本版**不改变连接、音频、视频行为**。
- 诊断报告新增两行位置上报状态：
  - `Location reporting: enabled/disabled; permission=granted/denied`
  - `Location runtime: subscribed=... components=... provider=... lastFix=... ageMs=... accuracy=... lastSend=GPGGA+GPRMC ageMs=...`
- 只记录**状态、定位来源、fix 年龄/精度和发送的语句类型**，不记录经纬度，报告可以安全分享。
- “向 iPhone 报告位置”的说明补充为：iPhone 请求时**每秒**发送 Android 精确位置；**无 GPS 定位时使用网络定位**。

## 本版改动

### 位置上报可核查

报告头部新增：

```text
Location reporting: enabled; permission=granted
Location runtime: subscribed=yes components=[0, 1, 2, 32769, 32770] requestAgeMs=8200ms provider=started lastFix=gps ageMs=900ms accuracy=6.0 m lastSend=GPGGA+GPRMC ageMs=900ms
```

- `subscribed`：iPhone 是否已用 `0xFFFA` 订阅位置
- `components`：iPhone 请求的语句/参数
- `provider`：Android 定位源是否成功启动
- `lastFix`：实际用于编码的定位来源、年龄、精度
- `lastSend`：实际发出的 NMEA 语句类型（GPGGA/GPRMC）与时间

这些数据来自本次会话的真实运行状态；未连接/未订阅时显示 `subscribed=no provider=idle lastFix=none lastSend=none`。

### 设置说明

8 个语种同步更新“向 iPhone 报告位置”的说明：iPhone 请求时每秒发送精确位置；无 GPS fix 时退回网络定位。行为本身未改动。

## 实测依据

`diplay-report(6).txt`（2026-10-23，V1.5.0 → V1.6.0）中已确认：

- 15:24:27 `Starting CarPlay controller ... location=enabled`
- 15:24:28 `location provider prewarmed=true`
- 15:24:32.639 iPhone 请求 `rx=0xfffa start-location-information`
- 15:24:32.644 XPplay 回复 `tx=0xfffb location-information`

日志只记录每次订阅后的第一帧，之后每秒静默发送，所以日志里通常只看到一条 `tx=0xfffb`。

## 版本号

- `versionCode 10601` / `versionName XPplay-V1.6.1`（诊断可核查性 + 文案修补，无行为变更）

## 校验和

```
af191110ae94f508fa7fb0c72879e48ca6992a0bb0a2f49de4a9c8b7af6a3b25  XPplay-V1.6.1-release.apk
```

# XPplay v1.4.0

软件内新增**检查更新**与**国内镜像通道**。

## 新增

**检查更新**
- 设置 → 关于 → 检查更新：读取最新发布版本，显示是否有新版本
- 可下载并校验 APK（SHA-256 比对），校验通过后直接安装
- 可选用后台检查（设置页开关）

**国内镜像（重点）**
GitHub 在国内常被阻断，因此更新页提供三条备用路径：

| 用途 | 通道 |
|---|---|
| 检查版本 | 先试 GitHub API → jsDelivr 上的 `version.json` → gitmirror 备份 |
| 下载 APK | 直连失败自动改用 `ghfast.top` / `gh-proxy.com` / `ghproxy.net` 加速前缀重试 |
| 手动分发 | 更新页列出各镜像地址，点一下复制，可发到手机或电脑下载 |

三个镜像都是第三方公益加速服务，任一失效只会多花一次连接超时。

## 说明

- 更新源固定指向本仓库（`joeyzhang99/XPplay`），不会误装成上游 DiPlay
- APK 校验和同时发布为 `SHA256SUMS.txt`，可手动核对：

```
c35f47583aba01f0396e4f7d0042dbb1ad1ef25a402df746fd9c2937df059398  XPplay-V1.4.0-release.apk
```

## 已知边界

- 非 Apple 认证产品，与 Apple 无关联
- 仅适配竖屏车机；不支持 CarPlay Ultra、仪表盘投射、Android Auto

## 许可

GPL-3.0。基于 xcertplay 与 DiPlay（均 GPL-3.0），界面改编自 DiAuto（AGPL-3.0）。

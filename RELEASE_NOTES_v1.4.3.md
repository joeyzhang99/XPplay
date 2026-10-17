# XPplay v1.4.3

更新检查改用真正实时的通道。

## 问题

上一版把 jsDelivr 当作备用清单来源，但 jsDelivr 对 `@main` 这类**分支引用**有 12 小时缓存，刚发版时它仍返回旧版本——国内用户点"检查更新"会被告知已是最新。

## 改动

版本清单改为由加速前缀代理 GitHub 的 raw 文件，转发的是实时内容，没有分支缓存：

| 顺序 | 通道 | 说明 |
|---|---|---|
| 1 | `api.github.com` | 海外或代理环境最快 |
| 2 | `ghfast.top` + raw | 国内主力 |
| 3 | `gh-proxy.com` + raw | 备用 |
| 4 | `cdn.jsdelivr.net` | 最后兜底（可能滞后） |

实测三个加速前缀均能取到最新 `version.json`。

## 校验和

```
68376399b444cf82dfb0c56767bf9cd1c66ec20b59938879a32d9eb94de0fc54  XPplay-V1.4.3-release.apk
```

# XPplay v1.4.2

修掉一条失效的备用更新通道。

## 改动

- 备用版本清单从 `raw.gitmirror.com` 换成 `gcore.jsdelivr.net`：前者已停止返回内容，后者是 jsDelivr 的另一组边缘节点
- 三通道现在的组合是：GitHub API → `cdn.jsdelivr.net` → `gcore.jsdelivr.net`
- 两组 jsDelivr 边缘的缓存刷新时间不同，一个还留着旧版本时另一个通常已更新，实际提高了国内检查更新的成功率

## 校验和

```
79904ad80e1d00e1a76934e92ae2015d1f07c021ffb5f078f5b6c921690ca6ff  XPplay-V1.4.2-release.apk
```

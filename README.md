# HealthConnectToS400

![Health Connect](https://img.shields.io/badge/Health-Connect-blue?style=flat-square)

将小米 S400 智能体脂秤的数据同步到 Android Health Connect

## 功能

读秤的 ble 广播解析然后写入 Health Connect

## 用法

- 输入信息
- 点 scan
- 称

## TODO

- 支持最低到 Android 9 (Health Connect SDK 支持到 9)

## 已知问题

- 部分情况会刷新 key 需要重新获取
- 抄来的算法和官方不同 (地区差异?)

## 感谢

- [mi-scale-exporter](https://github.com/lswiderski/mi-scale-exporter)
- [MiScaleBodyComposition](https://github.com/lswiderski/MiScaleBodyComposition)

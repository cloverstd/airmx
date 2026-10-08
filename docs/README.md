# 文档索引

## 标注约定

全仓库统一使用三档标注，用来区分结论的可靠程度：

| 标注 | 含义 |
|---|---|
| ✅ **实测** | 在真实设备上抓包 / 控制验证过 |
| 📖 **反编译** | 从官方 App v3.1.3 反编译代码读出，未在实机跑过 |
| ❓ **推断** | 由调用点或上下文反推，未确认 |

本仓库的实测部分集中在**新风机（AIRMX Pro / Eagle，固件 10.00.17）**。
加湿器（AirWater）和风扇（Fan）两条产品线基本只有 📖 级别的信息。

---

## 两条阅读路径

### 路径 A：我想让设备重新能用

```
01 背景      → 为什么要做这件事
09 获取密钥  → ★ 最关键的一步：拿到 device key
02 架构      → 搭起本地 MQTT 环境
src/airmx.py → 开箱即用的控制脚本
11 陷阱      → 踩坑时来查
```

### 路径 B：我要自己实现客户端 / 写 HA 集成 / 做固件

```
03 连接鉴权  → MQTT 怎么连
04 Topic     → 话题怎么拼
05 签名      → ★★ 签名算法，全仓库最关键的一节
06 指令表    → 有哪些指令
07 字段参考  → 每条指令的字段
08 枚举值    → 各字段的取值含义
11 陷阱      → 实现时容易踩的坑
```

---

## 文档清单

| 文件 | 内容 | 成熟度 |
|---|---|---|
| [01-background.md](01-background.md) | 厂商倒闭的经过、服务器现状、为什么设备还能救 | ✅ |
| [02-architecture.md](02-architecture.md) | 整体架构、最小可行方案、别人已经做好的轮子 | ✅ |
| [03-connection.md](03-connection.md) | 三条产品线的 MQTT 连接与鉴权参数 | 📖 |
| [04-topics.md](04-topics.md) | Topic 规范与逐字段含义 | ✅ / ❓ |
| [05-signing.md](05-signing.md) | **报文信封与签名算法** | ✅ |
| [06-commands.md](06-commands.md) | 全部 cmdId 总表 | ✅ / 📖 |
| [07-fields.md](07-fields.md) | 各消息类的 data 字段明细 | ✅ / 📖 |
| [08-enums.md](08-enums.md) | mode / 开关 / from / type / 哨兵值 | ✅ / 📖 |
| [09-device-key.md](09-device-key.md) | **device key 是什么、怎么拿到** | ✅ |
| [10-ble-provisioning.md](10-ble-provisioning.md) | 蓝牙配网协议 | 📖 |
| [11-pitfalls.md](11-pitfalls.md) | 已知陷阱与代码级 bug | ✅ |

---

## 配套材料

| 位置 | 内容 |
|---|---|
| [`src/airmx.py`](../src/airmx.py) | Python 参考客户端 + 命令行工具，已实机验证 |
| [`src/verify_control.py`](../src/verify_control.py) | 端到端验证脚本：改风量 → 确认 → 还原 |
| [`examples/config.example.toml`](../examples/config.example.toml) | 配置样例 |
| [`reference/java/`](../reference/java/) | 关键反编译片段，作为文档结论的依据 |
| [`tests/test_vectors.json`](../tests/test_vectors.json) | 签名算法测试向量（合成 key，可直接验证实现是否正确） |

---

## 致谢

本项目的 device key 获取链路完全建立在 [openairmx](https://github.com/openairmx)
组织的工作之上，详见 [README](../README.md#生态与致谢)。

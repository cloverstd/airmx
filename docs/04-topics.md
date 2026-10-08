# 04 · Topic 规范

> 来源：反编译 `mqtt/C2089g.java`（原始类名 `Topic`）+ 真实抓包验证
> 依据片段见 [`reference/java/Topic.java`](../reference/java/Topic.java)

## 模板

App 里所有 topic 都由 4 个方法生成：

```java
// 新风机 / 检测仪 —— 7 段
m10721c(deviceId, F1, F2, F3, F4)
    → airmx/01/{F1}/{F2}/{F3}/{F4}/{deviceId}

m10722d(deviceId)                      // 订阅用，通配形式
    → airmx/01/+/+/1/+/{deviceId}

// 加湿器 —— 6 段（少第一个字段）
m10719a(deviceId, F2, F3, F4)
    → airwater/01/{F2}/{F3}/{F4}/{deviceId}

m10720b(deviceId)
    → airwater/01/+/1/+/{deviceId}
```

风扇（`Fan`）在反编译产物里没有独立的 Topic 工厂，但从调用点看用的是
`fan/01/{F2}/{F3}/{F4}/{deviceId}`，与加湿器同构。

## 各字段实测值

`F3` 是**方向位**，这是唯一能从订阅模式自洽解释出来的含义：

| 字段 | 含义 | 观测值 |
|---|---|---|
| `01` | 协议版本？ | 恒为 `01` ❓ |
| `F1` | **含义未确认** | 控制=1、请求上报=0、设备上报=0 ❓ |
| `F2` | **含义未确认**，已知模板中恒为 1 | 恒为 `1` ❓ |
| `F3` | **方向位**：`0` = App→设备，`1` = 设备→App | ✅ |
| `F4` | **含义未确认**，所有已知模板中恒为 1 | 恒为 `1` ❓ |
| 末位 | 设备 ID | ✅ |

> 说明：只有 `F3` 能从代码里推出含义——因为 App 订阅的是 `airmx/01/+/+/1/+/{id}`
> （固定第 4 段为 1），而它发布时第 4 段恒为 0。其余三位缺乏足够上下文，
> 如实标为未确认，不做猜测。

## 全部已知 Topic

| Topic | 方向 | 用途 | 成熟度 |
|---|---|---|---|
| `airmx/01/1/1/0/1/{id}` | App→设备 | **下发控制指令** | ✅ 实测 |
| `airmx/01/0/1/0/1/{id}` | App→设备 | **请求立即上报**（instantPush） | ✅ 实测 |
| `airmx/01/0/1/1/1/{id}` | 设备→App | **设备状态上报** | ✅ 实测 |
| `airmx/01/+/+/1/+/{id}` | App 订阅 | 收设备上报（App 用的通配形式） | 📖 |
| `airmx/user/{uid}` | 服务端→App | 用户级推送（通知、绑定变更） | 📖 |
| `airmx/01/{F1}/1/0/1/{id}` | 设备订阅 | 蓝牙配网时下发给 Snow 的订阅 topic | 📖 |
| `airwater/01/1/0/1/{id}` | App→设备 | 加湿器控制 | 📖 |
| `airwater/01/+/1/+/{id}` | App 订阅 | 加湿器状态 | 📖 |
| `airwater/user/{uid}` | 服务端→App | 加湿器用户推送 | 📖 |
| `fan/01/1/0/1/{id}` | App→设备 | 风扇控制 | 📖 |
| `fan/01/+/1/+/{id}` | App 订阅 | 风扇状态 | 📖 |
| `fan/user/{uid}` | 服务端→App | 风扇用户推送 | 📖 |

## ⚠️ 最容易踩的坑

**控制指令和请求上报用的是不同的 topic。**

```
下发控制     airmx/01/1/1/0/1/{id}     ← 第二个字段是 1
请求上报     airmx/01/0/1/0/1/{id}     ← 第二个字段是 0
```

反直觉，但这是实测确认的。搞错的话设备会**静默忽略**你的报文——不报错、
不回应、什么都不会发生，非常难排查。

依据（`mqtt/eagle/MqttInterface.java`）：

```java
// 控制：mo10523b()
C2089g.m10721c(mqttBaseMessage.getDeviceId(), 1, 1, 0, 1)
//                                              ↑ F1 = 1

// 请求上报：mo10525d()
C2089g.m10721c(mqttReportInterval.getDeviceId(), 0, 1, 0, 1)
//                                                ↑ F1 = 0
```

## 一处代码内部的不一致

App 把请求上报发到 `airmx/01/0/1/0/1/{snowId}`，但它通过蓝牙告诉 Snow 设备
去订阅 `airmx/01/1/0/1/1/{snowId}`（见 [10-ble-provisioning.md](10-ble-provisioning.md)）。

这两者在 `F1/F2/F3` 三个位置上都对不上。可能是固件侧另有约定，也可能是
App 的 bug。**无法从反编译代码确认**，如实记录。

对本项目影响不大——我们要控制的是新风机（Eagle），它工作正常。

## 调用点索引

| 调用点 | 参数 | 结果 |
|---|---|---|
| `MqttInterface.mo10523b` | `(id, 1, 1, 0, 1)` | `airmx/01/1/1/0/1/{id}` |
| `MqttInterface.mo10525d` | `(id, 0, 1, 0, 1)` | `airmx/01/0/1/0/1/{id}` |
| `MqttInterface.mo10527f` | `m10722d(id)` | 订阅 `airmx/01/+/+/1/+/{id}` |
| `MqttInterface.m10592j` | — | 订阅 `airmx/user/{uid}` |
| `MqttAwInterface` | `(id, 1, 0, 1)` | `airwater/01/1/0/1/{id}` |
| `MqttAwInterface` | `m10720b(id)` | 订阅 `airwater/01/+/1/+/{id}` |
| `MqttFanInterface` | — | `fan/01/1/0/1/{id}` |
| `MqttFanInterface` | — | 订阅 `fan/01/+/1/+/{id}` |

## 为什么设备上报是 `airmx/01/0/1/1/1/{id}`

对照 `m10722d(id)` 生成的订阅模板 `airmx/01/+/+/1/+/{id}`：

```
airmx/01/ + / + / 1 / + / {id}
airmx/01/ 0 / 1 / 1 / 1 / {id}
          ↑   ↑   ↑   ↑
          F1  F2  F3=1  F4      ← F3 = 1，方向位为"设备→App"，匹配
```

这也从侧面印证了 `F3` 是方向位。

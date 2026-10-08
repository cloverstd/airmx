# 06 · 指令总表（cmdId）

> 来源：反编译 `mqtt/msg/` 下全部消息类 + `mqtt/C2076f.java`（报文分发）
> 成熟度：✅ 实测的只有 40 / 100 / 210 三条；其余为 📖 反编译

## 总表

| cmdId | name | 方向 | 产品线 | 实现类 | 见 |
|---|---|---|---|---|---|
| **20** | — | 设备→App | Eagle | `MqttOnlineStatus` | [08](08-enums.md) |
| **30** | — | 服务端→App | 全部 | `MqttAppMsg` | [07](07-fields.md#mqttappmsg--cmdid-30) |
| **40** | `instantPush` | App→设备 | Snow（走 Eagle 连接） | `MqttReportInterval` | [07](07-fields.md#mqttreportinterval--cmdid-40--1008--2008) |
| **41** | `delBind` | App→设备 | Snow（走 Eagle 连接） | `MqttSnowUnbind` | [07](07-fields.md#mqttsnowunbind--cmdid-41) |
| **100** | `control` | 双向 | Eagle | `MqttEagleControl` | [07](07-fields.md#mqtteaglecontrol--cmdid-100) |
| **110** | `eagleSet` | App→设备 | Eagle | `MqttEagleSetting` | [07](07-fields.md#mqtteaglesetting--cmdid-110--211) |
| **200** | — | 设备→App | Snow | `MqttSnowReport` | [07](07-fields.md#mqtteaglesetting--cmdid-110--211) |
| **210** | — | 设备→App | Eagle | `MqttEagleStatusReport` | [07](07-fields.md#mqtteaglestatusreport--cmdid-210) |
| **211** | 由设备给出 | 设备→App | Eagle | `MqttEagleSetting` | [07](07-fields.md#mqtteaglesetting--cmdid-110--211) |
| **1000** | `awControl` | 双向 | AirWater | `MqttAwControl` | [07](07-fields.md#加湿器-airwater) |
| **1001** | `awStatus` | 设备→App | AirWater | `MqttAwReport` | [07](07-fields.md#加湿器-airwater) |
| **1003** | `awSet` | 双向 | AirWater | `MqttAwSetting` | [07](07-fields.md#加湿器-airwater) |
| **1004** | `awDelay` | App→设备 | AirWater | `MqttAwDelayPowerOff` | [07](07-fields.md#加湿器-airwater) |
| **1005** | `wetFilm` | App→设备 | AirWater | `MqttAwWetFilm` | [07](07-fields.md#加湿器-airwater) |
| **1006** | — | 服务端→App | AirWater | （复用 `MqttAppMsg`） | [08](08-enums.md) |
| **1008** | `awInstantPush` | App→设备 | AirWater | `MqttReportInterval` | [07](07-fields.md#mqttreportinterval--cmdid-40--1008--2008) |
| **1009** | — | 设备→App | AirWater | `MqttOnlineStatus` | [08](08-enums.md) |
| **1011** | `sterilization` | App→设备 | AirWater | `MqttAwSterilization` | [07](07-fields.md#加湿器-airwater) |
| **1012** | `sterilizationInfo` | 设备→App | AirWater | `MqttAwSterilizationState` | [07](07-fields.md#加湿器-airwater) |
| **2000** | `fanControl` | 双向 | Fan | `MqttFanControl` | [07](07-fields.md#风扇-fan) |
| **2001** | `fanStatus` | 设备→App | Fan | `MqttFanReport` | [07](07-fields.md#风扇-fan) |
| **2003** | `fanSet` | 双向 | Fan | `MqttFanSetting` | [07](07-fields.md#风扇-fan) |
| **2004** | `fanDelay` | App→设备 | Fan | `MqttFanDelayPowerOff` | [07](07-fields.md#风扇-fan) |
| **2006** | — | 服务端→App | Eagle / Fan | （复用 `MqttAppMsg`） | [08](08-enums.md) |
| **2008** | `fanInstantPush` | App→设备 | Fan | `MqttReportInterval` | [07](07-fields.md#mqttreportinterval--cmdid-40--1008--2008) |
| **2009** | — | 设备→App | Fan | `MqttOnlineStatus` | [08](08-enums.md) |

## 编号有规律

```
Eagle (新风机)        100 区间段：100, 110, 210, 211
Snow  (检测仪)         小号段：  20, 30, 40, 41, 200
AirWater (加湿器)     1000 段：  1000–1012
Fan (风扇)            2000 段：  2000–2009
```

设计得很整齐，看 cmdId 就能猜出是哪条产品线。

## 服务端推送跳过验签

`cmdId ∈ {30, 1006, 2006}` 的消息**不做签名校验**（它们是服务器主动推来的
通知类消息）。各产品线的跳过集合略有差异，详见 [05-signing.md §四.4](05-signing.md)。

## 自己下发指令的最小集合

如果你只想让新风机重新可控，需要实现的只有两条：

| cmdId | 用途 | 必需 |
|---|---|---|
| **40** `instantPush` | 催设备立即上报，避免干等 300 秒 | 强烈建议 |
| **100** `control` | 开关机、风量、模式、电辅热、降噪 | **必需** |

以及解析设备发来的：

| cmdId | 用途 |
|---|---|
| **210** | 设备状态上报（风量、转速、滤芯寿命、温度……） |
| **20** | 设备在线状态 |

## 已实测确认可用的两条

```jsonc
// 下发控制
// topic: airmx/01/1/1/0/1/{deviceId}
{"cmdId":100,"name":"control","time":<秒>,"from":4,
 "data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0},
 "sig":"<MD5>"}

// 请求立即上报
// topic: airmx/01/0/1/0/1/{deviceId}
{"cmdId":40,"name":"instantPush","time":<秒>,"from":4,"type":"10000",
 "data":{"frequencyTime":2,"durationTime":300},
 "sig":"<MD5>"}
```

> 注意两条的 topic 不同，这是最容易踩的坑，见 [04-topics.md](04-topics.md#️-最容易踩的坑)。

## 反编译中能看到的其他指令

以下指令在反编译代码里能看到完整定义，但**本项目未在实机验证过**：

- `41` `delBind` — 解绑检测仪
- `110` `eagleSet` — 下发定时器、AI 阈值等设置（⚠️ 涉及风机的持久化配置，误用可能改变设备行为）
- `1004` / `2004` — 加湿器 / 风扇的延时关机
- `1005` `wetFilm` — 重置加湿器湿帘寿命
- `1011` `sterilization` — 加湿器电解杀菌

字段定义见 [07-fields.md](07-fields.md)。

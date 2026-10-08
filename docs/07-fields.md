# 07 · 字段参考

各指令 `data` 部分的逐字段明细。

> 成熟度：**加粗的**为 ✅ 实测；其余为 📖 反编译。
> 标「❓未确认」表示反编译代码里找不到消费该字段的逻辑，无法推断语义。

---

## 新风机 / 检测仪（Eagle / Snow）

### `MqttEagleControl` — cmdId 100

**方向**：双向（App→设备实际使用）
**name**：`control`

| 字段 | 类型 | 说明 |
|---|---|---|
| **`power`** | int | 电源：`1` 开 / `0` 关 |
| **`heatStatus`** | int | 电辅热：`1` 开 / `0` 关 |
| **`mode`** | int | 运行模式，见 [08-enums.md](08-enums.md) |
| **`cadr`** | int | 目标风量 `0`–`100` |
| **`denoise`** | int | 降噪：`1` 开 / `0` 关 |

发送与解析的字段完全一致（`getDataJsonObject()` 与 `parseJsonData()` 对称）。

**设备会回一条 cmdId 210 作为回执。**

### `MqttEagleStatusReport` — cmdId 210

**方向**：设备→App
**name**：`eagleStatus`

| 字段 | 类型 | 说明 |
|---|---|---|
| **`version`** | string | 固件版本，实测为 `"10.00.17"` |
| **`power`** | int | 电源 |
| **`heatStatus`** | int | 电辅热 |
| **`mode`** | int | 模式 |
| **`cadr`** | float | 当前风量百分比（解析时用 `optInt`，小数被截断） |
| **`prm`** | int | 风机实际转速 RPM（✅ 实测，控制后有几秒延迟才反映真实值） |
| **`denoise`** | int | 降噪 |
| **`g4Id`** / `g4Time` / **`g4Percent`** | string / long / int | G4 滤网：编号 / 剩余时间 / 剩余百分比 |
| **`hepaId`** / `hepaTime` / **`hepaPercent`** | 同上 | HEPA 滤网 |
| **`carbonId`** / `carbonTime` / **`carbonPercent`** | 同上 | 活性炭滤网 |
| **`diffPressure1`** / **`diffPressure2`** | int | 压差传感器读数（实测上报 `99999`，疑似"无效值"哨兵）❓ |
| **`t0`** | int | 进风温度（℃） |
| `h0` / `t1` / `t2` | int | ❓未确认（疑似其它温度/湿度传感器） |
| `status` | int | ❓未确认（代码里只透传，无任何数值判断逻辑） |

> `Percent` 三个字段的 getter 会强制夹到 `0`–`100`。
> 百分比的语义：`0` = 全新，`100` = 该换了。

### `MqttEagleSetting` — cmdId 110 / 211

**方向**：App→设备用 110，设备→App 用 211
**name**：`eagleSet`

⚠️ 这条指令会**持久化**到设备（定时器、AI 阈值等），误用可能改变设备行为。
本项目未在实机验证。

| 字段 | 说明 |
|---|---|
| `fanTimer` / `fanStart` / `fanStop` / `fanRepeat` | 风机定时器 |
| `heatTimer` / `heatStart` / `heatStop` / `heatRepeat` | 电辅热定时器 |
| `silentTimer` / `silentStart` / `silentStop` / `silentRepeat` | 静音定时器 |
| `co2Threshold` | CO₂ 阈值。App 写入 `(进度+60)*10`，单位 ppm×10 |
| `pm25Threshold` | PM2.5 阈值。App 写入 `进度+2`，单位 μg/m³ |
| `cadrThreshold` | 风量阈值。App 写入 `((进度+5)*10/550)*100`，百分比 |
| `pm25Avg` / `pm10Avg` | 默认 `50` / `80` |
| `pm25HepaFactor` / `pm25G4Factor` | 默认 `70` |
| `pm10HepaFactor` / `pm10G4Factor` | 默认 `160` |
| `reportInterval` | 默认 `0`（App 未设置） |
| `logLevel` | 默认 `0`（App 未设置） |

**两个关键行为**：

1. **条件发送**：三个定时器字段只在 `xxxTimer != -1` 时才输出。
   构造函数把三者初始化为 `-1`，因此**不设置定时器时这些字段根本不出现**。

2. **`Repeat` 恒为 127**：`getDataJsonObject()` 里硬编码
   ```java
   jSONObject.putOpt("fanRepeat", 127);   // 疑似"周一~周日全选"的位掩码 ❓
   ```
   无论你怎么设，发出去都是 127。

**解析不对称**：`parseJsonData()`（接收 211 时）只读前 12 个 timer 字段
+ `reportInterval` / `logLevel` / `co2Threshold` / `pm25Threshold` / `cadrThreshold`，
**PM 因子和 Avg 会被忽略**。

### `MqttSnowReport` — cmdId 200

**方向**：设备→App（检测仪上报）

| 字段 | 说明 |
|---|---|
| `time` | 上报时刻（也会写进接收侧的 `statusTime`） |
| `version` | 固件版本 |
| `pm10` / `pm25` / `pm100` | PM 值 |
| `co2` | CO₂ |
| `t` | 温度 |
| `h` | 湿度 |
| `ot` / `oh` | 室外温度 / 湿度（`o` 前缀推断）❓ |
| `opm25` | 室外 PM2.5 |
| `status` | ❓未确认 |

> 字段名就是单字母 `t` / `h`，源码里用了混淆常量 `C3156ai.f14779aF`，
> 其字面值为 `"t"`。

### `MqttReportInterval` — cmdId 40 / 1008 / 2008

**方向**：App→设备
请求设备提高上报频率。

| 工厂方法 | cmdId | name | 目标 |
|---|---|---|---|
| `createSnowReportInterval` | **40** | `instantPush` | Snow |
| `createAwReportInterval` | **1008** | `awInstantPush` | AirWater |
| `createFanReportInterval` | **2008** | `fanInstantPush` | Fan |

| 字段 | 说明 |
|---|---|
| **`frequencyTime`** | 上报间隔（秒）。App 实际值：Snow=`2`，AW/Fan=`5` |
| **`durationTime`** | 持续时长（秒）。App 实际值三家均为 `300` |
| `waterType` | **仅当 `> 0` 时输出**（AirWater 专有），取值 = `Device.waterType` |
| `cleanTime` | 随 `waterType > 0` 一起输出；`waterType==1` 取 `cleanTime1`，否则 `cleanTime2` |

> ⚠️ `durationTime` 的语义有个坑：`openairmx/airmx` 的示例用的是
> `make(2, 1)`，即 `durationTime=1`——**只上报 1 秒，等于没有效果**。
> 官方 App 用的是 300。如果你想立刻看到状态，请用 `durationTime=300`。

### `MqttSnowUnbind` — cmdId 41

**方向**：App→设备
**name**：`delBind`
**`type` 固定为 `10002`**

`data` 是空对象 `{}`。用于 App 解绑检测仪。

### `MqttOnlineStatus` — cmdId 20 / 1009 / 2009

**方向**：设备→App

⚠️ 注意字段在 **`data` 内部**，不是顶层：

| 字段 | 说明 |
|---|---|
| `type` | 在线对象类别：`1` = 设备本体，`2` = 另一对象 |
| `id` | 设备 ID |
| `online` | `1` = 在线，`0` = 离线 |
| `time` | 状态时间 |

> 这个类的 `getType()` / `setType()` 覆写指向的是 `data.type`，
> 与基类顶层的 `type` 是**两个不同的字段**。

### `MqttAppMsg` — cmdId 30

**方向**：服务端→App（推送通知）
`getDataJsonObject()` 返回 `null`（只收不发）。

| 字段 | 说明 |
|---|---|
| `title` | 推送标题 |
| `message` | 推送正文 |
| `isShow` / `isRefresh` / `isRemind` | ❓未确认（代码里无消费逻辑） |

### 旧版 Snow 报文（无 `cmdId`）

如果收到的 JSON **不含 `cmdId`**，会走一条特殊的解析分支（不验签）：

```json
{
  "timestamp": 1700000000,
  "sensorData": [
    {
      "humidity":    { "value": 45.2 },
      "temperature": { "value": 23.5 },
      "pm10":  { "value": 12 },
      "pm25":  { "value": 8 },
      "co2":   { "value": 620 }
    }
  ]
}
```

只取 `sensorData[0]`。`humidity` 和 `temperature` 会 **×100** 后存入内部分钟值。
这是 Eagle 主机代传检测仪数据的旧格式。

---

## 加湿器 AirWater

> ⚠️ 以下全部为 📖 反编译，**未在实机验证**。

### `MqttAwControl` — cmdId 1000

**name**：`awControl`

| 字段 | 说明 |
|---|---|
| `power` | 电源 |
| `mode` | 模式，见 [08-enums.md](08-enums.md) |
| `cadr` | 风量 |
| `waterflood` | 上水（UI 里取值翻转：`getWaterflood()==1 ? 0 : 1`） |
| `lock` | 童锁 |
| `anion` | 负离子（构造默认 `1`） |

### `MqttAwReport` — cmdId 1001

**name**：`awStatus`
只收不发（`getDataJsonObject()` 返回 `null`）。

| 字段 | 说明 |
|---|---|
| `version` | 固件版本（代码里与 `"00.00.43"` 做 `compareTo` 判断） |
| `power` / `mode` / `cadr` / `lock` | 同控制 |
| `waterflood` / `water` | 上水状态 / 水量 |
| `powerHeatStatus` | 电加热状态 ❓ |
| `h0` / `h` | 湿度原始值 / 显示值。`h == 99999` 或 `gooseOnline != 1` 时回退到 `h0` |
| `t0` / `t` | 温度原始值 / 显示值。`t == 99999` 时回退到 `t0` |
| `wetFilm` | 湿帘寿命百分比 |
| `gooseOnline` | 外置检测模块在线 ❓（"goose" 具体所指未确认） |
| `delayOffTimer` | 延时关机 |
| `hThreshold` | 目标湿度阈值（默认 `45`） |
| `isNeedClean` | 是否需要清洁 |
| `anion` / `nightLight` | 负离子 / 夜灯 |
| `electrolysis` / `electrolysisStatus` / `electrolysisLevel` | 电解杀菌及档位 ❓ |
| `bleSignal` / `wifiSignal` | 信号强度 |
| `WUD` / `uv` | ❓未确认（`WUD` 与 `7` 比较，疑似换水周期） |
| `cleanNotify` | ⚠️ **代码 bug，永远解析不到**，见 [11-pitfalls.md](11-pitfalls.md) |

### 其它加湿器指令

| cmdId | name | 字段 |
|---|---|---|
| `1003` | `awSet` | 与 Eagle 的定时器类似，但**无条件发送全部定时字段**，`repeat` 同样硬编码 127。默认值：`hThreshold=45`、`powerHeat=1`、`pirLock=1`、`strongModeThreshold=70`、`autoShakeEnable=1`、`reportInterval=300`、`logLevel=2` |
| `1004` | `awDelay` | `delayOffTimer`（默认 `-1`） |
| `1005` | `wetFilm` | `percent`（重置湿帘寿命时发 `100`） |
| `1011` | `sterilization` | `time`、`date`、`status` |
| `1012` | `sterilizationInfo` | `time`、`date`、`state`（`1`/`2`/`3` 对应 UI 步骤，`4` = 深度杀菌被中断） |

---

## 风扇 Fan

> ⚠️ 以下全部为 📖 反编译，**未在实机验证**。

### `MqttFanControl` — cmdId 2000

**name**：`fanControl`

| 字段 | 说明 |
|---|---|
| `power` / `mode` / `cadr` | 电源 / 模式 / 风量 |
| `shake` | 摇头 |
| `lock` / `anion` | 童锁 / 负离子 |
| `level` | **仅 C6 机型**：自然风档位 |
| `nod` | **仅 C6 机型**：点头 |
| `cold` | **仅非 C6 机型**：冷暖（`1` = 冷） |

> `isC6` 由 App 本地判定（发送前 `setC6(true/false)`），**不是协议字段**。

### `MqttFanReport` — cmdId 2001

**name**：`fanStatus`
只收不发。

`version`、`power`、`mode`、`cadr`、`shake`、`cold`、`anion`、`lock`、
`t`（温度）、`ot`（室外温度，getter 叫 `getT0()` 但 JSON key 是 `ot`）、
`sleepCurveStatus`（`1` = 睡眠曲线生效）、`delayOffTimer`、`dateTimer`、`level`、`nod`

### `MqttFanSetting` — cmdId 2003

**name**：`fanSet`

默认值：`aimt=21`、`aimotLevel=5`、`shakeAngle=120`、`autoLightOff=0`、
`sleepMaxCadr=72`、`sleepT1=21`、`sleepT2=22`、`sleepT3=26`

- **仅非 C6**：`aimt`、`aimotLevel`、`lampOffTimer`、`sleepCurve`、
  `sleepFirstStageStart/End/T`、`sleepSecondStage*`、`sleepThirdStage*`
- **始终发送**：`shakeAngle`、`autoLightOff`

> ⚠️ JSON 键名与字段名不一致：`sleepFirstStageStart` ↔ `sleepStart1`、
> `sleepFirstStageT` ↔ `sleepT1`，以此类推。

### `MqttFanDelayPowerOff` — cmdId 2004

**name**：`fanDelay`
字段：`delayOffTimer`（默认 `-1`）、`dateTimer`（发送时写入当前秒）

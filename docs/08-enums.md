# 08 · 枚举值与约定

> 来源：反编译全库搜索结果汇总
> 成熟度：**加粗**为 ✅ 实测，其余为 📖 反编译

## `mode` — 运行模式

**三条产品线的取值不一样**，不要混用。

### 新风机 Eagle

| 值 | 含义 | 依据 |
|---|---|---|
| **0** | **手动**（手动调风量时 `setMode(0)`） | ✅ |
| **1** | **AI**（按检测仪数据自动调速） | ✅ |
| **2** | **静音** | ✅ |
| **3** | **强力**（配合 `cadr=100`） | ✅ |

> 记忆点：0 手动 / 1 AI / 2 静音 / 3 强力。与 `openairmx/airmx` 的
> `EagleMode` 枚举一致。

### 加湿器 AirWater

| 值 | 含义 |
|---|---|
| 0 | 手动 |
| 1 | AI |
| 2 | 睡眠 |
| 4 | 清洁中（点击时若已是 4，提示"正在清洁，完成后会自动关闭"） |
| 5 | 水量不足（提示"水量不足，请加水后重试"） |
| 6 | 睡眠（另一机型，`airWaterType != 21` 时映射到睡眠按钮） |

### 风扇 Fan

| 值 | 含义 |
|---|---|
| 1 | AI |
| 2 | 睡眠 |

## 开关类字段

| 字段 | 取值 |
|---|---|
| **`power`** | `1` 开 / `0` 关 |
| **`heatStatus`** / `powerHeat` / `powerHeatStatus` | `1` 开 / `0` 关 |
| **`denoise`** | `1` 开 / `0` 关 |
| `lock` | `1` 童锁开 / `0` 关 |
| `anion` | 负离子（构造默认 `1`） |
| `shake` / `nod` | `1` 开 / `0` 关 |
| `cold` | `1` 冷风 / `0` 热风 |
| `waterflood` | `1` 上水中（UI 取值翻转） |
| `autoShakeEnable` / `pirLock` | ❓（默认 `1`） |
| `autoLightOff` / `nightLight` | ❓（默认 `0`） |
| `electrolysis` / `electrolysisLevel` / `electrolysisStatus` | ❓ |

## `from` — 消息来源

| 值 | 来源 |
|---|---|
| **1** | **设备**（检测仪上报时显式 `setFrom(1)`） |
| **2** | **新风机 Eagle**（✅ 实测：设备上报带 `"from":2`） |
| 3 | App（iOS） |
| **4** | **App（Android）**（App 发出的所有报文默认值） |

> 实测确认：App 发出的控制/请求报文 `from` = `4`；新风机上报 `from` = `2`。
> `openairmx/airmx` 的 `MessageSource` 枚举里还有 `Snow = 1`、`Eagle = 2`。

## 顶层 `type` 字段

见 [05-signing.md](05-signing.md#type-字段的两个特殊之处) —— 它是**字符串**，
且仅在非 0 时出现。

| 值 | 出现位置 | 含义 |
|---|---|---|
| `10000` | `MqttReportInterval`（Snow 的 `instantPush`） | 由 `MqttManager.m10647w() == 2` 决定，该值来自 `EagleHasBindSnow.getDevType()`。**表示绑定的检测仪设备类型为 2** ❓ 具体型号对应关系未确认 |
| `10002` | `MqttSnowUnbind`（`delBind`） | 解绑标识 ❓ 为何用该值未确认 |

> `ByteBufferUtils.ERROR_CODE = 10000` —— App 是借用这个无关常量当魔法数字用的，
> 名字具有误导性。

## 哨兵值

| 值 | 出现字段 | 含义 |
|---|---|---|
| `99999` | `h` / `t`（加湿器）、`diffPressure1` / `diffPressure2`（新风机） | **无效 / 未采集**。加湿器的 getter 会回退到 `h0` / `t0` |
| `-1` | `delayOffTimer`、`h0`、`t0`、三个定时器字段 | 无效初始值 |
| `127` | 各 `xxxRepeat` | 定时器重复位掩码，疑似"周一~周日全选" ❓，代码里**硬编码** |

## 滤芯寿命百分比

| 字段 | 范围 |
|---|---|
| `g4Percent` / `hepaPercent` / `carbonPercent` | `0`–`100`（getter 强制夹取） |
| `wetFilm`（加湿器湿帘） | `0`–`100`，重置时发 `100` |

语义：**`0` = 全新，`100` = 该换了**。

## 连接状态枚举 `MqttConnStatus`

`CONNECTED` / `CONNECTING` / `CONNECT_FAIL` / `DISCONNECTED`

纯本地回调，不上线，不参与协议。

## 设备型号相关（影响报文内容，但不是协议字段）

App 会在本地判断机型，决定发哪些字段：

| 字段 | 取值 |
|---|---|
| `Device.deviceType` | `1` = AIRMX Pro，`2` / `20` = AirWater，`3` = Air Flow / Air Heater |
| `Device.airWaterType` | `10` / `11` = A5，`20` = Air Flow，`21` = Air Heater，`30` = A2 |
| `isC6`（风扇） | 本地布尔值，决定发 `level`/`nod` 还是 `cold` |

`MqttManager.m10646v()`（检测仪绑定态）：`0` = 已绑定、`1` = 已绑定（另一分支）、
`2` = 未绑定；初始值 `2`。

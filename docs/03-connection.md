# 03 · 连接与鉴权

> 来源：官方 App v3.1.3 反编译（`mqtt/eagle/MqttInterface.java`、
> `mqtt/airwater/MqttAwInterface.java`、`mqtt/fan/MqttFanInterface.java`）
> 依据片段见 [`reference/java/MqttInterface.connection.java`](../reference/java/MqttInterface.connection.java)

## 三条独立的连接

App 针对三条产品线各建了一条 MQTT 连接，参数互不共用：

| | 新风机 / 检测仪（Eagle / Snow） | 加湿器（AirWater） | 风扇（Fan） |
|---|---|---|---|
| broker | `tcp://mqtt.airmx.cn:1883` | `tcp://awm.airmx.cn:1883` | `tcp://fanmq.airmx.cn:1883` |
| clientId 前缀 | `amx_` | `aaw_` | `afan_` |
| clientId 总长 | 23 字符 | 23 字符 | 23 字符 |
| username | `app_{uid}` | `app_{uid}` | `app_{uid}` |
| password | 登录 token | 登录 token | 登录 token |
| cleanSession | `false` | `false` | `false` |
| connectionTimeout | 10 秒 | 10 秒 | 10 秒 |
| keepAlive | 20 秒 | 20 秒 | 20 秒 |
| 发布 QoS | 1 | 1 | 1 |
| 发布 retain | false | false | false |

非线上环境统一回落到 `123.206.27.237:1883`（三个域名共用同一个 IP）。
依据：[`reference/java/MqttConstans.java`](../reference/java/MqttConstans.java)

## clientId 是怎么来的

```
amx_ + <设备标识的前 19 位>
```

这里的"设备标识"**不是新风机自己的 ID**，而是**手机自己的 UUID**。算法
（`p133n/p134a/C2096g.java`，`DeviceUuidFactory`）：

```
androidId = Settings.Secure.getString(cr, "android_id")
若 androidId == "9774d56d682e549c"（模拟器默认值）→ 视为 null

deviceUuid = UUID.nameUUIDFromBytes(androidId.getBytes("utf8"))
                 .toString()
                 .replaceAll("-", "")           // 32 位小写 hex

缓存到 SharedPreferences 文件 "device_id.xml"，key = "device_id"
```

> 💡 也就是说 clientId 只用来在 broker 上区分**不同的手机**，跟控制哪台设备无关。
> 你自己写客户端时随便取一个唯一的 clientId 就行。
>
> ⚠️ 但要注意：clientId 相同会互相顶掉连接。多开脚本时记得让 clientId 唯一。

## username / password ≠ 签名的 key

这是最容易混淆的一点，务必分清：

| | 用途 | 来源 |
|---|---|---|
| `username` = `app_{uid}`、`password` = `{token}` | **登录 broker 的凭据** | App 登录接口返回的 `UserLoggedInfo` |
| `device key`（32 位 hex） | **给控制指令签名**，决定你能不能控制设备 | 设备配网时上报，见 [09](09-device-key.md) |

线上环境这两者都需要。但**现在自建的 broker 一般直接放开匿名连接**
（`allow_anonymous true`），username / password 就用不上了。

真正决定能不能控制设备的是 **device key**——没有它你只能"看着"设备上报状态，
发出去的指令不会被执行。

## 订阅与发布

| 动作 | Topic |
|---|---|
| 订阅（用户级推送） | `airmx/user/{uid}` |
| 订阅（设备状态） | `airmx/01/+/+/1/+/{deviceId}` |
| 发布（控制） | `airmx/01/1/1/0/1/{deviceId}` |
| 发布（请求上报） | `airmx/01/0/1/0/1/{deviceId}` |

详细含义见 [04-topics.md](04-topics.md)。

## 断线重连与保活

App 有两套机制：

**1. 断线重连**（`connectionLost` 回调）

```
连接丢失 → 1 秒后重连（仅在有网络时）
```

**2. AlarmManager 保活**（`MqttService`）

```
每 5 分钟（300000 ms）拉起一次 Service，检查连接并重连
action: com.caiyungui.xinfeng.mqttEagle.keep_alive    （三条线各自的 action 不同）
```

设备侧也有类似机制——所以配网完成后即使你重启了 broker，设备也会自己连回来。

## 设备的上报节奏

| 场景 | 间隔 |
|---|---|
| 空闲 | **每 300 秒**一次 |
| 收到 `instantPush` 之后 | 按请求里的 `frequencyTime`（App 用 2 秒） |

> ⚠️ 写脚本时要注意这个：**设备空闲时可能要等 5 分钟才上报一次**。
> 如果你的代码连上就干等状态，很可能误判成"设备离线"。
> 正确做法是先发一条 `instantPush`（见 [06-commands.md](06-commands.md)）。

## 自己实现时的建议

如果你要写自己的客户端：

1. **broker 直接连你自己的**，不需要 username / password
2. **clientId 取一个唯一值**，避免多个客户端互踢
3. **订阅 `airmx/01/+/+/1/1/{deviceId}`** 收状态（比 App 的 `+/+/1/+/{id}` 更精确）
4. **发布前一定要签名**，见 [05-signing.md](05-signing.md)
5. **别指望设备秒回**，先用 `instantPush` 催一次

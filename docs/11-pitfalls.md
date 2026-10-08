# 11 · 已知陷阱与代码级 bug

排查问题时优先看这里。带 🐛 的是反编译代码里真实存在的 bug，
带 ⚠️ 的是使用协议时必须注意的行为。

---

## 签名相关

### ⚠️ `sig` 必须是 JSON 的最后一个键

接收侧靠 `indexOf("sig")` 定位切片位置。如果 `sig` 后面还有内容，
切出来的原文就不对，验签必然失败。

自研实现时建议改用**查找最后一个 `,"sig":`**，比 `indexOf("sig")` 健壮得多。

### ⚠️ `indexOf("sig")` 会误命中字符串值里的 `sig`

它找的是**子串**而不是键名。如果 `data` 里某个值包含 `sig` 三个字母
（比如字段名叫 `signal`），定位就会错。

### ⚠️ 验签必须用收到的**原始文本**

不能 parse 成对象再重新序列化去验签——空白和键顺序都会变，哪怕语义一致也会失败。

### ⚠️ `cmdId ∈ {30, 1006, 2006}` 跳过验签

这三类是服务端推送。如果你的实现无脑对所有报文验签，收到这类消息会一直报错。

### ⚠️ `time` 是**秒**不是毫秒

`System.currentTimeMillis() / 1000`。

### ⚠️ `type` 字段是**字符串**

`"type":"10000"` 而不是 `"type":10000`。写成数字签名会不一致。

---

## Topic 相关

### ⚠️ 控制与请求上报用的 topic 不同

```
下发控制     airmx/01/1/1/0/1/{id}     ← 第 2 段是 1
请求上报     airmx/01/0/1/0/1/{id}     ← 第 2 段是 0
```

搞错的话设备**静默忽略**——不报错、不回应。极难排查。

### ⚠️ clientId 相同会互相顶掉连接

MQTT 的 clientId 是会话标识，两个客户端用同一个 ID 会互相踢下线。
多开脚本时要保证唯一。

---

## 设备行为相关

### ⚠️ 设备空闲时每 300 秒才上报一次

如果你的代码连上就干等状态，很可能误判成"设备离线"。

**正确做法**：先发一条 `instantPush`（cmdId 40）催它上报。

### ⚠️ `instantPush` 的 `durationTime` 别用 1

`openairmx/airmx` 的示例代码写的是 `make(2, 1)`，即
`frequencyTime=2, durationTime=1` —— **只上报 1 秒，等于没有效果**。

官方 App 用的是 `durationTime=300`。想立刻看到状态就用 300。

### ⚠️ `prm`（转速）的回执有延迟

下发控制后，设备会**立刻**回一条状态报文，但里面的 `cadr` 是**设定值**，
`prm`（实际转速）要过几秒才反映真实值。别以为控制没生效。

### ⚠️ 切换风量会同时改变模式

App 调风量时是 `setPower(1); setMode(0); setCadr(n)` —— 一并把模式切回手动。
如果你只改 `cadr` 不指定 `mode`，设备可能仍按原模式（如 AI）运行，风量不生效。

### ⚠️ `cadr` 的语义在收发两侧不一致

设备上报时是 **float**，App 解析用 `optInt`，**小数被截断**。
自己做回环控制时要注意这个精度损失。

---

## 🐛 反编译代码里的 bug

以下都是官方 App 代码本身的问题，列在这里是因为它们能解释一些"看起来奇怪"的
现象，也提醒重实现者别照抄。

### 🐛 `cleanNotify` 永远解析不到

`MqttAwReport.parseJsonData()` 里判断的键名拼错了一个字母：

```java
if (jSONObject.has("cleanNotiry")) {        // ← 应该是 cleanNotify
    this.cleanNotify = jSONObject.optInt("cleanNotify");
}
```

`cleanNotiry` 这个键永远不会存在，所以 `cleanNotify` 恒为初值 `1`。

### 🐛 `MqttEagleStatusReport` 的 `cadr` 精度丢失

发送侧写 `Float`，解析侧 `optInt` —— 小数被截断。

### 🐛 多个 long 字段被 `optInt` 读取

- `MqttAwSterilization` / `MqttAwSterilizationState` 的 `time` / `date`
- `MqttFanDelayPowerOff` 的 `dateTimer`

时间戳超过约 2038 年会被截断。

### 🐛 `MqttEagleSetting` 覆写 `repeat` 字段

`getDataJsonObject()` 里硬编码：

```java
jSONObject.putOpt("fanRepeat", 127);
jSONObject.putOpt("heatRepeat", 127);
jSONObject.putOpt("silentRepeat", 127);
```

无论调用方设成什么，发出去都是 `127`。`MqttAwSetting` 同样如此。

### 🐛 `MqttAppMsg.getDataJsonObject()` 返回 `null`

发送时 `data` 键不会出现（`org.json` 的 `putOpt(k, null)` 是 no-op），
但接收侧 `parse()` 要求 `data` 必须存在，否则抛异常。
只收不发所以没暴露出来，但重实现时要小心。

### 🐛 `MqttEagleControl.toString()` 打印错误的类名

打印出来是 `MqttEagleSetting`（复制粘贴残留），纯日志问题，不影响协议。

### 🐛 请求上报的 topic 与下发给检测仪的话题不对称

App 把请求上报发到 `airmx/01/0/1/0/1/{snowId}`，但通过蓝牙告诉 Snow
去订阅 `airmx/01/1/0/1/1/{snowId}` —— 三个位置对不上。

可能是固件侧另有约定，也可能是 App 的 bug。**无法从反编译确认**。
好在控制新风机（Eagle）不受影响。

---

## 排障速查

| 症状 | 先查这里 |
|---|---|
| 指令发出去毫无反应 | 1) topic 对不对（[04](04-topics.md)）2) 签名对不对（[05](05-signing.md)）3) key 对不对（[09](09-device-key.md)） |
| 收不到状态 | 设备在线吗？先发 `instantPush`，别干等 300 秒 |
| 收到状态但验签失败 | 你用的原始文本吗？key 对吗？ |
| 改了风量但不生效 | 是不是没把 `mode` 一起切到手动（0）？ |
| 设备没连上自己的 broker | DNS 重映射做了吗？`mqtt.airmx.cn` 解析到哪？ |
| 配网失败 | `i.airmx.cn` 指向模拟服务器了吗？它跑起来了吗？ |

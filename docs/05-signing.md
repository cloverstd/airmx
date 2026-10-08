# 05 · 报文信封与签名算法

> **这是全仓库最关键的一节。** 签名算不对，设备会静默忽略你的所有指令。
>
> 来源：反编译 `mqtt/msg/MqttBaseMessage.java` + `mqtt/eagle/MqttInterface.java`
> 成熟度：✅ 实测（用真实抓包三方交叉验证，并已端到端控制成功）
>
> 依据片段：
> [`MqttBaseMessage.java`](../reference/java/MqttBaseMessage.java)、
> [`MqttInterface.signature-check.java`](../reference/java/MqttInterface.signature-check.java)、
> [`MD5Util.java`](../reference/java/MD5Util.java)

## 一、报文信封

所有报文都是同一套结构：

```json
{
  "cmdId": 100,
  "name":  "control",
  "time":  1700000000,
  "from":  4,
  "type":  "10000",          ← 可选，见下
  "data":  { ... },          ← 各指令不同
  "sig":   "80b161d4..."     ← MD5 签名
}
```

### 字段顺序是协议的一部分

顺序**固定**为：

```
cmdId, name, time, from, [type], data, sig
```

**这不是代码风格问题。** 签名是对 JSON 文本逐字节计算的，顺序变了签名就不匹配。
`org.json` 在 Android 上按插入顺序序列化，所以 App 的构造顺序就是协议顺序。

任何重实现都必须严格按这个顺序输出。

### 各字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `cmdId` | int | 指令编号，见 [06-commands.md](06-commands.md) |
| `name` | string | 指令名，App 发送时由构造函数设置 |
| `time` | long | **秒级**时间戳（`System.currentTimeMillis() / 1000`） |
| `from` | int | 来源：`1`=设备，`4`=App（见 [08-enums.md](08-enums.md)） |
| `type` | **string** | 可选，仅当非 0 时出现，位置在第 5 位 |
| `data` | object | 业务数据，各指令不同，见 [07-fields.md](07-fields.md) |
| `sig` | string | MD5 签名，**必须是最后一个键** |

### `type` 字段的两个特殊之处

1. **它是字符串，不是数字**
   ```java
   jSONObject.putOpt("type", this.type + "");    // 注意 this.type + ""
   ```
   签名时参与计算的是 `"type":"10000"` 而不是 `"type":10000`。

2. **它只在非 0 时出现**
   ```java
   if (this.type != 0) { ... }
   ```
   所以普通控制指令里根本没有这个字段。

目前已知的取值见 [08-enums.md](08-enums.md)。

## 二、签名算法

### 公式

```
sig = MD5( <报文 JSON 去掉首尾花括号、去掉 sig 字段> + "," + deviceKey )
```

一句话：**把除 `sig` 之外的整个 JSON 内容摊平，末尾补一个英文逗号和 deviceKey，
再取 MD5，输出 32 位小写十六进制。**

### 发送侧的实现

```java
// MqttBaseMessage.createPayload()
JSONObject o = createMsgObj();                  // cmdId, name, time, from, [type]
o.putOpt("data", getDataJsonObject());
String json = o.toString();                     // 此时还没有 sig
String sig  = createSign(json.substring(1, json.length() - 1));   // 去掉首尾花括号
o.putOpt("sig", sig);                           // sig 最后才追加

// createSign(str) = MD5(str + "," + deviceKey)
```

### 接收侧的实现

```java
// MqttInterface.messageArrived()
String raw = mqttMessage.toString();            // 收到的【原始文本】
String theirSig = obj.getString("sig");
String mine = MD5(raw.substring(1, raw.indexOf("sig") - 1) + deviceKey);
if (!mine.equals(theirSig)) { 丢弃; }
```

### 两者为什么等价

接收侧的切片看起来不一样，但字节上是完全相同的。设 `sig` 的 `s` 在下标 `S`：

```
下标:  0    1                                              S-2 S-1  S
       {   "cmdId":100, ... ,"data":{...}                  ,   "   sig":"..."   }
       ↑                                                    ↑
     substring(1, ...) 从这里开始                     这里正好是逗号
```

- `indexOf("sig")` = `S`（`s` 的位置）
- `S - 1` 是 `"sig"` 的左引号
- `substring(1, S - 1)` 取 `[1, S-2]`，**包含末尾那个逗号**

所以接收侧算的是 `MD5(去花括号的内容，含尾逗号) + deviceKey)`。

而发送侧 `substring(1, len-1)` 不含尾逗号，但 `createSign` 里补了 `+ ","`。

**两边都等价于 `MD5(inner + "," + deviceKey)`。** ✅

### MD5 实现细节

标准 MD5，输出 32 位小写十六进制，每字节左侧补零（不会丢前导零）。
字符串按平台默认字符集编码（Android 上是 UTF-8）。

见 [`MD5Util.java`](../reference/java/MD5Util.java)。

## 三、完整示例（可自行验算）

以下用**合成占位符 key** `0123456789abcdef0123456789abcdef` 计算，
不是任何真实设备的密钥。完整测试向量见
[`tests/test_vectors.json`](../tests/test_vectors.json)。

### 示例 1：控制指令

签名原文（注意末尾那个逗号是 `createSign` 补的，不在原文里）：

```
"cmdId":100,"name":"control","time":1700000000,"from":4,"data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0}
```

拼接后取 MD5：

```
MD5(上面的字符串 + "," + 0123456789abcdef0123456789abcdef)
  = 80b161d4407168d344d9f2eaab92b230
```

最终发出的报文：

```json
{"cmdId":100,"name":"control","time":1700000000,"from":4,"data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0},"sig":"80b161d4407168d344d9f2eaab92b230"}
```

### 示例 2：带 `type` 的请求上报

```json
{"cmdId":40,"name":"instantPush","time":1700000001,"from":4,"type":"10000","data":{"frequencyTime":2,"durationTime":300},"sig":"4989e4b5aa8fb1859d69298138b1dc0a"}
```

注意 `"type":"10000"` 在 `data` **之前**，且是字符串。

### 示例 3：设备上报（用于验证你收到的报文）

```json
{"cmdId":210,"name":"eagleStatus","time":1700000002,"from":2,"data":{"version":"10.00.17","power":1,"heatStatus":0,"mode":0,"cadr":35,"prm":1020,"g4Percent":69,"hepaPercent":51,"carbonId":"031","g4Id":"041","hepaId":"021","carbonPercent":69,"diffPressure1":99999,"diffPressure2":99999,"t0":27,"status":0,"denoise":1},"sig":"61b0f05305d62a97790cfe346c868bc4"}
```

### 自己验算

```python
import hashlib, json

key = "0123456789abcdef0123456789abcdef"

def sign(body):
    inner = json.dumps(body, separators=(",", ":"), ensure_ascii=False)[1:-1]
    return hashlib.md5((inner + "," + key).encode()).hexdigest()

body = {"cmdId":100,"name":"control","time":1700000000,"from":4,
        "data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0}}
print(sign(body))     # 应输出 80b161d4407168d344d9f2eaab92b230
```

## 四、⚠️ 六个必须注意的坑

### 1. `sig` 必须在最后

接收侧靠 `indexOf("sig")` 定位切片位置。如果 `sig` 不是最后一个键，
后面还有内容，算出来的签名就不对。

### 2. `indexOf("sig")` 找的是**子串**，不是键名

如果 JSON 里更早的地方出现了 `sig` 这三个字母——比如某个字符串值里含有
`sig`、或者 `data` 里有个字段名叫 `signal`——定位就会错，验签必然失败。

**自研实现时建议改用"找最后一个 `,"sig":`"** 而不是 `indexOf("sig")`，
更健壮。

### 3. 校验用的是**原始文本**，不是重新序列化的 JSON

接收侧拿的是 `mqttMessage.toString()`，即收到的原始字节。
**空白、键顺序必须逐字节保持原样。** 如果你把报文 parse 成对象再重新
序列化去验签，哪怕语义一致也会失败。

> 实测中观察到一个有意思的细节：设备发来的状态报文里偶尔会带空格，
> 例如 `{"cmdId": 210,"name":"eagleStatus",...,"g4Percent": 69,...}`。
> 这些空格是设备端序列化器产生的，同样参与签名计算。

### 4. `cmdId ∈ {30, 1006, 2006}` 跳过验签

这三类是服务端推送给 App 的消息，**完全不做签名校验**：

```java
if (optInt != 30 && optInt != 1006 && optInt != 2006) {
    // ... 验签
}
```

各产品线的跳过集合略有差异：

| 产品线 | 跳过验签的 cmdId |
|---|---|
| Eagle / Snow | 30, 1006, 2006 |
| AirWater | 30, 1006 |
| Fan | 30, 1006, 2006 |

### 5. `time` 是秒，不是毫秒

`System.currentTimeMillis() / 1000`。写错的话签名不会失败（因为它是报文内容
的一部分），但设备可能因为时间戳不合理而拒绝执行。

### 6. 没有 key 就只能读，不能写

验签失败时 App 只是打日志然后丢弃。设备侧对 App 发来的指令应该也是同样处理——
**没有正确的 key，你发出去的指令等于石沉大海**，不会有任何错误提示。

这也是为什么"拿到 device key"是整个方案最关键的一步：
见 [09-device-key.md](09-device-key.md)。

## 五、与其他实现的一致性

本仓库的算法与以下实现逐字节一致：

| 实现 | 语言 | 说明 |
|---|---|---|
| [`openairmx/airmx`](https://github.com/openairmx/airmx) | TypeScript | `src/util.ts` 的 `Signer` |
| [`src/airmx.py`](../src/airmx.py) | Python | 本仓库 |

三方交叉验证方式：用同一组 `(报文, key)` 分别计算，得到的 `sig` 完全相同；
再用真实抓包里设备发来的报文反验，也完全匹配。

## 六、需要向设备发指令的最小实现

```python
import hashlib, json, time

def build(cmd_id, name, data, key, msg_type=None):
    body = {"cmdId": cmd_id, "name": name, "time": int(time.time()), "from": 4}
    if msg_type is not None:
        body["type"] = msg_type
    body["data"] = data

    inner = json.dumps(body, separators=(",", ":"), ensure_ascii=False)[1:-1]
    body["sig"] = hashlib.md5((inner + "," + key).encode()).hexdigest()

    return json.dumps(body, separators=(",", ":"), ensure_ascii=False)
```

注意 `separators=(",", ":")` —— 不能有多余空格，否则签名对不上。

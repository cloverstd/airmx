# 10 · 蓝牙（BLE）配网协议

新风机没有屏幕键盘，WiFi 密码只能通过蓝牙传进去。这里记录两套用于不同产品线的
蓝牙协议。

> ⚠️ **来源不同，请分别看待：**
> - A 节是**本次逆向成果**（官方 App 反编译，📖 未实机验证）
> - B 节来自 **MIT 开源项目** [`openairmx/setup`](https://github.com/openairmx/setup)，
>   不是本仓库的逆向成果，版权与许可证按其原仓库
> - C 节补充的加密模式来自 **Apache-2.0 开源项目**
>   [`oladoga/AIRMX-Setup`](https://github.com/oladoga/AIRMX-Setup)

---

## A. 官方 App 对检测仪（Snow）的蓝牙协议

> 来源：反编译 `p131m/C2029i.java`（原始类名 `DanyBleController`）
> 成熟度：📖 反编译

### 特征 UUID

| 用途 | UUID |
|---|---|
| 写入（App→设备） | `0000000D-0000-1000-8000-00805F9B34FB` |
| 通知（设备→App） | `0000000E-0000-1000-8000-00805F9B34FB` |

### 分包规则

- 单包 ≤ **20 字节**
- 首字节 = 本包长度 - 1
- 第 2 字节 = 指令号

### 指令

| 指令 | 用途 | payload |
|---|---|---|
| `1` | 配置 WiFi | `"<ssid>","<password>"`（**带引号**） |
| `27` | 下发 MQTT 参数 | `<mqtt_host> 1883 snow_<deviceId> <deviceKey>` |
| `28` | 下发话题 | `Snow_<deviceId> airmx/01/+/1/+/+/<deviceId> airmx/01/1/0/1/1/<deviceId>` |

> **注意指令 27**：这里把 **device key 当作 MQTT 密码**下发给检测仪。
> 也就是说检测仪的 key 是通过蓝牙交给设备的。
> 这与新风机（Eagle）的 key 由服务器下发的方式不同。

---

## B. AIRMX Pro 的蓝牙配网协议

> 来源：[`openairmx/setup`](https://github.com/openairmx/setup)（MIT），
> 部署在 <https://airmx.pro>
> 成熟度：该项目的实现，本仓库未独立验证

### 设备识别

| 项 | 值 |
|---|---|
| 蓝牙设备名 | `AIRMX Pro` |
| 主服务 | `22210000-554a-4546-5542-46534450464d` |
| 写入特征 | `22210001-554a-4546-5542-46534450464d` |
| 通知特征 | `22210002-554a-4546-5542-46534450464d` |

### 包格式

```
[seq] [cur<<4 | total] [flags] [cmdId] [payload...]
  1B         1B             1B      1B       ≤16B
```

| 字节 | 含义 |
|---|---|
| 0 | 序号，从 1 递增 |
| 1 | 高 4 位 = 当前包序号，低 4 位 = 总包数 |
| 2 | 加密标志（`0x00` = 明文） |
| 3 | 指令号 |
| 4+ | 数据，按 **16 字节**分片 |

> ⚠️ 另一个独立实现（见 C 节）把第 2–3 字节读作**一个大端 16 位整数**
> `cmdField = (type << 14) | (cmdId & 0x3FFF)`。两者其实是同一个字段的
> 两种读法：低 14 位是指令号，高 2 位是类型（0 = 明文，1 = 加密）。
> 当指令号 < 256 且不加密时，两种读法结果一致。

### 指令

| cmdId | 名称 | payload |
|---|---|---|
| `0x0b` (11) | 握手 | `08` + 8 字节 token（全 0）+ `05` + `"1.0.0"` |
| `0x15` (21) | 配置 WiFi | `[ssid_len][ssid][password_len][password]` |
| `0x16` (22) | 注册 | 空 |

### 流程

```
握手(0x0b) ──► 设备回复 ──► 发 WiFi(0x15) ──► 设备回复
    ──► 注册(0x16) ──► 设备回复 payload 首字节 = 4，后 4 字节 = deviceId(uint32)
    ──► 配网页拿 deviceId 去 /exchange 换 key
```

发送每条指令之间需要间隔 **500 ms**。

---

## C. 加密模式与 AES 密钥派生

> 来源：[`oladoga/AIRMX-Setup`](https://github.com/oladoga/AIRMX-Setup)
> （Apache-2.0，面向加湿器 AirWater 系列）
> 成熟度：该项目的实现，本仓库未独立验证

上面 B 节的 `openairmx/setup` 只用了明文模式（flags 恒为 `0x00`）。
另一个面向加湿器的 Python 实现补全了加密模式：

### 密钥派生

**AES 密钥直接来自设备 MAC 地址**：

```python
mac = "98:CD:AC:BB:0D:46"
key = mac.upper().encode("ascii")[:16]      # 取前 16 字节，不足补 0
iv  = key                                    # IV 等于 key 本身
```

即在 Java 里的等价写法：

```java
byte[] key = new byte[16];
System.arraycopy(mac.toUpperCase().getBytes(), 0, key, 0, 16);
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
cipher.init(ENCRYPT_MODE, key, new IvParameterSpec(key));
```

> 注意 17 字符的 MAC（含冒号）截取前 16 字节后正好少一个字符
> （`"98:CD:AC:BB:0D:4"`），这是刻意为之还是巧合尚不明确 ❓

### 何时加密

包头的 `type` 为 `1` 时 payload 用上述 AES-CBC 加密；`type` 为 `0` 时明文。

### 绑定应答里的 MAC

绑定成功时设备返回的 payload **前 6 字节就是原始 MAC**：

```python
mac_bytes = payload[:6]
mac_str = ":".join(f"{b:02X}" for b in mac_bytes)
```

拿到 MAC 后即可按上面的方式派生出 AES 密钥，用于后续的加密通信。

---

## D. 两套协议的关系

| | 官方 App（A 节） | openairmx/setup（B 节） |
|---|---|---|
| 目标设备 | 检测仪 Snow | 新风机 AIRMX Pro |
| 特征 UUID | `0000000D-…` / `0000000E-…` | `22210000-…` 系列 |
| 首字节 | 长度-1 | 序号 |
| 指令号 | 1 / 27 / 28 | 0x0b / 0x15 / 0x16 |
| key 来源 | 由 App 通过蓝牙下发 | 由设备上报给服务器，再从 `/exchange` 取 |

两套协议**不通用**。如果你要给新风机配网，用 B 节的那套。

---

## 参考

| 项目 | 许可证 | 内容 |
|---|---|---|
| [openairmx/setup](https://github.com/openairmx/setup) | MIT | Web Bluetooth 配网页（B 节来源） |
| [oladoga/AIRMX-Setup](https://github.com/oladoga/AIRMX-Setup) | Apache-2.0 | 加湿器 BLE 配网 Python 脚本（C 节来源） |
| [dext0r/airmx](https://github.com/dext0r/airmx) | 无声明 | 加湿器 Home Assistant 集成（**本仓库仅链接，未引用其代码**） |

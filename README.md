# AIRMX 秒新 · 本地控制

[English](README.en.md) | 中文

**厂商已经倒闭，官方服务器全部下线，App 用不了了——但这台机器还能救回来。**

本仓库是秒新 AIRMX 官方 App（v3.1.3）的**完整逆向成果**：MQTT 协议、签名算法、
指令集、以及让设备重新可控的完整链路。附一个已实机验证的 Python 参考客户端。

> ✅ **已验证**：设备重新接入本地 MQTT 后，读取状态、开关机、调风量、
> 切换模式、电辅热、降噪全部可用。测试机型 AIRMX Pro，固件 `10.00.17`。

---

## 快速上手

一共三步。详细版见 [docs/02-architecture.md](docs/02-architecture.md)
和 [docs/09-device-key.md](docs/09-device-key.md)。

### 1. 拿到你的 device key

这是最关键、也曾经是最难的一步。控制指令要签名，而签名需要每台设备唯一的 key。

社区的 [openairmx](https://github.com/openairmx) 已经打通了这条链路：
跑一个 [模拟服务器](https://github.com/openairmx/server) 冒充 `i.airmx.cn`，
然后用 [网页版蓝牙配网工具](https://airmx.pro) 给设备重新配一次网——
**设备会主动把自己的 key 上报给服务器，页面再把它显示给你。**

👉 完整步骤：[docs/09-device-key.md](docs/09-device-key.md)

### 2. 在你的内网架起 MQTT broker

```bash
apt install mosquitto
```

```conf
# /etc/mosquitto/conf.d/local.conf
listener 1883 0.0.0.0
allow_anonymous true
```

然后把 `*.airmx.cn` 在路由器 / DNS 上解析到这台机器：

```conf
# /etc/dnsmasq.d/airmx.conf
address=/airmx.cn/192.168.2.215
```

设备会在几秒内连上来。验证：

```bash
mosquitto_sub -h 192.168.2.215 -t 'airmx/#' -v
# 每 300 秒会看到一条 cmdId:210 的状态上报
```

### 3. 开始控制

```bash
pip install paho-mqtt

export AIRMX_BROKER=mqtt.airmx.cn
export AIRMX_DEVICE_ID=<你的设备ID>
export AIRMX_KEY=<你的device key>

python src/airmx.py status          # 读状态（只读）
python src/airmx.py cadr 60 --yes   # 调风量
python src/airmx.py mode turbo --yes
python src/airmx.py on --yes  /  off --yes
python src/airmx.py heat on --yes  /  denoise off --yes
```

不带 `--yes` 时只显示当前状态，不会动设备。

不确定 key 对不对？跑一遍端到端验证：

```bash
python src/verify_control.py
```

它会读状态 → 改风量 → 确认设备回执 → 还原原值。

---

## 协议速查

### 签名（最关键）

```
sig = MD5( 报文 JSON 去掉首尾花括号与 sig 字段 + "," + deviceKey )
```

```python
inner = json.dumps(body, separators=(",", ":"))[1:-1]   # body 不含 sig
sig = hashlib.md5((inner + "," + key).encode()).hexdigest()
```

字段顺序固定为 `cmdId, name, time, from, [type], data, sig` ——
**顺序是协议的一部分，变了签名就对不上。**

👉 完整说明：[docs/05-signing.md](docs/05-signing.md)

### Topic

| 用途 | Topic |
|---|---|
| 下发控制 | `airmx/01/1/1/0/1/{deviceId}` |
| 请求立即上报 | `airmx/01/0/1/0/1/{deviceId}` |
| 设备状态上报 | `airmx/01/0/1/1/1/{deviceId}` |
| App 订阅 | `airmx/01/+/+/1/+/{deviceId}` |

> ⚠️ 控制和请求上报的 topic **不一样**，搞错的话设备会静默忽略。

### 控制报文

```json
{"cmdId":100,"name":"control","time":1700000000,"from":4,
 "data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0},
 "sig":"80b161d4407168d344d9f2eaab92b230"}
```

| 字段 | 取值 |
|---|---|
| `power` | 0 关 / 1 开 |
| `mode` | 0 手动 / 1 AI / 2 静音 / 3 强力 |
| `cadr` | 0–100 风量百分比 |
| `denoise` | 0 关 / 1 开 |
| `heatStatus` | 0 关 / 1 开 |

---

## 文档

完整索引见 **[docs/README.md](docs/README.md)**，两条入门路径：

**「我想让机器重新能用」**
[01 背景](docs/01-background.md) → [09 拿 key](docs/09-device-key.md) →
[02 架构](docs/02-architecture.md) → 用 [src/airmx.py](src/airmx.py)

**「我要自己写客户端 / HA 集成 / 固件」**
[03 连接](docs/03-connection.md) → [04 Topic](docs/04-topics.md) →
[05 签名](docs/05-signing.md) → [06 指令表](docs/06-commands.md) →
[07 字段](docs/07-fields.md) → [08 枚举](docs/08-enums.md) → [11 坑](docs/11-pitfalls.md)

每篇文档都标了成熟度：✅ 实测 / 📖 反编译 / ❓ 推断。
本仓库的实测集中在**新风机**，加湿器和风扇基本只有 📖 级别信息。

### 配套材料

| 位置 | 内容 |
|---|---|
| [src/airmx.py](src/airmx.py) | Python 参考客户端 + CLI |
| [src/verify_control.py](src/verify_control.py) | 端到端验证脚本 |
| [reference/java/](reference/java/) | 关键反编译片段（签名、Topic、连接参数） |
| [tests/test_vectors.json](tests/test_vectors.json) | 签名测试向量，可验证你的实现 |
| [examples/config.example.toml](examples/config.example.toml) | 配置样例 |

---

## ⚠️ 安全提醒

1. **device key 等同于设备的控制权。** 不要提交到仓库、不要贴到 Issue 或论坛。
   本仓库所有示例一律使用占位符。
2. **目前没有更换 key 的方法**——请把它当作**不可撤销的凭据**。
3. **不要把模拟服务器暴露到公网。** 它的 `/exchange?device={id}` 接口
   **没有任何鉴权**，谁能访问谁就能取走 key。
4. **MQTT broker 也别暴露到公网。** 设备是明文连接、无认证的。

更多见 [NOTICE.md](NOTICE.md)。

---

## 生态与致谢

本项目的 device key 获取链路**完全建立在 [openairmx](https://github.com/openairmx)
的工作之上**。本仓库是在他们打通"拿到 key"这一段之后，继续把 MQTT 协议层挖透
并整理成文档。

| 项目 | 许可证 | 作用 | 关系 |
|---|---|---|---|
| [openairmx/server](https://github.com/openairmx/server) | MIT | 模拟 `i.airmx.cn` 的 Node.js 服务 | **前置**：接收并保管设备上报的 key |
| [openairmx/setup](https://github.com/openairmx/setup) | MIT | Web Bluetooth 配网页（<https://airmx.pro>） | **前置**：完成配网并显示 key |
| [openairmx/airmx](https://github.com/openairmx/airmx) | MIT | TypeScript MQTT 客户端 | 独立实现，签名算法与本仓库互相印证 |
| [openairmx/homebridge-airmx](https://github.com/openairmx/homebridge-airmx) | MIT | Homebridge 插件 | 相关项目 |
| [oladoga/AIRMX-Setup](https://github.com/oladoga/AIRMX-Setup) | Apache-2.0 | 加湿器 BLE 配网 Python 脚本 | 补充了蓝牙加密模式的细节 |
| [dext0r/airmx](https://github.com/dext0r/airmx) | **无声明** | 加湿器 Home Assistant 集成 | **仅链接，未引用其代码** |
| [dizherui/Airmx](https://github.com/dext0r/airmx) | — | 上者的中文记录版 | 同上 |
| [cloverstd/airmx-fan](https://github.com/cloverstd/airmx-fan) | — | ESP32 + Rust 硬件替代控制器 | **互补**：那是换掉控制板自己驱动风机，本仓库是保留原厂设备恢复软件控制 |

> `dext0r/airmx` 仓库没有声明许可证，因此本仓库**只引用链接，不复制其中任何代码**。

特别感谢 [@lizhineng](https://github.com/lizhineng)——他的
[研究手记](https://gist.github.com/lizhineng/7d172feea28b7796a70df3d886b5d943)
最早记录了 MQTT 连接凭据的获取途径，也是整个 openairmx 生态的起点。

---

## 免责声明

本项目是**非官方项目**，与北京彩云归科技有限公司（AIRMX / 秒新）无任何关联。
厂商已停止运营，不再提供任何服务。

本项目的目的仅在于**让用户继续使用自己已经购买的硬件**（互操作性）。
不包含官方 APK 或完整反编译产物；`reference/java/` 中的代码片段版权归原权利人所有。

详细声明见 **[NOTICE.md](NOTICE.md)**。

## 许可证

代码与文档采用 [MIT 许可证](LICENSE)。
`reference/java/` 中的反编译片段**不受**本许可证覆盖，详见 [NOTICE.md](NOTICE.md)。

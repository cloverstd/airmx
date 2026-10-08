# 02 · 整体架构

## 你需要替换掉两台服务器

设备固件里写死了两个域名，两者都必须被"骗"到你自己的机器上：

| 域名 | 原来是干什么的 | 现在需要提供什么 |
|---|---|---|
| `mqtt.airmx.cn:1883` | MQTT broker，收发控制指令与状态 | 一个 broker（如 mosquitto） |
| `i.airmx.cn:80` | 业务 API：配网、发 key、对时 | 一个极小的模拟 HTTP 服务 |

> 注意：`i.airmx.cn` 只在**配网阶段**需要。设备一旦记下了 MQTT 地址和 key，
> 日常运行就只跟 broker 打交道了。所以模拟服务器可以用完就关——这也更安全
> （见 [NOTICE.md §6.2](../NOTICE.md)）。

## 拓扑

```
                    ┌─────────────────────────────┐
                    │  你的内网（如 192.168.2.0/24）│
                    └─────────────────────────────┘

   ┌──────────────┐                                  ┌──────────────────┐
   │  新风机       │                                  │  控制脚本 / HA   │
   │  (AIRMX Pro) │                                  │  (本项目)        │
   └──────┬───────┘                                  └────────┬─────────┘
          │                                                   │
          │  连 mqtt.airmx.cn:1883                             │
          │  （固件里写死，改不了）                             │
          │                                                   │
          ▼                                                   ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │  路由器 / DNS 把 *.airmx.cn 解析到内网机器                          │
   └────────────────────────────────┬───────────────────────────────────┘
                                    │
                                    ▼
                    ┌──────────────────────────────┐
                    │  你的服务器（树莓派 / NAS /   │
                    │  软路由 …… 都行）             │
                    │                              │
                    │  :1883  mosquitto            │  ← 设备实际连这里
                    │  :80    openairmx/server     │  ← 只在配网时需要
                    └──────────────────────────────┘
```

## 最小可行方案（推荐）

直接复用社区已经做好的两个轮子，不要自己造：

### 第 1 步：MQTT broker

```bash
# 任意一台常开的内网机器
apt install mosquitto
```

配置要点（`/etc/mosquitto/conf.d/local.conf`）：

```conf
listener 1883 0.0.0.0
allow_anonymous true
```

> ⚠️ **只监听内网**。设备本身是明文连接、无认证的，千万别把这个端口暴露到公网。

### 第 2 步：DNS 重映射

在你的路由器 / DNS 服务器上，把下列域名解析到上面那台机器：

```
mqtt.airmx.cn    → <你的服务器 IP>
i.airmx.cn       → <你的服务器 IP>
awm.airmx.cn     → <你的服务器 IP>     # 有加湿器才需要
fanmq.airmx.cn   → <你的服务器 IP>     # 有风扇才需要
air.airmx.cn     → <你的服务器 IP>     # 可选
```

用 dnsmasq 的话：

```conf
# /etc/dnsmasq.d/airmx.conf
address=/airmx.cn/192.168.2.215
```

### 第 3 步：配网 + 取 key

用 [openairmx/setup](https://github.com/openairmx/setup)（网页版，部署在
<https://airmx.pro>）+ [openairmx/server](https://github.com/openairmx/server)
（模拟服务器）走一次蓝牙配网，把 device key 取回来。

完整步骤见 **[09-device-key.md](09-device-key.md)**。

### 第 4 步：开始控制

```bash
AIRMX_BROKER=mqtt.airmx.cn AIRMX_DEVICE_ID=1 AIRMX_KEY=<你的key> \
    python src/airmx.py status
```

## 验证设备是否连上来了

配网完成后，设备会在几秒内连上你的 broker。用任意 MQTT 客户端订阅 `airmx/#`
就能看到它：

```bash
mosquitto_sub -h 192.168.2.215 -t 'airmx/#' -v
```

正常情况下你会看到设备每 **300 秒**上报一次状态：

```json
{"cmdId":210,"name":"eagleStatus","time":1791472543,"from":2,
 "data":{"version":"10.00.17","power":1,"heatStatus":0,"mode":0,
         "cadr":35,"prm":1020,...},"sig":"..."}
```

看到 `cmdId:210` 就说明设备活着并且连上了。若想让它立刻上报一次，可以发一条
`instantPush`（见 [06-commands.md](06-commands.md)）。

## 常见变体

| 做法 | 优缺点 |
|---|---|
| **路由器 DNS 重映射**（推荐） | 一次配好，全屋设备生效，不挑客户端 |
| 单机 hosts 文件 | 只能骗过那台机器，设备连不上——**不适用于本方案** |
| DHCP 分配 + 静态路由劫持 | 部分路由器（Keenetic / MikroTik）支持，效果同上 |
| ESP8266 专用接入点 | 不想动主路由时用，见 `airmx-ha/airmx-esp-gate` |

## 与生态里其他方案的关系

```
openairmx/server  ──┐
（模拟 i.airmx.cn）  │
                    ├──►  拿到 device key  ──►  本项目（MQTT 控制）
openairmx/setup   ──┘         ▲
（蓝牙配网页）                  │
                              │
dext0r/airmx  ────────────────┘  加湿器走这条路（HA 集成）
oladoga/AIRMX-Setup ──────────┘  备选的蓝牙配网脚本
```

本仓库专注**新风机（Eagle）的 MQTT 协议**，是链条上"拿到 key 之后怎么控制"那一段。

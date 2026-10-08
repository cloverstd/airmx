# 09 · device key：是什么，怎么拿到

> **这是整个方案最关键的一步。** 没有 key，你能看到设备在说什么，
> 但一句指令也发不进去。
>
> 成熟度：✅ 实测

## 一、它是什么

`device key` 是一个 **32 位小写十六进制字符串**，例如：

```
0123456789abcdef0123456789abcdef     ← 这是占位符示例，不是真实密钥
```

每一台设备有**自己唯一的一个**。它的作用只有一个：

> **给控制指令做 MD5 签名。**

见 [05-signing.md](05-signing.md)。设备收到指令后会用自己的 key 复算一遍签名，
对不上就直接忽略——**不报错、不回应、什么都不发生**。

### 它和 MQTT 密码不是一回事

| | 用途 | 现在的状况 |
|---|---|---|
| MQTT 密码（登录 token） | 连上 broker 的凭据 | 自建 broker 通常放开匿名连接，**不需要** |
| **device key** | 给控制指令签名 | **必需，且只有你有** |

### ⚠️ 它不可更换

目前**没有已知方法能更换设备的 key**——它很可能在固件里固化。

请把它当作**不可撤销的凭据**：一旦公开，任何能访问你局域网内 MQTT broker 的人
都能控制你的机器。不要提交到仓库、不要贴到 Issue 或论坛、不要出现在截图里。

## 二、它是怎么来的（原本的流程）

原厂的流程是这样的：

```
   App                    i.airmx.cn 服务器              新风机
    │                            │                        │
    │  ① 蓝牙配网：发 WiFi 密码    │                        │
    ├───────────────────────────────────────────────────► │
    │                            │                        │
    │                            │  ② 设备注册，上报       │
    │                            │     MAC + 自己的 key    │
    │                            │ ◄──────────────────────┤
    │                            │                        │
    │                            │  ③ 返回 eagleId（设备ID）│
    │                            ├───────────────────────► │
    │                            │                        │
    │  ④ 登录后拉设备列表          │                        │
    │     拿到 key                │                        │
    │ ◄──────────────────────────┤                        │
```

关键点在第 ②步：**设备会主动把自己的加密密钥上报给服务器**。

这个设计原本是为了让服务器能代表用户去控制设备。但对现在的我们来说，
它意味着——**只要冒充服务器，就能把 key 骗出来。**

## 三、怎么拿到（现在的做法）

社区已经把这条链路做成了现成的工具，直接复用即可：

| 组件 | 作用 | 地址 |
|---|---|---|
| **`openairmx/server`** | 模拟 `i.airmx.cn` 的 Node.js 服务，负责接收并保存设备上报的 key | <https://github.com/openairmx/server> |
| **`openairmx/setup`** | 网页版蓝牙配网工具，最后把 key 显示出来让你复制 | <https://github.com/openairmx/setup> · 部署在 <https://airmx.pro> |

### 完整步骤

#### 第 1 步：准备模拟服务器

在一台内网常开的机器上：

```bash
git clone https://github.com/openairmx/server
cd server
node server.mjs
# 输出：Listening on 0.0.0.0:80
```

它零依赖，只要有 Node.js 就能跑。

#### 第 2 步：把 `i.airmx.cn` 指过去

在路由器 / DNS 上重映射：

```
i.airmx.cn  →  <跑 server 的那台机器>
```

> 为什么必须改 DNS 而不能改设备配置？因为设备固件里的主机名是写死的，
> 配网时改不了。

#### 第 3 步：跑一次蓝牙配网

1. 用 **Chrome / Edge**（需要支持 Web Bluetooth）打开 <https://airmx.pro>
2. 把设备置于配网状态（一般需要长按机身上的配对键，不同型号操作不同）
3. 按页面提示完成：连接蓝牙 → 输入 WiFi 密码 → 等待注册
4. **成功后页面会显示 device key，点 Copy 复制出来**

#### 第 4 步：妥善保存

把 key 存到本地配置文件（**不要提交到 git**）：

```bash
mkdir -p ~/.config/airmx
cat > ~/.config/airmx/config.toml <<'EOF'
[broker]
host = "mqtt.airmx.cn"
port = 1883

[device]
id  = <你的设备 ID>
key = "<你复制出来的 key>"
EOF

chmod 600 ~/.config/airmx/config.toml
```

或者用环境变量：

```bash
export AIRMX_KEY="<你的 key>"
export AIRMX_DEVICE_ID="<你的设备 ID>"
```

#### 第 5 步：验证

```bash
python src/verify_control.py --broker mqtt.airmx.cn --device <设备ID>
```

这个脚本会：读当前状态 → 改一下风量 → 确认设备回执 → 恢复原值。
看到 `✅ 结论：device key 有效` 就成功了。

## 四、模拟服务器的接口细节

`openairmx/server` 实现了 `i.airmx.cn` 的四个端点：

### `GET /gettime`

设备定时对时。

```json
{ "time": 1700000000 }
```

### `GET /eagle?path=eagle/GET/genId`

**最关键的一个。** 设备在配网时调用它注册自己。

请求参数：

```
params = {"mac": "AA:BB:CC:DD:EE:FF", "key": "<设备自己的加密密钥>"}
```

服务端把 `mac` 和 `key` 存库，返回分配的设备 ID：

```json
{ "status": 200, "data": { "eagleId": 1 } }
```

> 💡 `eagleId` 是数据库自增主键。所以**第一个注册的设备 ID 就是 1**——
> 这也解释了为什么社区里很多人的设备 ID 都是 1。

### `GET /eagle?path=eagle/GET/online`

设备用来判断"我有没有连上 AIRMX 网络"。模拟服务器固定返回成功，
让设备以为检测仪和主机都已连接：

```json
{ "status": 200, "data": { "snow": 1, "eagle": 1 } }
```

### `GET /exchange?device={id}`

配网页用它把 key 取回来显示给用户。

```json
{ "key": "<该设备的 device key>" }
```

> ⚠️ **这个接口没有任何鉴权。** 只要知道设备 ID 就能拿到 key。
> 所以 `openairmx/server` **绝对不能暴露到公网**，而且最好配完网就关掉。

## 五、如果我已经没有原厂设备了

key 是**每台设备唯一**的，无法从别处推导。兜底的途径：

| 途径 | 说明 |
|---|---|
| 旧手机里的 App 数据 | 如果你以前登录过 App 并拉取过设备列表，key 可能残留在应用数据或历史抓包里 |
| 重新配网 | 按上面第三节的流程走一遍（**推荐**，只要设备还能进配网模式） |
| 拆机读取 | 理论上可行，本项目未验证 |

> ⚠️ 注意：`openairmx/airmx` 的测试文件里出现过一串格式合法的 key
> （形如 `f0eb21fe…022cd5d`）。**那是另一个设备的，对你的机器无效**，
> 不要拿它当默认值去试。

## 六、常见问题

**Q：配网后设备没连上我的 broker？**

检查设备是否真的把 `i.airmx.cn` 和 `mqtt.airmx.cn` 都解析到了你的服务器。
用 `mosquitto_sub -t 'airmx/#' -v` 观察，设备上线后每 300 秒会有一条上报。

**Q：配网页面报错 / 30 秒超时？**

多半是 `i.airmx.cn` 没有正确重映射，或者模拟服务器没在 80 端口跑起来。
设备连不上服务器就不会完成注册。

**Q：我还能用官方 App 吗？**

不能。而且就算能，也**不能和自建 broker 同时用**——设备只能连一个服务器。

**Q：key 泄露了怎么办？**

很遗憾，目前**没有轮换机制**。请把它当不可撤销的凭据，泄露后建议尽快排查
局域网内谁还能访问你的 broker。

## 七、致谢

这条链路完全建立在 [openairmx](https://github.com/openairmx) 的工作之上。
本仓库是在他们打通"拿到 key"这一段之后，继续把 MQTT 协议层挖透并整理成文档。

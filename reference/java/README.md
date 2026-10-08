# 反编译代码片段

这里收录的是官方 App v3.1.3（包名 `com.caiyungui.xinfeng`）的反编译片段，
用于**佐证 `docs/` 里协议文档的准确性**——文档里的每个结论都能在下面这些
代码里找到出处。

## 版权与用途声明

这些代码提取自厂商发布的 Android 应用，**版权归原厂商所有**，此处仅作为
技术说明与互操作性研究之用。厂商（北京彩云归科技）已停止运营，不再提供
任何服务与支持。若权利方有异议，请联系删除。

## 提取方式

```bash
# 1. 取得官方 APK（v3.1.3）
#    官方渠道已下线，可从 APKPure 等归档站获取 com.caiyungui.xinfeng

# 2. 反编译
jadx -d out --deobf AIRMX秒新_3.1.3.apk
```

## 文件对照

反编译产物里类名被混淆成了 `C2089g` 这种形式，但 jadx 保留了原始类名注释
（形如 `/* compiled from: Topic.java */`）。下表给出对照关系。

| 本目录文件 | 反编译产物路径 | 原始类名 | 证明了什么 |
|---|---|---|---|
| `MqttBaseMessage.java` | `com/caiyungui/xinfeng/mqtt/msg/MqttBaseMessage.java` | `MqttBaseMessage` | 报文信封的字段与顺序、**签名算法**、`type` 字段是字符串 |
| `MqttInterface.connection.java` | `com/caiyungui/xinfeng/mqtt/eagle/MqttInterface.java` | `MqttInterface` | MQTT 连接参数（clientId / username / password / keepAlive）、订阅与发布的 topic |
| `MqttInterface.signature-check.java` | 同上 | `MqttInterface` | 收到报文时的**验签公式**，以及 `sig` 必须位于末尾这一事实 |
| `Topic.java` | `com/caiyungui/xinfeng/mqtt/C2089g.java` | `Topic` | topic 模板的构造方式 |
| `MD5Util.java` | `com/caiyungui/xinfeng/p133n/p134a/C2100k.java` | `MD5Util` | 签名用的 MD5 实现（标准小写 hex） |
| `MqttConstans.java` | `com/caiyungui/xinfeng/mqtt/C2063e.java` | `MqttConstans` | 三条产品线各自的服务器地址 |

## 阅读顺序建议

先看 `MD5Util.java`（就 30 行），再看 `MqttBaseMessage.java` 的
`createPayload()` / `createSign()`，最后看 `MqttInterface.signature-check.java`。
三者连起来就是完整的签名机制。

对应的中文说明见 [`docs/05-signing.md`](../../docs/05-signing.md)。

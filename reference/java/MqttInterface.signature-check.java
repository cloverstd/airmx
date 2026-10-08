/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: MqttInterface
 * 路径: com/caiyungui/xinfeng/mqtt/eagle/MqttInterface.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/05-signing.md
 *
 * ★ 本文件摘出 messageArrived() 这一个方法，它是接收端验签的实现。
 *   同样的代码在 airwater/MqttAwInterface.java 和 fan/MqttFanInterface.java
 *   里一字不差地重复了三遍。
 */
package com.caiyungui.xinfeng.mqtt.eagle;

// ...（import 略）

public class MqttInterface /* ... */ {

    @Override // org.eclipse.paho.client.mqttv3.MqttCallback
    public void messageArrived(String str, MqttMessage mqttMessage) {
        try {
            C2099j.m10747b(f9203l, "onMessageArrived Topic:" + str + " Message:" + new String(mqttMessage.getPayload()));
            String mqttMessage2 = mqttMessage.toString();
            if (TextUtils.isEmpty(mqttMessage2)) {
                C2099j.m10752g(f9203l, "onMessageArrived but MqttMessage is null msg=" + mqttMessage2);
                return;
            }
            JSONObject jSONObject = new JSONObject(mqttMessage2);

            // 没有 cmdId 的报文走另一条分支（旧版检测仪传感器数据格式），
            // 不做签名校验，见 docs/07-fields.md 末尾。
            if (!jSONObject.has("cmdId")) {
                m10598w(jSONObject);
                return;
            }

            int optInt = jSONObject.optInt("cmdId");

            // ⚠️ cmdId 为 30 / 1006 / 2006 的报文【完全跳过签名校验】—— 它们是服务端推送
            if (optInt != 30 && optInt != 1006 && optInt != 2006) {
                if (this.f9214i == null) {
                    C2099j.m10752g(f9203l, "onMessageArrived but bind eagle is null msg=" + jSONObject.toString());
                    return;
                }

                /* ================= 验签部分 ================= */
                String string = jSONObject.getString("sig");
                String key = this.f9214i.getKey();

                // 从索引 1 开始（跳过首个 '{'），切到 indexOf("sig") - 1 为止
                String m10753a = C2100k.m10753a(
                    mqttMessage2.substring(1, mqttMessage2.indexOf("sig") - 1) + key
                );

                if (m10753a.equals(string)) {
                    MqttBaseMessage m10656a = C2076f.m10656a(jSONObject, this.f9214i.getId(), this.f9214i.getKey());
                    if (m10656a == null) {
                        return;
                    }
                    m10597v(m10656a);
                    return;
                }
                C2099j.m10747b(f9203l, "onMessageArrived but invalid signature localSign=" + m10753a + " eagleKey=" + key);
                return;
                /* =========================================== */
            }

            MqttAppMsg mqttAppMsg = new MqttAppMsg(0L, "");
            if (mqttAppMsg.parse(jSONObject) == 1) {
                m10596u(mqttAppMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            C2099j.m10747b(f9203l, e.getMessage());
        }
    }
}

/*
 * 验签公式拆解：为什么它和发送端是等价的
 * ------------------------------------------------------------------
 * 收到的一条报文（sig 必须是最后一个键）：
 *
 *   {"cmdId":210,"name":"eagleStatus","time":1791472543,"from":2,"data":{…},"sig":"68399ecb…"}
 *    0^                                                                          ^
 *
 *   indexOf("sig") 命中的是 "sig" 里的那个 s，记其下标为 S。
 *   那么 S-1 是 "sig" 的左引号，S-2 是它前面的那个逗号。
 *
 *   substring(1, S-1) 取的是 [1, S-2]，也就是：
 *       "cmdId":210,"name":"eagleStatus",…,"data":{…},
 *   —— 已经包含末尾那个逗号了。
 *
 *   再拼上 key，等价于：
 *       MD5( 去掉花括号的 JSON + "," + deviceKey )
 *
 * 而发送端 createSign(json.substring(1, len-1)) 是：
 *       MD5( 去掉花括号的 JSON + "," + deviceKey )
 * （发送时 JSON 里还没有 sig，所以没有尾逗号，由 createSign 补上）
 *
 * 两边完全一致。
 *
 * 由此推出几个必须注意的脆点：
 *   1. sig 必须位于 JSON 文本的最后，否则 indexOf("sig") 定位不到正确位置；
 *   2. indexOf 找的是【子串 "sig"】，如果更早的地方出现了这两个字母
 *      （比如某个字符串值里含有 "sig"），定位就会错，验签必然失败；
 *   3. 校验用的是收到的【原始文本】，不是重新序列化的 JSON，
 *      所以空白、键顺序必须逐字节保持原样；
 *   4. cmdId ∈ {30, 1006, 2006} 跳过验签。
 */

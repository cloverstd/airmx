/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: MqttBaseMessage
 * 路径: com/caiyungui/xinfeng/mqtt/msg/MqttBaseMessage.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/05-signing.md
 *
 * ★ 这是整个签名机制的核心。createMsgObj() 决定字段顺序，
 *   createSign() 决定签名公式，createPayload() 把两者串起来。
 */
package com.caiyungui.xinfeng.mqtt.msg;

import com.caiyungui.xinfeng.p133n.p134a.C2100k;
import com.umeng.analytics.pro.C3201c;
import java.io.Serializable;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public abstract class MqttBaseMessage extends MqttMessage implements Serializable {
    private static final int SENDABLE_MESSAGE_DEFAULT_FROM = 4;
    private int cmdId;
    private long deviceId;
    private String deviceKey;
    private String sig;
    private long time;
    private int type;
    private String name = "";
    private int from = 4;

    public MqttBaseMessage(long j, String str) {
        this.deviceId = j;
        this.deviceKey = str;
    }

    /*
     * 字段顺序在这里确定: cmdId, name, time, from, [type]
     * 注意 type 被拼成了字符串 (this.type + "")，且 type == 0 时整个字段不出现。
     */
    private JSONObject createMsgObj() {
        JSONObject jSONObject = new JSONObject();
        jSONObject.putOpt("cmdId", Integer.valueOf(this.cmdId));
        jSONObject.putOpt("name", this.name);
        jSONObject.putOpt("time", Long.valueOf(this.time));
        jSONObject.putOpt("from", Integer.valueOf(this.from));
        if (this.type != 0) {
            jSONObject.putOpt(C3201c.f15147y, this.type + "");   // C3201c.f15147y == "type"
        }
        return jSONObject;
    }

    /* ★ 签名公式: MD5( 传入的字符串 + "," + deviceKey ) */
    private String createSign(String str) {
        return C2100k.m10753a(str + "," + this.deviceKey);
    }

    public int createPayload() {
        try {
            JSONObject createMsgObj = createMsgObj();
            createMsgObj.putOpt("data", getDataJsonObject());
            String jSONObject = createMsgObj.toString();

            // substring(1, length-1) 去掉首尾两个花括号，然后交给 createSign
            String createSign = createSign(jSONObject.substring(1, jSONObject.length() - 1));

            this.sig = createSign;
            createMsgObj.putOpt("sig", createSign);       // sig 追加在最后
            setPayload(createMsgObj.toString().getBytes());
            return 1;
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis() / 1000;         // time 是秒级时间戳
    }

    public int getCmdId() {
        return this.cmdId;
    }

    abstract JSONObject getDataJsonObject();

    public long getDeviceId() {
        return this.deviceId;
    }

    public String getDeviceKey() {
        return this.deviceKey;
    }

    public int getFrom() {
        return this.from;
    }

    public String getName() {
        return this.name;
    }

    public String getSig() {
        return this.sig;
    }

    public long getTime() {
        return this.time;
    }

    public int getType() {
        return this.type;
    }

    public int parse(JSONObject jSONObject) {
        try {
            this.cmdId = jSONObject.optInt("cmdId");
            this.name = jSONObject.optString("name");
            this.time = jSONObject.optLong("time");
            this.from = jSONObject.optInt("from");
            parseJsonData(jSONObject.getJSONObject("data"));   // data 必须存在
            this.sig = jSONObject.optString("sig");
            return 1;
        } catch (JSONException e) {
            e.printStackTrace();
            return -1;
        }
    }

    abstract void parseJsonData(JSONObject jSONObject);

    public void setCmdId(int i) {
        this.cmdId = i;
    }

    public void setDeviceId(long j) {
        this.deviceId = j;
    }

    public void setDeviceKey(String str) {
        this.deviceKey = str;
    }

    public void setFrom(int i) {
        this.from = i;
    }

    public void setName(String str) {
        this.name = str;
    }

    public void setSig(String str) {
        this.sig = str;
    }

    public void setTime(long j) {
        this.time = j;
    }

    public void setType(int i) {
        this.type = i;
    }

    @Override // org.eclipse.paho.client.mqttv3.MqttMessage
    public String toString() {
        return "MqttBaseMessage{eaglekey='" + this.deviceKey + "', sig='" + this.sig + "', eagleId=" + this.deviceId + ", cmdId=" + this.cmdId + ", name='" + this.name + "', time=" + this.time + ", from=" + this.from + '}';
    }
}

/*
 * 结论（详见 docs/05-signing.md）:
 *
 *   发送时的序列化顺序固定为
 *       {"cmdId":c,"name":n,"time":t,"from":f,[,"type":"…"],"data":{…},"sig":"…"}
 *
 *   签名原文 = 上面的 JSON 去掉首尾花括号、去掉 sig 字段，再拼上 "," 和 deviceKey
 *       sig = MD5( "cmdId":c,"name":n,"time":t,"from":f[,"type":"…"],"data":{…} + "," + deviceKey )
 *
 *   因为 sig 是在签名之后才追加的，所以签名时它必然不在原文里；
 *   而接收方校验时（见 MqttInterface.signature-check.java）是从收到的
 *   原始文本里把 sig 之前的部分切出来 —— 这就要求 sig 必须是最后一个键。
 */

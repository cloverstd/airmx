/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: MqttConstans
 * 混淆后路径: com/caiyungui/xinfeng/mqtt/C2063e.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/03-connection.md
 */
package com.caiyungui.xinfeng.mqtt;

import com.caiyungui.xinfeng.AirMxEnvironment;

/* compiled from: MqttConstans.java */
/* renamed from: com.caiyungui.xinfeng.mqtt.e */
/* loaded from: classes.dex */
public class C2063e {
    /* renamed from: a   加湿器 (AirWater) 的 broker 主机名 */
    public static String m10586a() {
        return AirMxEnvironment.m9907a().m9909c() ? "awm.airmx.cn" : "123.206.27.237";
    }

    /* renamed from: b   新风机 / 检测仪 (Eagle / Snow) 的 broker 主机名 */
    public static String m10587b() {
        return AirMxEnvironment.m9907a().m9909c() ? "mqtt.airmx.cn" : "123.206.27.237";
    }

    /* renamed from: c   风扇 (Fan) 的 broker，这里已经带上了协议和端口 */
    public static String m10588c() {
        return AirMxEnvironment.m9907a().m9909c() ? "tcp://fanmq.airmx.cn:1883" : "tcp://123.206.27.237:1883";
    }
}

/*
 * 三条产品线各自的 broker：
 *
 *   新风机 / 检测仪   mqtt.airmx.cn:1883
 *   加湿器            awm.airmx.cn:1883
 *   风扇              fanmq.airmx.cn:1883
 *
 * 非线上环境统一回落到 123.206.27.237:1883（同一个 IP，只是主机名不同）。
 *
 * 现状：这三个域名指向的服务器（线上环境为腾讯云 82.157.56.105）已全部下线，
 * 所有端口不可达。所以需要在路由器 / DNS 层把域名劫持到自己内网的 broker。
 */

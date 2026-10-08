/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: Topic
 * 混淆后路径: com/caiyungui/xinfeng/mqtt/C2089g.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/04-topics.md
 */
package com.caiyungui.xinfeng.mqtt;

/* compiled from: Topic.java */
/* renamed from: com.caiyungui.xinfeng.mqtt.g */
/* loaded from: classes.dex */
public class C2089g {
    /* renamed from: a */
    public static String m10719a(long j, int i, int i2, int i3) {
        return String.format("airwater/01/%s/%s/%s/%s", Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3), Long.valueOf(j));
    }

    /* renamed from: b */
    public static String m10720b(long j) {
        return String.format("airwater/01/%s/%s/%s/%s", "+", 1, "+", Long.valueOf(j));
    }

    /* renamed from: c */
    public static String m10721c(long j, int i, int i2, int i3, int i4) {
        return String.format("airmx/01/%s/%s/%s/%s/%s", Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3), Integer.valueOf(i4), Long.valueOf(j));
    }

    /* renamed from: d */
    public static String m10722d(long j) {
        return String.format("airmx/01/%s/%s/%s/%s/%s", "+", "+", 1, "+", Long.valueOf(j));
    }
}

/*
 * 全部 topic 都由这 4 个方法生成，参数就是路径里的各段：
 *
 *   m10721c(deviceId, F1, F2, F3, F4)
 *       → airmx/01/{F1}/{F2}/{F3}/{F4}/{deviceId}      新风机 / 检测仪
 *
 *   m10719a(deviceId, F2, F3, F4)
 *       → airwater/01/{F2}/{F3}/{F4}/{deviceId}        加湿器（少一个字段）
 *
 * 调用的地方（见 MqttInterface.connection.java）：
 *   发布控制      m10721c(id, 1, 1, 0, 1)  → airmx/01/1/1/0/1/{id}
 *   发布请求上报  m10721c(id, 0, 1, 0, 1)  → airmx/01/0/1/0/1/{id}
 *   订阅设备上报  m10722d(id)              → airmx/01/+/+/1/+/{id}
 *
 * 注意后两者的第二个字段不同（1 和 0），这是文档里标记为「含义未确认」的部分，
 * 但它们的实际值已由真实抓包确认可用。
 */

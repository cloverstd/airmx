/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: MD5Util
 * 混淆后路径: com/caiyungui/xinfeng/p133n/p134a/C2100k.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/05-signing.md
 */
package com.caiyungui.xinfeng.p133n.p134a;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/* compiled from: MD5Util.java */
/* renamed from: com.caiyungui.xinfeng.n.a.k */
/* loaded from: classes.dex */
public class C2100k {
    /* renamed from: a */
    public static String m10753a(String str) {
        try {
            byte[] digest = MessageDigest.getInstance("md5").digest(str.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b2 : digest) {
                String hexString = Integer.toHexString(b2 & 255);
                if (hexString.length() == 1) {
                    sb.append("0");
                }
                sb.append(hexString);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
}

/*
 * 要点：
 * 1. 就是标准 MD5，输出 32 位小写十六进制，每个字节左侧补零（不会丢前导零）。
 * 2. str.getBytes() 使用平台默认字符集 —— 在 Android 上是 UTF-8。
 * 3. 它本身不掺盐，盐（device key）是在调用方拼接进去的，
 *    见 MqttBaseMessage.createSign()。
 */

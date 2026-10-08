/*
 * 反编译自官方 App v3.1.3 (com.caiyungui.xinfeng)
 * 原始类名: MqttInterface
 * 路径: com/caiyungui/xinfeng/mqtt/eagle/MqttInterface.java
 *
 * 版权归原厂商所有，此处仅作技术说明与互操作性研究之用。
 * 说明文档: docs/03-connection.md
 *
 * ★ 本文件是从该类的 572 行里摘出的【连接与收发】相关片段，
 *   非完整文件。方法名保留 jadx 的混淆编号，便于对照原产物。
 */
package com.caiyungui.xinfeng.mqtt.eagle;

// ...（import 略）

public class MqttInterface extends Binder implements InterfaceC2061c, MqttCallback {

    /* ============================================================
     * 1. clientId 的生成（构造函数）
     *    "amx_" + 设备标识的前 19 位  → 共 23 个字符
     *    这里的「设备标识」是手机自己的 UUID，不是新风机的 ID。
     *    见 docs/03-connection.md 里 DeviceUuidFactory 的算法。
     * ============================================================ */
    public MqttInterface(Context context) {
        C2099j.m10747b(f9203l, "Mqtt interface onCrate");
        this.f9206a = context;
        HandlerThread handlerThread = new HandlerThread(f9204m);
        handlerThread.start();
        this.f9209d = new Handler(handlerThread.getLooper());
        this.f9210e = new MqttDefaultFilePersistence(context.getCacheDir().getAbsolutePath());
        this.f9208c = String.format("amx_%s", C2096g.m10733a(this.f9206a).substring(0, 19));
    }

    /* ============================================================
     * 2. 建立连接：地址、用户名、密码、超时、保活
     *    username = "app_" + 用户 uid
     *    password = 登录拿到的 token
     * ============================================================ */
    /* renamed from: m */
    private MqttClient m10595m() {
        String format = String.format(Locale.US, "tcp://%s:%d", C2063e.m10587b(), 1883);
        C2099j.m10747b(f9203l, " create mqtt clientId：" + this.f9208c + " with " + format);
        MqttClient mqttClient = new MqttClient(format, this.f9208c, this.f9210e);
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        this.f9212g = mqttConnectOptions;
        mqttConnectOptions.setCleanSession(false);
        String str = "app_" + C2007e.m10234a().m10236c();      // 用户 uid
        this.f9212g.setUserName(str);
        String m10235b = C2007e.m10234a().m10235b();           // 登录 token
        C2099j.m10747b(f9203l, " create mqtt userName:" + str + " token:" + m10235b);
        if (!TextUtils.isEmpty(m10235b)) {
            this.f9212g.setPassword(m10235b.toCharArray());
        }
        this.f9212g.setConnectionTimeout(10);
        this.f9212g.setKeepAliveInterval(20);
        mqttClient.setCallback(this);
        return mqttClient;
    }

    /* ============================================================
     * 3. 订阅：airmx/user/{uid}  —— 服务端给 App 的推送通道
     * ============================================================ */
    /* renamed from: j */
    private void m10592j() {
        m10590B();
        C2007e m10234a = C2007e.m10234a();
        if (m10234a.m10238e()) {
            String format = String.format("airmx/user/%s", m10234a.m10236c() + "");
            this.f9215j = format;
            m10589A(format);
        }
    }

    /* ============================================================
     * 4. 订阅：airmx/01/+/+/1/+/{deviceId}  —— 设备状态上报
     * ============================================================ */
    @Override // com.caiyungui.xinfeng.mqtt.InterfaceC2061c
    /* renamed from: f */
    public void mo10527f(Device device) {
        mo10529h();
        this.f9214i = device;
        if (device != null) {
            C2099j.m10747b(f9203l, "bindDevice: deviceId=" + device.getId() + " key=" + device.getKey());
            String m10722d = C2089g.m10722d(device.getId());     // Topic 工厂，见 Topic.java
            this.f9216k = m10722d;
            m10589A(m10722d);
        }
    }

    /* ============================================================
     * 5. 发布：控制指令
     *    topic = airmx/01/1/1/0/1/{deviceId}
     *    QoS 1，retain false
     * ============================================================ */
    @Override // com.caiyungui.xinfeng.mqtt.InterfaceC2061c
    /* renamed from: b */
    public void mo10523b(MqttBaseMessage mqttBaseMessage, IMqttActionListener iMqttActionListener) {
        // ...（省略空值 / 连接状态检查）
        try {
            mqttBaseMessage.setRetained(false);
            mqttBaseMessage.setQos(1);
            mqttBaseMessage.createPayload();                     // 在这里生成 sig
            String m10721c = C2089g.m10721c(mqttBaseMessage.getDeviceId(), 1, 1, 0, 1);
            C2099j.m10747b(f9203l, "Mqtt send msg cmd=" + mqttBaseMessage.getCmdId() + " topic=" + m10721c + " msg=" + new String(mqttBaseMessage.getPayload()));
            this.f9211f.getTopic(m10721c).publish(mqttBaseMessage);
            if (iMqttActionListener != null) {
                iMqttActionListener.onSuccess(new MqttToken());
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /* ============================================================
     * 6. 发布：请求设备立即上报（instantPush）
     *    topic = airmx/01/0/1/0/1/{deviceId}   ← 与控制的 1/1/0/1 不同！
     * ============================================================ */
    @Override // com.caiyungui.xinfeng.mqtt.InterfaceC2061c
    /* renamed from: d */
    public void mo10525d(MqttReportInterval mqttReportInterval, IMqttActionListener iMqttActionListener) {
        // ...（省略空值 / 连接状态检查）
        try {
            mqttReportInterval.setRetained(false);
            mqttReportInterval.setQos(1);
            mqttReportInterval.createPayload();
            String m10721c = C2089g.m10721c(mqttReportInterval.getDeviceId(), 0, 1, 0, 1);
            C2099j.m10747b(f9203l, "Mqtt send sendSnowReportInterval cmd=" + mqttReportInterval.getCmdId() + " topic=" + m10721c + " msg=" + new String(mqttReportInterval.getPayload()));
            this.f9211f.getTopic(m10721c).publish(mqttReportInterval);
            if (iMqttActionListener != null) {
                iMqttActionListener.onSuccess(new MqttToken());
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /* ============================================================
     * 7. 断线重连 + 保活
     * ============================================================ */
    @Override // org.eclipse.paho.client.mqttv3.MqttCallback
    public void connectionLost(Throwable th) {
        if (th != null) {
            th.printStackTrace();
            C2099j.m10747b(f9203l, "连接丢失 connectionLost e:" + th.toString());
        }
        m10593k(MqttConnStatus.DISCONNECTED);
        this.f9209d.postDelayed(new Runnable() { // 1 秒后重连
            @Override
            public void run() {
                MqttInterface.this.m10602p();
            }
        }, 1000L);
    }

    /* MqttService 里另有 AlarmManager 每 5 分钟拉起一次保活，见 docs/03-connection.md */
}

/*
 * 关键结论：
 *
 *   地址          tcp://mqtt.airmx.cn:1883
 *   clientId      amx_{手机设备UUID前19位}
 *   username      app_{用户uid}
 *   password      {登录 token}
 *   cleanSession  false
 *   keepAlive     20 秒
 *   connectionTimeout 10 秒
 *   发布 QoS      1，retain false
 *   订阅          airmx/user/{uid}、airmx/01/+/+/1/+/{deviceId}
 *   发布控制      airmx/01/1/1/0/1/{deviceId}
 *   发布请求上报  airmx/01/0/1/0/1/{deviceId}
 *
 * 注意：username / password 是【登录账号】的凭据，用于连上 broker；
 *       它和后面签名的 device key 是两回事，不要混淆。
 *       现在的本地 broker 一般直接放开匿名连接，这两者用不上；
 *       真正决定能不能控制设备的是 device key。
 */

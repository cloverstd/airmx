#!/usr/bin/env python3
"""AIRMX 秒新新风机（Eagle）本地控制器。

厂商已停止运营，官方 MQTT 服务器下线。本工具让你可以在自己的局域网里
重新控制设备。

协议来源：官方 App v3.1.3（包名 com.caiyungui.xinfeng）反编译 + 真实抓包验证。
完整协议文档见 docs/，签名算法见 docs/05-signing.md。

  MQTT 地址   tcp://<broker>:1883        （官方为 mqtt.airmx.cn，现已停服）
  订阅        airmx/01/+/+/1/1/{deviceId}
  下发控制    airmx/01/1/1/0/1/{deviceId}
  请求上报    airmx/01/0/1/0/1/{deviceId}    ← 注意与控制的 topic 不同
  签名        sig = MD5(JSON 去掉首尾花括号与 sig 字段 + "," + deviceKey)

配置优先级：命令行参数 > 环境变量 > config.toml

  AIRMX_BROKER / AIRMX_PORT / AIRMX_DEVICE_ID / AIRMX_KEY
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path

import paho.mqtt.client as mqtt

# ---------- 协议常量 ----------

CMD_INSTANT_PUSH = 40        # 请求设备提高上报频率
CMD_CONTROL = 100            # 控制指令
CMD_EAGLE_STATUS = 210       # 设备状态上报

FROM_APP_ANDROID = 4         # 报文 from 字段：App 侧
FROM_EAGLE = 2               # 报文 from 字段：设备侧

# MqttBaseMessage.type —— App(Android) 发请求时固定为该值，且序列化成【字符串】
# 见 docs/05-signing.md
MSG_TYPE_ANDROID = "10000"

MODE_NAMES = {0: "手动", 1: "AI", 2: "静音", 3: "强力"}
MODE_ALIASES = {"manual": 0, "ai": 1, "silent": 2, "turbo": 3}

CONFIG_PATHS = (
    Path("config.toml"),                                             # 当前目录
    Path(__file__).parent.parent / "config.toml",                    # 仓库根目录
    Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))  # 用户配置目录
    / "airmx" / "config.toml",
)


# ---------- 签名 ----------

def md5_hex(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def sign_payload(payload: dict, key: str) -> str:
    """签名。字段顺序必须与 App 的序列化顺序一致，否则签名不匹配：
    cmdId, name, time, from, [type], data
    """
    body = dict(payload)
    body.pop("sig", None)
    inner = json.dumps(body, separators=(",", ":"), ensure_ascii=False)[1:-1]
    return md5_hex(inner + "," + key)


def verify_payload(raw: str, key: str) -> bool:
    """校验收到的报文签名。与 App 的 MqttInterface.messageArrived() 公式一致：
    md5(raw.substring(1, raw.indexOf("sig") - 1) + key)
    """
    try:
        sig = json.loads(raw)["sig"]
    except Exception:
        return False
    i = raw.index("sig")
    return md5_hex(raw[1:i - 1] + key) == sig


# ---------- 状态 ----------

@dataclass
class EagleStatus:
    device_id: int
    version: str = ""
    power: int = 0
    heat_status: int = 0
    mode: int = 0
    cadr: int = 0
    prm: int = 0              # 实际转速 RPM
    denoise: int = 0
    status: int = 0
    t0: int = 0               # 进风温度
    diff_pressure1: int = 0
    diff_pressure2: int = 0
    g4_id: str = ""
    g4_percent: int = 0
    carbon_id: str = ""
    carbon_percent: int = 0
    hepa_id: str = ""
    hepa_percent: int = 0
    raw_time: int = 0
    received_at: float = field(default_factory=time.time)

    @classmethod
    def from_message(cls, device_id: int, d: dict) -> "EagleStatus":
        x = d.get("data", {})
        return cls(
            device_id=device_id,
            version=x.get("version", ""),
            power=x.get("power", 0),
            heat_status=x.get("heatStatus", 0),
            mode=x.get("mode", 0),
            cadr=x.get("cadr", 0),
            prm=x.get("prm", 0),
            denoise=x.get("denoise", 0),
            status=x.get("status", 0),
            t0=x.get("t0", 0),
            diff_pressure1=x.get("diffPressure1", 0),
            diff_pressure2=x.get("diffPressure2", 0),
            g4_id=x.get("g4Id", ""),
            g4_percent=x.get("g4Percent", 0),
            carbon_id=x.get("carbonId", ""),
            carbon_percent=x.get("carbonPercent", 0),
            hepa_id=x.get("hepaId", ""),
            hepa_percent=x.get("hepaPercent", 0),
            raw_time=d.get("time", 0),
        )

    def to_control(self) -> dict:
        """转成控制报文的 data 部分（字段名与设备一致）。"""
        return {
            "power": self.power,
            "heatStatus": self.heat_status,
            "mode": self.mode,
            "cadr": self.cadr,
            "denoise": self.denoise,
        }

    def describe(self) -> str:
        return (
            f"设备 {self.device_id}  固件 {self.version}\n"
            f"  电源 {'开' if self.power else '关'}   "
            f"模式 {MODE_NAMES.get(self.mode, self.mode)}   "
            f"风量 {self.cadr}%\n"
            f"  电辅热 {'开' if self.heat_status else '关'}   "
            f"降噪 {'开' if self.denoise else '关'}   "
            f"进风温度 {self.t0}°C   转速 {self.prm} RPM\n"
            f"  滤芯 G4 {self.g4_percent}%   碳 {self.carbon_percent}%   "
            f"HEPA {self.hepa_percent}%"
        )


# ---------- 客户端 ----------

class Airmx:
    def __init__(self, broker: str, port: int, device_id: int, key: str,
                 debug: bool = False):
        if not key or key.startswith("<"):
            raise ValueError(
                "缺少 device key。\n"
                "  获取方式见 docs/09-device-key.md\n"
                "  可通过 --key、环境变量 AIRMX_KEY 或 config.toml 提供。"
            )
        self.broker, self.port = broker, port
        self.device_id, self.key = device_id, key
        self.debug = debug

        self.status: EagleStatus | None = None
        self._status_event = threading.Event()
        self._subscribed = False

        self.client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"amx_ctrl_{int(time.time())}",
        )
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    # ----- MQTT -----

    def _on_connect(self, c, u, flags, rc, props):
        if self.debug:
            print(f"[mqtt] 已连接 {rc}", file=sys.stderr)
        # App 订阅的是 airmx/01/+/+/1/+/{id}，这里收窄到实测的设备上报位置
        self.client.subscribe(f"airmx/01/+/+/1/1/{self.device_id}", qos=1)
        self._subscribed = True

    def _on_message(self, c, u, msg):
        raw = msg.payload.decode("utf-8", "replace")
        if self.debug:
            print(f"[mqtt] <- {msg.topic} {raw[:200]}", file=sys.stderr)
        try:
            d = json.loads(raw)
        except Exception:
            return
        if d.get("cmdId") != CMD_EAGLE_STATUS:
            return
        if not verify_payload(raw, self.key):
            if self.debug:
                print("[mqtt] !! 签名校验失败，丢弃", file=sys.stderr)
            return
        self.status = EagleStatus.from_message(self.device_id, d)
        self._status_event.set()

    def connect(self):
        self.client.connect(self.broker, self.port, 30)
        self.client.loop_start()
        for _ in range(50):
            if self._subscribed:
                break
            time.sleep(0.1)

    def close(self):
        self.client.loop_stop()
        self.client.disconnect()

    # ----- 发送 -----

    def _publish(self, topic: str, cmd_id: int, name: str, data: dict,
                 msg_type: str | None = None) -> str:
        # 字段顺序必须是 cmdId, name, time, from, [type], data —— 顺序影响签名
        body = {
            "cmdId": cmd_id,
            "name": name,
            "time": int(time.time()),
            "from": FROM_APP_ANDROID,
        }
        if msg_type is not None:
            body["type"] = msg_type
        body["data"] = data
        body["sig"] = sign_payload(body, self.key)
        payload = json.dumps(body, separators=(",", ":"), ensure_ascii=False)
        if self.debug:
            print(f"[mqtt] -> {topic} {payload}", file=sys.stderr)
        self.client.publish(topic, payload, qos=1)
        return payload

    def request_status(self, frequency: int = 2, duration: int = 300,
                       wait: int = 40) -> EagleStatus | None:
        """请求设备立即上报状态。纯读取，不改变设备运行状态。

        topic 是 airmx/01/0/1/0/1/{id}，与控制的 1/1/0/1 不同。
        App 还会带上 type="10000"。
        """
        self._status_event.clear()
        self._publish(f"airmx/01/0/1/0/1/{self.device_id}", CMD_INSTANT_PUSH,
                      "instantPush",
                      {"frequencyTime": frequency, "durationTime": duration},
                      msg_type=MSG_TYPE_ANDROID)
        if self._status_event.wait(wait):
            return self.status
        return None

    def control(self, **changes) -> EagleStatus | None:
        """下发控制指令。未指定的字段沿用设备当前状态。

        可传：power(bool) / heat(bool) / denoise(bool) / mode(int) / cadr(int)
        """
        if self.status is None:
            self.request_status()
        if self.status is None:
            raise RuntimeError("读不到设备当前状态，无法安全下发控制")

        data = self.status.to_control()
        if "mode" in changes:
            data["mode"] = changes["mode"]
        if "cadr" in changes:
            data["cadr"] = max(0, min(100, int(changes["cadr"])))
        for src, dst in (("power", "power"), ("heat", "heatStatus"),
                         ("denoise", "denoise")):
            if src in changes and changes[src] is not None:
                data[dst] = 1 if changes[src] else 0

        self._status_event.clear()
        self._publish(f"airmx/01/1/1/0/1/{self.device_id}", CMD_CONTROL,
                      "control", data)
        self._status_event.wait(20)
        return self.status


# ---------- 配置 ----------

def load_config() -> dict:
    cfg = {"broker": None, "port": None, "device_id": None, "key": None}

    for path in CONFIG_PATHS:
        if path.is_file():
            try:
                import tomllib
                with path.open("rb") as f:
                    raw = tomllib.load(f)
            except Exception as e:
                print(f"⚠️  读取 {path} 失败: {e}", file=sys.stderr)
                continue
            cfg["broker"] = raw.get("broker", {}).get("host")
            cfg["port"] = raw.get("broker", {}).get("port")
            cfg["device_id"] = raw.get("device", {}).get("id")
            cfg["key"] = raw.get("device", {}).get("key")
            break

    if os.environ.get("AIRMX_BROKER"):
        cfg["broker"] = os.environ["AIRMX_BROKER"]
    if os.environ.get("AIRMX_PORT"):
        cfg["port"] = int(os.environ["AIRMX_PORT"])
    if os.environ.get("AIRMX_DEVICE_ID"):
        cfg["device_id"] = int(os.environ["AIRMX_DEVICE_ID"])
    if os.environ.get("AIRMX_KEY"):
        cfg["key"] = os.environ["AIRMX_KEY"]

    return cfg


# ---------- CLI ----------

def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        prog="airmx",
        description="AIRMX 秒新新风机本地控制器（协议逆向详见 docs/）",
        epilog="写操作会真实改变设备运行状态，需额外加 --yes。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--broker", help="MQTT broker 地址（默认取 config.toml / $AIRMX_BROKER）")
    ap.add_argument("--port", type=int, help="MQTT 端口，默认 1883")
    ap.add_argument("--device", type=int, help="设备 ID")
    ap.add_argument("--key", help="device key（优先用 $AIRMX_KEY，勿写进命令行历史）")
    ap.add_argument("-v", "--verbose", action="store_true", help="打印 MQTT 收发原文")
    ap.add_argument("--wait", type=int, default=40,
                    help="等待设备上报的秒数（设备空闲时每 300 秒才上报一次，默认 40）")

    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status", help="读取当前状态（只读）")
    sub.add_parser("push", help="请求设备立即上报一次（只读）")
    sub.add_parser("noop", help="下发与当前状态完全相同的控制指令，用于验证控制通道（物理零变化）")

    for name, help_text in (("on", "开机"), ("off", "关机")):
        p = sub.add_parser(name, help=help_text)
        p.add_argument("--yes", action="store_true", help="确认执行")
    p = sub.add_parser("cadr", help="设置风量 0-100（会切到手动模式并开机）")
    p.add_argument("value", type=int)
    p.add_argument("--yes", action="store_true", help="确认执行")
    p = sub.add_parser("mode", help="切换运行模式")
    p.add_argument("value", choices=list(MODE_ALIASES))
    p.add_argument("--yes", action="store_true", help="确认执行")
    for name, help_text in (("heat", "电辅热开关"), ("denoise", "降噪开关")):
        p = sub.add_parser(name, help=help_text)
        p.add_argument("value", choices=["on", "off"])
        p.add_argument("--yes", action="store_true", help="确认执行")

    return ap


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    cfg = load_config()

    broker = args.broker or cfg["broker"]
    port = args.port or cfg["port"] or 1883
    device = args.device if args.device is not None else cfg["device_id"]
    key = args.key or cfg["key"]

    if not broker or device is None or not key:
        missing = []
        if not broker:
            missing.append("broker 地址")
        if device is None:
            missing.append("设备 ID")
        if not key:
            missing.append("device key")
        print(f"❌ 缺少配置：{'、'.join(missing)}\n", file=sys.stderr)
        print("请任选一种方式提供：", file=sys.stderr)
        print("  1) cp examples/config.example.toml config.toml 然后填好", file=sys.stderr)
        print("  2) 设置环境变量 AIRMX_BROKER / AIRMX_DEVICE_ID / AIRMX_KEY", file=sys.stderr)
        print("  3) 命令行传 --broker --device --key", file=sys.stderr)
        print("\n获取 device key：见 docs/09-device-key.md", file=sys.stderr)
        return 2

    try:
        dev = Airmx(broker, port, device, key, args.verbose)
    except ValueError as e:
        print(f"❌ {e}", file=sys.stderr)
        return 2

    dev.connect()
    try:
        if args.cmd in ("status", "push"):
            st = dev.request_status(wait=args.wait)
            print(st.describe() if st else
                  f"❌ {args.wait} 秒内没收到设备上报。\n"
                  f"   · 设备是否在线？检查 broker 上是否能看到 airmx/01/0/1/1/1/{device}\n"
                  f"   · 加大等待秒数（--wait 320）可覆盖设备 300 秒的空闲上报周期")
            return 0 if st else 1

        if args.cmd == "noop":
            st = dev.request_status(wait=args.wait)
            if st is None:
                print("❌ 拿不到当前状态，中止")
                return 1
            print("当前状态：")
            print(st.describe())
            data = st.to_control()
            print(f"\n下发与当前完全相同的控制报文（物理零变化）：{data}")
            dev._status_event.clear()
            dev._publish(f"airmx/01/1/1/0/1/{dev.device_id}", CMD_CONTROL,
                         "control", data)
            print("已发送，监听 90 秒观察设备回执 ...")
            if dev._status_event.wait(90):
                print("\n✅ 设备有回执，控制通道正常。")
                print(dev.status.describe())
                return 0
            print("\n❌ 90 秒内无回执")
            return 1

        # 以下都是写操作
        if not args.yes:
            print(f"⚠️  '{args.cmd}' 会改变设备的实际运行状态。")
            print("   确认后请加 --yes 重新执行。当前状态：")
            st = dev.request_status(wait=args.wait)
            print(st.describe() if st else "(取不到状态)")
            return 2

        if args.cmd == "on":
            st = dev.control(power=True)
        elif args.cmd == "off":
            st = dev.control(power=False)
        elif args.cmd == "cadr":
            st = dev.control(mode=0, cadr=args.value, power=True)
        elif args.cmd == "mode":
            m = MODE_ALIASES[args.value]
            extra = {"cadr": 100} if m == 3 else {}
            st = dev.control(mode=m, power=True, **extra)
        elif args.cmd == "heat":
            st = dev.control(heat=args.value == "on")
        elif args.cmd == "denoise":
            st = dev.control(denoise=args.value == "on")
        else:
            return 2
        print(st.describe() if st else "已发送（未收到回执）")
        return 0
    finally:
        dev.close()


if __name__ == "__main__":
    sys.exit(main())

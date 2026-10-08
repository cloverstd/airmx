#!/usr/bin/env python3
"""端到端验证：确认你的 device key 能通过 MQTT 真实控制新风机。

流程（会真实改变设备风量，最后自动还原）：

  1. 读取当前状态
  2. 把 cadr 改成一个明显不同的值，确认设备回执显示新值
  3. 恢复原始 cadr，再次确认

每一步的设备回执都会用你的 key 验签，双向都通才算通过。

用法：
    AIRMX_KEY=<你的 key> python verify_control.py --device <设备ID> --broker <broker>
    或先写好 config.toml（见 examples/config.example.toml）
"""
from __future__ import annotations

import argparse
import sys

from airmx import Airmx, load_config


def show(tag: str, st) -> None:
    if st is None:
        print(f"{tag}: (未收到)")
        return
    print(f"{tag}: power={st.power} mode={st.mode} cadr={st.cadr} "
          f"prm={st.prm} denoise={st.denoise} heat={st.heat_status} t0={st.t0}")


def main(argv=None) -> int:
    cfg = load_config()

    ap = argparse.ArgumentParser(description="AIRMX 控制链路端到端验证")
    ap.add_argument("--broker", default=cfg["broker"])
    ap.add_argument("--port", type=int, default=cfg["port"] or 1883)
    ap.add_argument("--device", type=int, default=cfg["device_id"])
    ap.add_argument("--key", default=cfg["key"])
    ap.add_argument("--target-cadr", type=int, default=None,
                    help="要切换到的风量；默认自动挑一个与当前不同的值")
    ap.add_argument("--wait", type=int, default=120, help="等待设备上报的秒数")
    args = ap.parse_args(argv)

    if not args.broker or args.device is None or not args.key:
        print("❌ 缺少 broker / 设备 ID / device key，请先配置 config.toml "
              "或设置 AIRMX_* 环境变量", file=sys.stderr)
        return 2

    print("=== AIRMX 控制链路验证 ===")
    print(f"broker = {args.broker}:{args.port}   device = {args.device}\n")

    dev = Airmx(args.broker, args.port, args.device, args.key, debug=False)
    dev.connect()
    try:
        print("[1] 请求设备上报并读取当前状态 ...")
        print("    （设备空闲时每 300 秒才上报一次，这一步可能要等一会儿）")
        st = dev.request_status(wait=args.wait)
        if st is None:
            print(f"❌ {args.wait} 秒内没收到设备上报")
            return 1
        print("    ✅ 收到回执且签名验证通过")
        show("    当前", st)
        original = st.to_control()

        target = args.target_cadr
        if target is None:
            target = 70 if int(st.cadr) < 50 else 30

        print(f"\n[2] 下发控制：cadr {st.cadr} → {target}（其余字段保持原值）")
        st2 = dev.control(mode=st.mode, cadr=target,
                          power=bool(st.power), heat=bool(st.heat_status),
                          denoise=bool(st.denoise))
        show("    回执", st2)
        if st2 is None:
            print("    ❌ 未收到回执")
            ok = False
        elif int(st2.cadr) == target:
            print(f"    ✅ 控制生效，cadr 已变为 {target}")
            ok = True
        else:
            print(f"    ⚠️  回执 cadr={st2.cadr}，与目标 {target} 不一致")
            ok = False

        print(f"\n[3] 恢复原始 cadr={original['cadr']}")
        st3 = dev.control(mode=original["mode"], cadr=original["cadr"],
                          power=bool(original["power"]),
                          heat=bool(original["heatStatus"]),
                          denoise=bool(original["denoise"]))
        show("    回执", st3)

        print("\n" + "=" * 56)
        if ok:
            print("✅ 结论：device key 有效，可通过 MQTT 完整控制该设备。")
        else:
            print("❌ 结论：未能确认控制生效。")
            print("   排查建议：")
            print("   · 确认 broker 上确实能看到设备（订阅 airmx/# 观察）")
            print("   · 确认 topic 是 airmx/01/1/1/0/1/{deviceId}")
            print("   · 确认 device key 与设备匹配")
        print("=" * 56)
        return 0 if ok else 1
    finally:
        dev.close()


if __name__ == "__main__":
    sys.exit(main())

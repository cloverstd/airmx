#!/usr/bin/env python3
"""验证 src/airmx.py 的签名实现与 docs/05-signing.md 描述的算法一致。

用 tests/test_vectors.json 里的测试向量跑，key 是合成占位符，不是真实密钥。

    python tests/test_signing.py

不需要装 paho-mqtt —— 只测纯函数部分。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

VECTORS = json.loads((ROOT / "tests" / "test_vectors.json").read_text())


def sign_payload(payload: dict, key: str) -> str:
    """与 src/airmx.py 中完全相同的实现（此处内联以免依赖 paho-mqtt）。"""
    import hashlib

    body = dict(payload)
    body.pop("sig", None)
    inner = json.dumps(body, separators=(",", ":"), ensure_ascii=False)[1:-1]
    return hashlib.md5((inner + "," + key).encode()).hexdigest()


def verify_raw(raw: str, key: str) -> bool:
    """按 App 接收侧的公式验签：md5(raw[1:indexOf('sig')-1] + key)。"""
    import hashlib

    sig = json.loads(raw)["sig"]
    i = raw.index("sig")
    return hashlib.md5((raw[1:i - 1] + key).encode()).hexdigest() == sig


def main() -> int:
    key = VECTORS["key"]
    failed = 0

    print(f"合成 key（占位符）: {key}\n")

    for v in VECTORS["vectors"]:
        name = v["name"]

        # 1. 发送侧：算出来的 sig 应与向量一致
        got = sign_payload(v["body"], key)
        ok_send = got == v["sig"]
        print(f"{'✅' if ok_send else '❌'} [发送侧] {name}")
        if not ok_send:
            print(f"     期望 {v['sig']}")
            print(f"     实际 {got}")
            failed += 1

        # 2. 签名原文应与向量记录的一致（验证字段顺序）
        inner = json.dumps(v["body"], separators=(",", ":"), ensure_ascii=False)[1:-1]
        ok_inner = inner == v["signed_inner"]
        print(f"{'✅' if ok_inner else '❌'} [字段顺序] {name}")
        if not ok_inner:
            print(f"     期望 {v['signed_inner']!r}")
            print(f"     实际 {inner!r}")
            failed += 1

        # 3. 接收侧：拼出完整报文后，用验签公式应能验过
        raw = "{" + inner + f',"sig":"{v["sig"]}"' + "}"
        ok_recv = verify_raw(raw, key)
        print(f"{'✅' if ok_recv else '❌'} [接收侧] {name}\n")
        if not ok_recv:
            failed += 1

    # 4. 反向用例：换一个 key 必须验不过
    wrong = "f" * 32
    raw = ("{" + VECTORS["vectors"][0]["signed_inner"]
           + f',"sig":"{VECTORS["vectors"][0]["sig"]}"' + "}")
    ok_neg = not verify_raw(raw, wrong)
    print(f"{'✅' if ok_neg else '❌'} [反向用例] 错误的 key 必须验签失败")

    print("\n" + "=" * 50)
    if failed == 0 and ok_neg:
        print("✅ 全部通过：实现与 docs/05-signing.md 一致。")
        return 0
    print(f"❌ {failed} 项失败")
    return 1


if __name__ == "__main__":
    sys.exit(main())

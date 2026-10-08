# AIRMX (秒新) · Local Control

English | [中文](README.md)

**The vendor is gone, the official servers are offline, and the app no longer works —
but the hardware can be brought back to life.**

This repository is a complete reverse-engineering of the official AIRMX app (v3.1.3):
the MQTT protocol, the signing algorithm, the full command set, and an end-to-end path
to regain control of your device. It ships with a field-tested Python reference client.

> ✅ **Verified**: after reconnecting the device to a local MQTT broker, reading status,
> power on/off, fan speed, run mode, auxiliary heat and denoise all work.
> Tested on AIRMX Pro, firmware `10.00.17`.

---

## Quick start

Three steps. Full instructions in [docs/](docs/) (Chinese, with English identifiers
in all tables and code).

### 1. Obtain your device key

This is the critical — and historically the hardest — step. Control commands must be
signed, and signing requires a per-device key.

The [openairmx](https://github.com/openairmx) community solved this: run a
[mock server](https://github.com/openairmx/server) impersonating `i.airmx.cn`,
then re-provision the device with the
[Web Bluetooth setup page](https://airmx.pro). **The device uploads its own key
to the server during provisioning, and the page displays it for you.**

👉 Full walkthrough: [docs/09-device-key.md](docs/09-device-key.md)

### 2. Run an MQTT broker on your LAN

```bash
apt install mosquitto
```

```conf
# /etc/mosquitto/conf.d/local.conf
listener 1883 0.0.0.0
allow_anonymous true
```

Point `*.airmx.cn` at that machine via your router / DNS:

```conf
# /etc/dnsmasq.d/airmx.conf
address=/airmx.cn/192.168.2.215
```

The device connects within seconds. Verify:

```bash
mosquitto_sub -h 192.168.2.215 -t 'airmx/#' -v
# a cmdId:210 status report every 300 seconds
```

### 3. Control it

```bash
pip install paho-mqtt

export AIRMX_BROKER=mqtt.airmx.cn
export AIRMX_DEVICE_ID=<your-device-id>
export AIRMX_KEY=<your-device-key>

python src/airmx.py status          # read-only
python src/airmx.py cadr 60 --yes   # set fan speed
python src/airmx.py mode turbo --yes
python src/airmx.py on --yes  /  off --yes
python src/airmx.py heat on --yes  /  denoise off --yes
```

Without `--yes`, write commands only print the current state.

Not sure your key is right? Run the end-to-end check:

```bash
python src/verify_control.py
```

It reads the status, changes the fan speed, confirms the device acknowledges,
then restores the original value.

---

## Protocol at a glance

### Signing

```
sig = MD5( JSON without outer braces and without the sig field + "," + deviceKey )
```

```python
inner = json.dumps(body, separators=(",", ":"))[1:-1]   # body has no sig yet
sig = hashlib.md5((inner + "," + key).encode()).hexdigest()
```

Field order is fixed: `cmdId, name, time, from, [type], data, sig`.
**The order is part of the protocol** — change it and the signature breaks.

👉 Details: [docs/05-signing.md](docs/05-signing.md)

### Topics

| Purpose | Topic |
|---|---|
| Send control | `airmx/01/1/1/0/1/{deviceId}` |
| Request immediate report | `airmx/01/0/1/0/1/{deviceId}` |
| Device status report | `airmx/01/0/1/1/1/{deviceId}` |
| App subscription | `airmx/01/+/+/1/+/{deviceId}` |

> ⚠️ Control and request-report use **different** topics. Get it wrong and the
> device silently ignores you.

### Control message

```json
{"cmdId":100,"name":"control","time":1700000000,"from":4,
 "data":{"power":1,"heatStatus":0,"mode":0,"cadr":50,"denoise":0},
 "sig":"80b161d4407168d344d9f2eaab92b230"}
```

| Field | Values |
|---|---|
| `power` | 0 off / 1 on |
| `mode` | 0 manual / 1 AI / 2 silent / 3 turbo |
| `cadr` | 0–100 fan speed percentage |
| `denoise` | 0 off / 1 on |
| `heatStatus` | 0 off / 1 on |

---

## Documentation

Full index: **[docs/README.md](docs/README.md)** (Chinese). Two entry paths:

**"I just want my machine back"**
[01 Background](docs/01-background.md) → [09 Device key](docs/09-device-key.md) →
[02 Architecture](docs/02-architecture.md) → use [src/airmx.py](src/airmx.py)

**"I'm writing my own client / HA integration / firmware"**
[03 Connection](docs/03-connection.md) → [04 Topics](docs/04-topics.md) →
[05 Signing](docs/05-signing.md) → [06 Commands](docs/06-commands.md) →
[07 Fields](docs/07-fields.md) → [08 Enums](docs/08-enums.md) →
[11 Pitfalls](docs/11-pitfalls.md)

Every document carries a confidence marker: ✅ measured / 📖 decompiled / ❓ inferred.
Measurements are concentrated on the **air purifier (Eagle)**; the humidifier and fan
product lines are mostly 📖 level.

### Supporting material

| Path | Contents |
|---|---|
| [src/airmx.py](src/airmx.py) | Python reference client + CLI |
| [src/verify_control.py](src/verify_control.py) | End-to-end verification script |
| [reference/java/](reference/java/) | Key decompiled snippets (signing, topics, connection) |
| [tests/test_vectors.json](tests/test_vectors.json) | Signing test vectors to validate your implementation |
| [examples/config.example.toml](examples/config.example.toml) | Config template |

---

## ⚠️ Security notes

1. **The device key is equivalent to full control of the device.** Never commit it,
   never paste it into an issue or forum. All examples here use placeholders.
2. **There is currently no way to rotate the key** — treat it as an irrevocable
   credential.
3. **Never expose the mock server to the internet.** Its `/exchange?device={id}`
   endpoint has **no authentication whatsoever**.
4. **Don't expose your MQTT broker either.** The device connects in plaintext,
   without authentication.

See [NOTICE.md](NOTICE.md) for the full statement (Chinese).

---

## Ecosystem & credits

The device-key acquisition path is **entirely built on the work of
[openairmx](https://github.com/openairmx)**. This repository picks up where they
left off: digging through the MQTT protocol layer and documenting it.

| Project | License | Role | Relation |
|---|---|---|---|
| [openairmx/server](https://github.com/openairmx/server) | MIT | Node.js mock of `i.airmx.cn` | **Prerequisite**: receives and stores the key |
| [openairmx/setup](https://github.com/openairmx/setup) | MIT | Web Bluetooth setup page (hosted at <https://airmx.pro>) | **Prerequisite**: provisions and reveals the key |
| [openairmx/airmx](https://github.com/openairmx/airmx) | MIT | TypeScript MQTT client | Independent implementation; signing algorithm cross-validated |
| [openairmx/homebridge-airmx](https://github.com/openairmx/homebridge-airmx) | MIT | Homebridge plugin | Related work |
| [oladoga/AIRMX-Setup](https://github.com/oladoga/AIRMX-Setup) | Apache-2.0 | BLE provisioning script (Python) | Documents the encrypted BLE mode |
| [dext0r/airmx](https://github.com/dext0r/airmx) | **none declared** | Home Assistant integration (humidifier) | **Link only — no code reused** |
| [cloverstd/airmx-fan](https://github.com/cloverstd/airmx-fan) | — | ESP32 + Rust replacement controller | **Complementary**: replaces the control board; this repo keeps the original hardware alive |

> `dext0r/airmx` declares no license, so this repository **links to it but copies
> none of its code**.

Special thanks to [@lizhineng](https://github.com/lizhineng), whose
[research notes](https://gist.github.com/lizhineng/7d172feea28b7796a70df3d886b5d943)
first documented how to obtain the MQTT credentials — the starting point of the
entire openairmx ecosystem.

---

## Disclaimer

This is an **unofficial project**, not affiliated with or endorsed by
Beijing Caiyungui Technology Co., Ltd. (AIRMX / 秒新). The vendor has ceased
operations and provides no services.

The sole purpose is **interoperability — letting owners keep using hardware they
already purchased**. No official APK or full decompilation output is included;
copyright of the code snippets in `reference/java/` remains with the original rights
holder.

Full statement: **[NOTICE.md](NOTICE.md)** (Chinese).

## License

Code and documentation: [MIT](LICENSE).
The decompiled snippets under `reference/java/` are **not** covered by this license —
see [NOTICE.md](NOTICE.md).

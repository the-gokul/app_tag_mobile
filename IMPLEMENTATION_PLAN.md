# Tag Mobile — Implementation Plan

Step-by-step plan to go from `preview_home.html` (UI mock) to a working **Android app** + **tag firmware** (`app_tag`).

---

## Overview

| Layer | Repo | Role |
|-------|------|------|
| Tag firmware | `C:\nordic\v\app_tag` | BLE peripheral: control writes + sensor notify stream |
| Mobile app | `C:\nordic\v\app_tag_mobile` | Scan, connect, Start/Stop, receive packets, save CSV |

**Wire format:** binary `sensor_packet_t` v8 over GATT notify (not CSV on BLE).  
**CSV:** built on the phone when user taps Save.

---

## Phase 1 — Tag firmware: BLE control (START / STOP / time sync)

**Goal:** Phone can tell the tag when to sample and sync wall-clock once.

| Step | Task | Status |
|------|------|--------|
| 1.1 | Add `tag_control` module (session + time sync state) | Done |
| 1.2 | Add GATT **TAG_COMMAND** write characteristic | Done |
| 1.3 | Gate `sensor_sched` — sample only while recording | Done |
| 1.4 | Reset packet/sample counters on START | Done |
| 1.5 | Document command bytes in `BLE.md` | Done |

**TAG_COMMAND UUID:** `7f5e0a12-4c1d-4b9a-9c22-a1b2c3d4e5f6`

| Byte 0 | Command | Payload |
|--------|---------|---------|
| `0x01` | START | `int64` mobile Unix ms (8 bytes, LE) |
| `0x02` | STOP | none |

**Behaviour:**
- Boot: **not recording** (power save — no sensor reads).
- START: store `unix_ms` + tag uptime, reset counters, begin sampling → ring → notify.
- STOP: stop sampling; keep BLE connected.
- Packets still use **tag uptime** in header; phone computes `date_time = syncBase + timestamp_ms`.

---

## Phase 2 — Tag firmware: runtime config (optional, after Phase 1 works)

| Step | Task |
|------|------|
| 2.1 | Add **TAG_CONFIG** write characteristic |
| 2.2 | Apply: period, samples/packet, flush_pkts, sensor enables |
| 2.3 | Match preview defaults (5 samples, 50 ms, 20 hold pkts) |

Deferred until Phase 1 is tested on hardware.

---

## Phase 3 — Android app scaffold

| Step | Task | Status |
|------|------|--------|
| 3.1 | Create Gradle project under `app_tag_mobile/android/` | Done |
| 3.2 | BLE permissions + Nordic scan/connect libs | Done (platform BLE API) |
| 3.3 | Port screens from `preview_home.html` to Compose/XML | Done |
| 3.4 | GATT: discover TAG_STREAM, enable notify, write TAG_COMMAND | Done |

**Reference:** [Android-nRF-Toolbox](https://github.com/nordicsemi/Android-nRF-Toolbox) patterns.

---

## Phase 4 — Android: recording flow

| Step | Task | Status |
|------|------|--------|
| 4.1 | Start → write START + `System.currentTimeMillis()` | Done |
| 4.2 | Parse v8 notify payloads (`sensor_packet.h` layout) | Done |
| 4.3 | Stop → write STOP | Done |
| 4.4 | Status UI: Ready / Syncing / Receiving / Received | Done |
| 4.5 | Save → CSV with `timestamp_ms` + `date_time` columns | Done |

---

## Phase 5 — Android: Custom data + file format

| Step | Task | Status |
|------|------|--------|
| 5.1 | Custom data screen (period, samples, hold, sensors) | Done (app-side) |
| 5.2 | Save config to tag (Phase 2 firmware) | Pending |
| 5.3 | SI unit columns toggle (app-side CSV only) | Done |
| 5.4 | Storage Access Framework — save `.csv` to phone | Done |

---

## Phase 6 — Polish & test

| Step | Task |
|------|------|
| 6.1 | MTU exchange (central requests ≥ 247) |
| 6.2 | Coded PHY fallback note (many phones scan 1M only) |
| 6.3 | End-to-end test: connect → Start → Stop → Save CSV |
| 6.4 | Remove or archive `preview_home.html` when app replaces it |

---

## BLE service map (target)

```
TAG_STREAM  7f5e0a10-…-e5f6
├── TAG_SENSOR_DATA  7f5e0a11  NOTIFY     sensor_packet_t v8
├── CCC (notify enable)
└── TAG_COMMAND      7f5e0a12  WRITE      START / STOP + time
```

---

## Current defaults (preview ↔ firmware target)

| Setting | Preview | `app_tag` today (compile-time) |
|---------|---------|--------------------------------|
| Samples / packet | 5 | 5 (`ACCUM=250`, `PERIOD=50`) |
| Sample period | 50 ms | 50 ms |
| Hold packets | 20 | 20 |
| Recording | Start/Stop button | START/STOP via TAG_COMMAND |

Default hold: 20 pkts × 250 ms = **~5 s** before BLE flush (matches preview).

---

## Order of work (this session)

1. ✅ This plan document  
2. ✅ Phase 1 firmware (`tag_control` + GATT write)  
3. ✅ Phase 3 Android scaffold (minimal buildable project)  
4. ✅ Phase 4 recording UI + CSV save  
5. 🔄 Phase 6 polish + hardware test

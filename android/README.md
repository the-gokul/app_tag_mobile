# Tag Mobile — Android App

Native Android app matching `preview_home.html`.

## Open & run

1. Android Studio → **Open** → `C:\nordic\v\app_tag_mobile\android`
2. Gradle sync → Run on phone (USB debugging)

## Screens

| Screen | Activity |
|--------|----------|
| Home (connected devices) | `MainActivity` |
| Scanner | `ScannerActivity` |
| Device (Start / Stop / Save) | `DeviceActivity` |
| Custom data + CSV format | `CustomDataActivity` |

## Flow

1. **+ Scan** → pick Tag device → connect
2. **Start** → sends START + mobile time to tag → receives v8 packets
3. **Stop** → sends STOP → **Save** appears
4. **Save** → name prompt → pick folder → `.csv` with `timestamp_ms` + `date_time`

## BLE

See `IMPLEMENTATION_PLAN.md` for UUIDs and protocol.

**Note:** Custom data settings are stored in the app until tag firmware TAG_CONFIG (Phase 2) is added.


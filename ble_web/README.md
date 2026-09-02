# Tag BLE Web App

Web Bluetooth version of the Tag mobile app — **no APK build needed**.

## Folder

```
ble_web/
  index.html       Main app (same UI as preview)
  js/
    ble-client.js  Web Bluetooth connect / START / STOP
    packet-csv.js  v8 packet parse + CSV download
```

## Use on phone (simplest)

### Step 1 — Put files online (HTTPS required)

Web Bluetooth only works on **HTTPS** (not `file://`).

**Option A — Netlify Drop (free, 2 minutes)**

1. Open [https://app.netlify.com/drop](https://app.netlify.com/drop)
2. Drag the whole `ble_web` folder onto the page
3. Copy the HTTPS link (e.g. `https://random-name.netlify.app`)

**Option B — Same Wi‑Fi + PC (for testing)**

On PC in the `ble_web` folder:

```powershell
cd C:\nordic\v\app_tag_mobile\ble_web
python -m http.server 8080
```

On phone Chrome: `http://<your-pc-ip>:8080`  
Note: plain HTTP may block BLE on some phones — prefer Netlify for phone-only use.

### Step 2 — Open on phone

1. Install **Google Chrome** on Android
2. Open the HTTPS link
3. Optional: Chrome menu → **Add to Home screen** (works like an app)

### Step 3 — Connect & record

1. Tap **+ Connect** → **Select Tag**
2. Pick your tag in the system dialog
3. Open device → **Start** → **Stop** → **Save** (CSV downloads to phone)

## Requirements

| Item | Required |
|------|----------|
| Browser | **Chrome** on Android (or Edge Chromium) |
| Page URL | **HTTPS** (or `localhost`) |
| Tag firmware | `app_tag` with TAG_COMMAND (START/STOP) |
| Bluetooth | ON on phone |

## BLE UUIDs

| Characteristic | UUID |
|----------------|------|
| Service | `7f5e0a10-4c1d-4b9a-9c22-a1b2c3d4e5f6` |
| Sensor data (notify) | `7f5e0a11-…` |
| Command (write) | `7f5e0a12-…` |

## Coded PHY note

If the tag advertises **LE Coded S=8 only**, some phones may not see it in the picker. Flash a **1M PHY** build for phone testing if scan fails.

## vs native Android app

| | ble_web | android/ APK |
|--|---------|----------------|
| Install | Open URL | Install APK |
| Build | None | Android Studio |
| CSV save | Browser download | Files app |
| Custom config to tag | App-side only | App-side only |

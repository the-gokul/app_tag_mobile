# Build APK with GitHub (no Android Studio on your PC)

GitHub builds `app-debug.apk` in the cloud. You **download one file** and install on your phone.

---

## One-time setup (~10 minutes)

### 1. Create GitHub account
[https://github.com/signup](https://github.com/signup) if you don't have one.

### 2. Create a new repository
- GitHub → **New repository**
- Name: e.g. `app_tag_mobile`
- **Private** is fine
- Don't add README (you already have files)

### 3. Push this folder to GitHub

Open PowerShell in `C:\nordic\v\app_tag_mobile`:

```powershell
cd C:\nordic\v\app_tag_mobile
git init
git add .
git commit -m "Tag mobile app"
git branch -M main
git remote set-url origin https://github.com/the-gokul/app_tag_mobile.git
# (first time only: git remote add origin https://github.com/the-gokul/app_tag_mobile.git)
git push -u origin main
```

Replace `YOUR_USERNAME` with your GitHub username.

---

## Build APK (every time you update code)

### 1. On GitHub website
1. Open your repo → **Actions** tab
2. Click **Build Tag APK** (left side)
3. Click **Run workflow** → **Run workflow**

### 2. Wait ~5–10 minutes
Green checkmark = build OK.

### 3. Download APK
1. Open the completed workflow run
2. Scroll to **Artifacts**
3. Download **`tag-app-debug`** (zip with `app-debug.apk` inside)

---

## Install on phone

1. Unzip on PC or phone → get **`app-debug.apk`**
2. Copy to phone **Downloads** (USB, WhatsApp, Drive)
3. Tap **`app-debug.apk`** → Install
4. Open **Tag** app

No link in browser. No Android Studio on your laptop.

---

## Auto-build on push

After setup, every `git push` to `main` that changes `android/` also triggers a new APK build.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Actions tab empty | Push `.github/workflows/build-apk.yml` |
| Build fails | Open failed run → read red log |
| Phone won't install | Settings → allow install from unknown sources |
| Tag not found in app | Flash latest `app_tag` firmware first |

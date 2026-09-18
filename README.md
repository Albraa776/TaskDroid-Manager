# TaskDroid Manager

Full Android device task manager & device-care monitor. Displays everything about your phone in one app:

- **Overview** – live Device Care score, RAM / storage / battery / CPU gauges, quick device summary
- **Network** – live internet graph (WiFi + mobile download/upload KB/s), WiFi details, mobile radio,
  dual/single SIM slots, LTE & 5G NR support, VoLTE status, LTE bands (best-effort from RIL / radio)
- **Battery** – live level graph + temperature graph, volts, current, charge counter, health, cycles
- **CPU** – SoC vendor detection (Qualcomm / MediaTek / Unisoc-Spreadtrum / HiSilicon / Exynos / Tensor…),
  model, platform, architecture, live usage graph, per-core load, current frequencies & governor
- **Apps** – every installed app (user + system) with version and size, searchable, tap to open
- **Services** – running processes & services, PSS memory, foreground/background
- **System** – full hardware + software info, kernel, SELinux, sensors, build, **bootloader locked/unlocked**
- **Care** – storage per volume, RAM detail, swap/ZRAM, **device thermal zones**, and
  **location temperature** of your current place (Open-Meteo) automatically refreshed

Bootloader status is read from `ro.boot.verifiedbootstate`, `vbmeta.device_state`, `flash.locked`
and the build fingerprint – no root required.

## Build

APK is built in the cloud with GitHub Actions (never on a local machine):

- Push to `master`/`main` or run `workflow_dispatch`
- Releases a signed, installable **`TaskDroid Manager.apk`** (minSdk 26 / Android 8.0+)
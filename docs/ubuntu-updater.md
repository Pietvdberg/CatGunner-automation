# Automatic APK updates from Ubuntu

Run the worker as the **same Ubuntu user who paired the phone**, in the SSH session
opened from Termux. It uses that user's existing ADB server and persistent
`~/.android/adbkey`; it never uploads, prints or copies private keys. This cloud
workspace cannot connect to your home LAN, so the one-time installer runs there.

## Install once

First finish `adb connect PHONE_IP:CONNECTION_PORT` using the **main** Wireless
debugging screen, and confirm `adb devices` lists the phone as `device`. Pairing
alone is not a connection. Enrollment groups IP and discovery aliases by hardware identity. If multiple
physical phones are connected, explicitly select yours using `enroll --serial`
as shown below.

Download the small worker bundle, extract it, and run its installer:

```bash
mkdir -p ~/catgunner-updater
curl -fL --retry 3 https://raw.githubusercontent.com/Pietvdberg/CatGunner-automation/refs/tags/deployment-v1.0.1/downloads/ubuntu-updater-1.0.1.tar.gz -o ~/catgunner-updater/worker.tar.gz
tar -xzf ~/catgunner-updater/worker.tar.gz -C ~/catgunner-updater
bash ~/catgunner-updater/install-ubuntu.sh
```

The installer uses sudo to install Ubuntu packages (adb, apksigner, aapt, headless
Java and Python) and enable user lingering. This keeps the user timer running
after SSH logout, while Ubuntu is powered on. Run the script **without sudo**;
Ubuntu may request your normal sudo password. It enrolls the already-connected
phone's hardware serial, installs a user service, and starts the timer. Re-running
the installer preserves enrollment. There is no root service, web listener or AI
model, and no GitHub token is required for this public repository.

## Operation

Every five minutes (plus up to 20 seconds jitter), it:

1. Discovers wireless **connection** ports using ADB mDNS, and reconnects on the LAN.
2. Selects only the enrolled hardware identity. Other paired devices are not updated.
3. Reads `downloads/update.json` over verified HTTPS. Only explicitly published
   metadata advances the update channel; arbitrary CI builds are ignored.
4. Compares the **phone's** installed version code; current/newer versions are left alone.
5. Downloads the versioned APK from its tag, checks SHA-256, verifies its APK
   signature against the pinned existing certificate, and checks package/version/arm64 ABI.
6. Defers while `CaptureService` exists (including paused capture), AutoPilot is
   resumed, or Android's service/activity state cannot be determined. Stop capture
   and leave AutoPilot's screen when you want a pending update to proceed.
7. Rechecks idle state immediately before `adb install -r` and verifies the
   installed version afterward. It never uninstalls, downgrades, wipes data,
   changes Android security settings, or restarts automation/capture.

The idle checks are snapshots, not an atomic Android maintenance lock: avoid
starting capture at the exact moment an update installs. An app-side maintenance
handshake would be needed for a strict race-free guarantee. The worker never
force-stops AutoPilot to make an installation possible.

The initial manifest publishes **0.4.7, version code 14**. If that version is
already installed, the first check correctly reports **Up to date**. Future
published versions install automatically once the phone is reachable and idle.

Screen capture still requires user consent on the phone after an update.
Calibration/app data are retained by `install -r`; accessibility reconnection is
managed by Android. The worker does not automatically grant permissions.

## Commands

```bash
# Read recent results; use -f to follow live.
journalctl --user -u catgunner-updater.service -n 40 --no-pager

# Check immediately.
systemctl --user start catgunner-updater.service

# Pause automatic updates, also stopping a currently running check.
systemctl --user disable --now catgunner-updater.timer
systemctl --user stop catgunner-updater.service

# Resume.
systemctl --user enable --now catgunner-updater.timer
```

State/config/last successful install and at most the latest verified APK live in
`~/.local/state/catgunner-updater/`. The actual installed phone version, not this
record, determines whether to install. Separate invocations share an exclusive
file lock. Logs go to the systemd user journal; no screenshots are collected.

## Reconnection and troubleshooting

Wireless debugging must remain enabled, both devices must be reachable on the
same LAN, and Ubuntu must stay awake. Ports can change on toggles/reboots; mDNS
finds new connection ports without a fresh pairing while Android retains the key.
Network client isolation can block connectivity. Check:

```bash
adb mdns check
adb mdns services
adb devices
```

The discovery list needs an `_adb-tls-connect._tcp` service. If discovery is
unavailable, manually run `adb connect IP:CURRENT_CONNECTION_PORT`; the worker
uses an existing authorized connection too. For a fixed endpoint fallback, or
when more than one phone is connected, re-enroll explicitly:

```bash
python3 ~/.local/share/catgunner-updater/updater.py enroll --serial ADB_DEVICE_SELECTOR --endpoint 192.168.1.82:CURRENT_CONNECTION_PORT
```

That endpoint may need updating when Android changes its port. If authorization
expires/revokes, pair again using `adb pair` under this same Ubuntu user. No timer
can supply Android's new pairing code automatically. Do not expose ADB or SSH on
the public internet. For remote private access use a VPN.

Version 1.0.1 fixes enrollment when ADB exposes the same phone under both its
IP address and mDNS discovery name. Download/extract the new bundle over the old
setup directory and rerun the installer. Packages already installed are reused.

If enrollment says there is no authorized connection, run `adb devices`, connect
the phone using the current connection port, and rerun the installer.
If installation fails, the worker logs the error and tries on the next interval;
it does not silently mark the version installed. If the timer fails, check
`systemctl --user status catgunner-updater.service`. The service needs a normal
Ubuntu systemd user session. It intentionally refuses a first installation and
requires the already-installed package.

## Publishing future APKs

After the normal Android checks, signing verification and versioned APK export:

```bash
# Use Android build-tools on PATH, or Ubuntu's aapt/apksigner packages.
python3 tools/deployment/publish-update.py 0.4.8
```

Commit the new versioned APK and generated `downloads/update.json`, create the
matching immutable `v0.4.8` tag, and push **both atomically**. Never publish the
manifest before its tag/APK is available. Increasing version codes are required.
The mutable `AutoPilot-Pixel10.apk` alias is not used by the worker. Its pinned
certificate is the project's existing development certificate, not a new
production signing identity. A checksum protects artifact integrity; trusted
HTTPS and the pinned APK signer provide the corresponding source checks.

Worker code is **not** self-updated from GitHub. Upgrade its reviewed installer
bundle explicitly to change deployment behavior. Future k3s hosting would need
persistent ADB keys, one worker, LAN/mDNS networking, and a new one-time pairing;
this delivery installs the recommended Ubuntu worker only.

## Validation

`python3 -m unittest discover -s tools/deployment/tests -v` exercises rejection,
identity, discovery, activity gating, version, download and installation paths.
`publish-update.py 0.4.7 --check` verifies the real published APK against its hash,
certificate and metadata. Unit syntax and shell syntax are checked separately.
Home-LAN discovery and actual installation still need execution on the home
Ubuntu machine; cloud tests cannot establish phone connectivity.

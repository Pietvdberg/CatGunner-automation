#!/usr/bin/env bash
# Run as the Ubuntu user who already paired the phone, not with sudo.
set -euo pipefail
if [[ $(id -u) == 0 ]]; then
  echo 'Run this script as your normal paired Ubuntu user, without sudo.' >&2
  exit 1
fi
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
sudo apt-get update
sudo apt-get install -y adb apksigner aapt default-jre-headless python3
worker_dir="$HOME/.local/share/catgunner-updater"
state_dir="$HOME/.local/state/catgunner-updater"
unit_dir="$HOME/.config/systemd/user"
install -d -m 700 "$worker_dir" "$state_dir" "$HOME/.android"
install -d "$unit_dir"
# Check before changing an existing timer; manual pairing is intentionally never automated.
if [[ ! -f "$state_dir/config.json" ]]; then
  XDG_STATE_HOME="$HOME/.local/state" python3 "$source_dir/updater.py" enroll
fi
install -m 600 "$source_dir/updater.py" "$worker_dir/updater.py"
install -m 644 "$source_dir/catgunner-updater.service" "$unit_dir/"
install -m 644 "$source_dir/catgunner-updater.timer" "$unit_dir/"
systemctl --user daemon-reload
sudo loginctl enable-linger "$(id -un)"
systemctl --user enable --now catgunner-updater.timer
systemctl --user start catgunner-updater.service
systemctl --user list-timers catgunner-updater.timer --no-pager
printf '\nUpdater installed. Logs: journalctl --user -u catgunner-updater.service -n 30\n'

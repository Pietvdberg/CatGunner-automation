#!/usr/bin/env python3
"""Run the read-only native OCR fixture probe on an explicitly connected emulator."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=5555, type=int)
    parser.add_argument("--quick", action="store_true")
    parser.add_argument("--fixture", choices=["available", "power-unavailable"], default="available")
    parser.add_argument("--output", default="dist/native-ocr-probe.json", type=Path)
    args = parser.parse_args()
    from adb_shell.adb_device import AdbDeviceTcp

    root = Path(__file__).resolve().parent.parent
    device = AdbDeviceTcp(args.host, args.port, default_transport_timeout_s=180)
    device.connect(auth_timeout_s=60, read_timeout_s=60)

    def shell(command, timeout=240):
        return device.shell(command, transport_timeout_s=180,
                            read_timeout_s=180, timeout_s=timeout)

    if shell("getprop ro.kernel.qemu").strip() != "1":
        raise RuntimeError("This helper only installs on an Android emulator.")
    for local, target in [
        (root / "app/build/outputs/apk/debug/app-x86_64-debug.apk", "probe-app.apk"),
        (root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk", "probe-test.apk"),
    ]:
        print(f"Installing {local.name}", flush=True)
        remote = f"/data/local/tmp/{target}"
        device.push(str(local), remote, transport_timeout_s=180, read_timeout_s=180)
        result = shell(f"pm install -r -t {remote}")
        if "Success" not in result:
            raise RuntimeError(result)
    command = "am instrument -w -r "
    if args.quick:
        command += f"-e quick true -e fixture {args.fixture} "
    command += ("nl.pietvdberg.catgunner.autopilot.test/"
                "nl.pietvdberg.catgunner.autopilot.probe.OcrProbe")
    print("Running native OCR; use one ADB connection to this emulator.", flush=True)
    result = shell(command, timeout=1200)
    print(result, flush=True)
    prefix = "INSTRUMENTATION_RESULT: results="
    rows = next((json.loads(line[len(prefix):]) for line in result.splitlines()
                 if line.startswith(prefix)), None)
    if rows is None:
        raise RuntimeError("The probe did not return structured results.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, indent=2) + "\n")
    if "INSTRUMENTATION_CODE: -1" not in result:
        raise RuntimeError("The native probe failed; partial results were retained.")


if __name__ == "__main__":
    main()

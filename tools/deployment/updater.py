#!/usr/bin/env python3
"""Install published, verified AutoPilot APKs onto one enrolled wireless device."""
import argparse
import fcntl
import hashlib
import ipaddress
import json
import logging
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request

PACKAGE = 'nl.pietvdberg.catgunner.autopilot'
SIGNER = '59ba0330a8b1028572a87d34e59453c1c5b0ff3731d19d2ac36736c15623a997'
BASE = 'https://raw.githubusercontent.com/Pietvdberg/CatGunner-automation/'
MANIFEST = BASE + 'refs/heads/main/downloads/update.json'
STATE = Path(os.environ.get('XDG_STATE_HOME', str(Path.home() / '.local/state'))) / 'catgunner-updater'
LOG = logging.getLogger('updater')


class Pending(Exception):
    """An expected condition to check again on the next timer tick."""


def run(*args, timeout=30):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed: {(result.stdout + result.stderr).strip()[:1500]}')
    return result.stdout


def adb(*args, timeout=30):
    return run('adb', *args, timeout=timeout)


def shell(device, *args):
    return adb('-s', device, 'shell', *args)


def atomic_json(path, value):
    with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        json.dump(value, handle, indent=2)
        handle.write('\n')
    temporary.replace(path)


def endpoint(value):
    """Only LAN unicast IPv4 endpoints; never pass arbitrary text to adb connect."""
    match = re.fullmatch(r'(\d+\.\d+\.\d+\.\d+):(\d{1,5})', value)
    if not match:
        raise ValueError('Expected a LAN IPv4:port endpoint')
    address = ipaddress.ip_address(match[1])
    private = any(address in ipaddress.ip_network(net) for net in
                  ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
    if not private or not 1 <= int(match[2]) <= 65535:
        raise ValueError('Endpoint must be a private LAN address with a valid port')
    return value


def devices():
    return [line.split()[0] for line in adb('devices').splitlines()
            if re.fullmatch(r'\S+\s+device', line)]


def reconnect(config):
    candidates = set()
    if config.get('endpoint'):
        candidates.add(endpoint(config['endpoint']))
    # Discovery is optional: already-connected devices and a fixed endpoint still work.
    try:
        for line in adb('mdns', 'services').splitlines():
            fields = line.split()
            if len(fields) >= 3 and fields[-2].rstrip('.') == '_adb-tls-connect._tcp':
                try:
                    candidates.add(endpoint(fields[-1]))
                except ValueError:
                    pass
    except (RuntimeError, subprocess.TimeoutExpired):
        LOG.info('ADB mDNS unavailable; trying existing connections/configured endpoint')
    for candidate in sorted(candidates):
        try:
            adb('connect', candidate, timeout=8)
        except (RuntimeError, subprocess.TimeoutExpired):
            pass


def connect(config):
    reconnect(config)
    matches = []
    for device in devices():
        try:
            if shell(device, 'getprop', 'ro.serialno').strip() == config['device_serial']:
                matches.append(device)
        except (RuntimeError, subprocess.TimeoutExpired):
            continue
    if not matches:
        raise Pending('Enrolled phone unavailable; update remains pending')
    return matches[0]


def enroll(args):
    reconnect({})
    candidates = devices()
    if args.serial:
        candidates = [device for device in candidates if device == args.serial]
    if len(candidates) != 1:
        raise RuntimeError('Connect one phone with adb first, or select it with enroll --serial ADB_SERIAL')
    device = candidates[0]
    serial = shell(device, 'getprop', 'ro.serialno').strip()
    if not re.fullmatch(r'[A-Za-z0-9._-]{4,128}', serial):
        raise RuntimeError('Cannot read a stable device identity; not enrolling')
    config = {'device_serial': serial}
    if args.endpoint:
        config['endpoint'] = endpoint(args.endpoint)
    atomic_json(STATE / 'config.json', config)
    LOG.info('Phone enrolled. Existing Ubuntu ADB keys are retained; no keys are copied.')


def manifest(data):
    if data.get('schema') != 1 or data.get('package') != PACKAGE:
        raise ValueError('Unsupported update manifest/package')
    if not re.fullmatch(r'\d+\.\d+\.\d+', data.get('version', '')):
        raise ValueError('Invalid published version')
    if type(data.get('version_code')) is not int or data['version_code'] <= 0:
        raise ValueError('Invalid version code')
    if not re.fullmatch(r'[0-9a-f]{64}', data.get('sha256', '')):
        raise ValueError('Invalid checksum')
    if not re.fullmatch(r'[A-Za-z0-9._-]+', data.get('version_name', '')):
        raise ValueError('Invalid Android version name')
    return data


def fetch(url):
    response = urllib.request.urlopen(url, timeout=30)
    if not response.geturl().startswith(BASE):
        response.close()
        raise ValueError('Unexpected download redirect')
    return response


def published():
    with fetch(MANIFEST) as response:
        data = response.read(16385)
    if len(data) > 16384:
        raise ValueError('Update manifest too large')
    return manifest(json.loads(data))


def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def download(info):
    path = STATE / f"AutoPilot-Pixel10-{info['version']}.apk"
    if path.exists() and checksum(path) == info['sha256']:
        return path
    url = BASE + f"refs/tags/v{info['version']}/downloads/{path.name}"
    temporary = None
    try:
        with fetch(url) as response, tempfile.NamedTemporaryFile(dir=STATE, delete=False) as handle:
            temporary = Path(handle.name)
            total = 0
            for chunk in iter(lambda: response.read(1024 * 1024), b''):
                total += len(chunk)
                if total > 80 * 1024 * 1024:
                    raise ValueError('APK exceeds download limit')
                handle.write(chunk)
        if checksum(temporary) != info['sha256']:
            raise ValueError('APK checksum mismatch')
        temporary.replace(path)
        return path
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def verify_apk(path, info):
    output = run('apksigner', 'verify', '--print-certs', str(path), timeout=60)
    signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', output, re.M)
    if [value.lower() for value in signers] != [SIGNER]:
        raise ValueError('APK signing certificate does not match the pinned AutoPilot certificate')
    badging = run('aapt', 'dump', 'badging', str(path))
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not match or (match[1], int(match[2]), match[3]) != (
            PACKAGE, info['version_code'], info['version_name']):
        raise ValueError('APK package/version does not match publication metadata')
    if "'arm64-v8a'" not in badging:
        raise ValueError('APK does not include the phone ABI')


def installed_version(device):
    output = shell(device, 'dumpsys', 'package', PACKAGE)
    if f'Package [{PACKAGE}]' not in output:
        raise Pending('AutoPilot is not installed; first installation must be done explicitly')
    versions = set(re.findall(r'\bversionCode=(\d+)\b', output))
    if len(versions) != 1:
        raise RuntimeError('Cannot determine installed version; refusing installation')
    return int(versions.pop())


def require_idle(device):
    services = shell(device, 'dumpsys', 'activity', 'services', PACKAGE)
    if 'ACTIVITY MANAGER SERVICES' not in services:
        raise RuntimeError('Cannot inspect Android services; refusing installation')
    if 'CaptureService' in services:
        raise Pending('Screen capture service is running; stop capture to allow the update')
    activities = shell(device, 'dumpsys', 'activity', 'activities')
    resumed = re.findall(r'^.*(?:topResumedActivity|mResumedActivity|ResumedActivity):.*$', activities, re.M)
    if not resumed:
        raise Pending('Cannot establish foreground activity; deferring update')
    if any(PACKAGE in line for line in resumed):
        raise Pending('AutoPilot is open; leave its screen to allow the update')


def check():
    config = json.loads((STATE / 'config.json').read_text())
    device = connect(config)
    info = published()
    current = installed_version(device)
    if current >= info['version_code']:
        LOG.info('Up to date: installed code %s, published code %s', current, info['version_code'])
        return
    apk = download(info)
    verify_apk(apk, info)
    # Recheck after potentially slow download/verification, immediately before replacement.
    require_idle(device)
    if installed_version(device) >= info['version_code']:
        return
    require_idle(device)
    output = adb('-s', device, 'install', '-r', str(apk), timeout=180)
    if not re.search(r'^Success\s*$', output, re.M):
        raise RuntimeError(f'Installation did not report success: {output[:1500]}')
    if installed_version(device) != info['version_code']:
        raise RuntimeError('Post-install version check failed')
    atomic_json(STATE / 'last-install.json', {'version': info['version'], 'version_code': info['version_code'],
                                            'sha256': info['sha256']})
    # Keep only the currently verified APK; state is never used as proof of installation.
    for old in STATE.glob('AutoPilot-Pixel10-*.apk'):
        if old != apk:
            old.unlink()
    LOG.info('Installed AutoPilot %s. Open the app and grant screen capture when needed.', info['version'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    enrollment = commands.add_parser('enroll')
    enrollment.add_argument('--serial', help='ADB device selector when multiple devices are connected')
    enrollment.add_argument('--endpoint', help='Optional fixed LAN IPv4:connection-port fallback')
    commands.add_parser('check')
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    os.umask(0o077)
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (STATE / 'worker.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            LOG.info('Another updater invocation is active')
            return 0
        try:
            if args.command == 'enroll':
                enroll(args)
            else:
                check()
        except Pending as error:
            LOG.info('Pending: %s', error)
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
            LOG.error('%s', error)
            return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

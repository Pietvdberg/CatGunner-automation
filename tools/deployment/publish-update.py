#!/usr/bin/env python3
"""Publish metadata only after verifying the versioned Pixel APK. Tag it with the same commit."""
import argparse
import json
from pathlib import Path
import re

import updater

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('version', help='Published numeric version, e.g. 0.4.8')
parser.add_argument('--check', action='store_true', help='Validate the current publication without writing')
args = parser.parse_args()
if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
    parser.error('Expected a numeric three-part version')
root = Path(__file__).resolve().parents[2]
apk = root / 'downloads' / f'AutoPilot-Pixel10-{args.version}.apk'
output = updater.run('aapt', 'dump', 'badging', str(apk))
match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output, re.M)
if not match or match[1] != updater.PACKAGE:
    raise SystemExit('Unexpected APK package/metadata')
info = updater.manifest({'schema': 1, 'package': match[1], 'version': args.version,
                         'version_code': int(match[2]), 'version_name': match[3],
                         'sha256': updater.checksum(apk)})
updater.verify_apk(apk, info)
manifest = root / 'downloads/update.json'
if args.check:
    if json.loads(manifest.read_text()) != info:
        raise SystemExit('Published update manifest does not match the verified APK')
    print('Publication checksum, package, version, ABI and pinned signing certificate verified.')
else:
    manifest.write_text(json.dumps(info, indent=2) + '\n')
    print(f'Prepared {manifest.name}; publish APK and metadata with immutable tag v{args.version}.')

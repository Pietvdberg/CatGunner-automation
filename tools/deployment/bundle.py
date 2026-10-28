#!/usr/bin/env python3
"""Create or check the reproducible small Ubuntu installer bundle."""
import argparse
import gzip
import io
from pathlib import Path
import tarfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
files = ['tools/deployment/updater.py', 'tools/deployment/install-ubuntu.sh',
         'tools/deployment/catgunner-updater.service', 'tools/deployment/catgunner-updater.timer',
         'docs/ubuntu-updater.md']
stream = io.BytesIO()
with tarfile.open(fileobj=stream, mode='w', format=tarfile.USTAR_FORMAT) as archive:
    for name in files:
        contents = (root / name).read_bytes()
        entry = tarfile.TarInfo(Path(name).name)
        entry.size = len(contents)
        entry.mode = 0o700 if name.endswith('.sh') else 0o600
        archive.addfile(entry, io.BytesIO(contents))
compressed_stream = io.BytesIO()
with gzip.GzipFile(filename='', fileobj=compressed_stream, mode='wb', mtime=0) as zipped:
    zipped.write(stream.getvalue())
compressed = compressed_stream.getvalue()
target = root / 'downloads/ubuntu-updater-1.0.1.tar.gz'
if args.check:
    if target.read_bytes() != compressed:
        raise SystemExit('Installer bundle is stale. Run python3 tools/deployment/bundle.py')
    print('Installer bundle matches its source files.')
else:
    target.write_bytes(compressed)
    print(f'Created {target.name} ({len(compressed)} bytes)')

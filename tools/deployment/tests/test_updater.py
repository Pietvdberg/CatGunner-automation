"""Deployment policy tests: no device or network access."""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('updater', Path(__file__).parents[1] / 'updater.py')
u = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(u)
INFO = json.loads((Path(__file__).parents[3] / 'downloads/update.json').read_text())


class UpdaterTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.state = Path(self.directory.name)
        self.patch('STATE', self.state)
        (self.state / 'config.json').write_text(json.dumps({'device_serial': 'PHONE123'}))

    def patch(self, name, *args, **kwargs):
        context = patch.object(u, name, *args, **kwargs)
        self.addCleanup(context.stop)
        return context.start()

    def flow(self, version=13):
        self.patch('connect', return_value='phone:1234')
        self.patch('published', return_value=INFO)
        self.version = self.patch('installed_version', return_value=version)
        self.download = self.patch('download', return_value=self.state / 'app.apk')
        self.verify = self.patch('verify_apk')
        self.idle = self.patch('require_idle')
        self.adb = self.patch('adb', return_value='Success\n')

    def test_reject_manifest_path_injection_and_bad_types(self):
        for field, value in [('schema', 2), ('package', 'other'), ('version', '../evil'),
                             ('version_code', True), ('sha256', 'a' * 63), ('version_name', "x' y")]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                u.manifest({**INFO, field: value})

    def test_restrict_endpoints(self):
        self.assertEqual(u.endpoint('192.168.1.82:43210'), '192.168.1.82:43210')
        for value in ['8.8.8.8:5555', '127.0.0.1:5555', '192.168.1.82:0',
                      '192.168.1.82:65536', '192.168.1.82:5555;reboot', 'evil:5555']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                u.endpoint(value)

    def test_unknown_device_never_selected(self):
        self.patch('adb', return_value='')
        self.patch('devices', return_value=['other:5555'])
        self.patch('shell', return_value='OTHERPHONE')
        with self.assertRaises(u.Pending):
            u.connect({'device_serial': 'PHONE123'})

    def test_discovery_ignores_pairing_and_selects_enrolled_identity(self):
        adb = self.patch('adb', return_value='one _adb-tls-pairing._tcp 192.168.1.2:1234\n'
                         'two _adb-tls-connect._tcp 192.168.1.82:4567\n')
        self.patch('devices', return_value=['other', 'phone'])
        self.patch('shell', side_effect=['OTHER', 'PHONE123'])
        self.assertEqual(u.connect({'device_serial': 'PHONE123'}), 'phone')
        adb.assert_any_call('connect', '192.168.1.82:4567', timeout=8)
        self.assertEqual(adb.call_count, 2)

    def enrollment(self, selectors, identities, selected=None):
        self.patch('reconnect')
        self.patch('devices', return_value=selectors)
        self.patch('shell', side_effect=identities)
        return argparse.Namespace(serial=selected, endpoint=None)

    def test_enroll_duplicate_aliases_of_one_phone(self):
        args = self.enrollment(['192.168.1.82:36141', 'adb-phone._adb-tls-connect._tcp'],
                               ['PHONE123', 'PHONE123'])
        u.enroll(args)
        config = json.loads((self.state / 'config.json').read_text())
        self.assertEqual(config, {'device_serial': 'PHONE123', 'endpoint': '192.168.1.82:36141'})

    def test_enroll_refuses_multiple_physical_phones_without_overwriting_config(self):
        args = self.enrollment(['phone1', 'phone2'], ['PHONE123', 'PHONE456'])
        before = (self.state / 'config.json').read_text()
        with self.assertRaisesRegex(RuntimeError, 'Multiple physical devices'):
            u.enroll(args)
        self.assertEqual((self.state / 'config.json').read_text(), before)

    def test_enroll_explicit_alias_with_other_phone_connected(self):
        args = self.enrollment(['phone1', 'phone2'], ['PHONE456'], selected='phone2')
        u.enroll(args)
        self.assertEqual(json.loads((self.state / 'config.json').read_text()),
                         {'device_serial': 'PHONE456'})

    def test_enroll_no_connection_explains_reconnect(self):
        args = self.enrollment([], [])
        with self.assertRaisesRegex(RuntimeError, 'adb connect'):
            u.enroll(args)

    def test_enroll_unreadable_identity_refuses(self):
        args = self.enrollment(['phone1'], [''])
        with self.assertRaisesRegex(RuntimeError, 'stable device identity'):
            u.enroll(args)

    def test_device_listing_tolerates_trailing_spaces_and_ignores_unauthorized(self):
        self.patch('adb', return_value='List of devices attached\n'
                   '192.168.1.82:36141\tdevice  \n'
                   'adb-phone._adb-tls-connect._tcp device\n'
                   'offline-phone offline\nunauthorized-phone unauthorized\n')
        self.assertEqual(u.devices(), ['192.168.1.82:36141', 'adb-phone._adb-tls-connect._tcp'])

    def test_active_capture_blocks_install(self):
        self.patch('shell', return_value='ACTIVITY MANAGER SERVICES\nServiceRecord{CaptureService}')
        with self.assertRaises(u.Pending):
            u.require_idle('phone')

    def test_foreground_app_blocks_install(self):
        self.patch('shell', side_effect=['ACTIVITY MANAGER SERVICES\n(nothing)',
                                        f'topResumedActivity: ActivityRecord{{{u.PACKAGE}/.ui.MainActivity}}'])
        with self.assertRaises(u.Pending):
            u.require_idle('phone')

    def test_unknown_service_or_foreground_state_blocks(self):
        for outputs, exception in [(['permission denied'], RuntimeError),
                                   (['ACTIVITY MANAGER SERVICES', 'unknown'], u.Pending)]:
            with self.subTest(outputs=outputs):
                self.patch('shell', side_effect=outputs)
                with self.assertRaises(exception):
                    u.require_idle('phone')

    def test_idle_accessibility_service_does_not_block(self):
        self.patch('shell', side_effect=['ACTIVITY MANAGER SERVICES\nGameAccessibilityService',
                                        'mResumedActivity: ActivityRecord{com.termux/.app.TermuxActivity}'])
        u.require_idle('phone')

    def test_reject_wrong_signer_and_apk_metadata(self):
        metadata = (f"package: name='{u.PACKAGE}' versionCode='14' versionName='0.4.7-ocr-worker'\n"
                    "native-code: 'arm64-v8a'\n")
        for signer, badging in [('a' * 64, metadata), (u.SIGNER, metadata.replace("='14'", "='15'")),
                                (u.SIGNER, metadata.replace(u.PACKAGE, 'other')),
                                (u.SIGNER, metadata.replace('arm64-v8a', 'x86'))]:
            with self.subTest(signer=signer, badging=badging):
                self.patch('run', side_effect=[f'Signer #1 certificate SHA-256 digest: {signer}\n', badging])
                with self.assertRaises(ValueError):
                    u.verify_apk(Path('file.apk'), INFO)

    def test_signature_failure_does_not_install(self):
        self.flow()
        self.verify.side_effect = subprocess.TimeoutExpired('apksigner', 60)
        with self.assertRaises(subprocess.TimeoutExpired):
            u.check()
        self.adb.assert_not_called()

    def test_already_current_or_newer_never_downloads_or_installs(self):
        self.flow(version=15)
        u.check()
        self.download.assert_not_called()
        self.adb.assert_not_called()

    def test_pending_capture_does_not_install(self):
        self.flow()
        self.idle.side_effect = u.Pending('capture running')
        with self.assertRaises(u.Pending):
            u.check()
        self.adb.assert_not_called()

    def test_success_replaces_without_downgrade_or_launch_flags(self):
        self.flow()
        self.version.side_effect = [13, 13, 14]
        u.check()
        self.assertEqual(self.idle.call_count, 2)
        self.adb.assert_called_once_with('-s', 'phone:1234', 'install', '-r',
                                         str(self.state / 'app.apk'), timeout=180)
        self.assertEqual(json.loads((self.state / 'last-install.json').read_text())['version_code'], 14)

    def test_install_error_or_postcheck_failure_never_records_success(self):
        self.flow()
        self.adb.return_value = 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]'
        with self.assertRaises(RuntimeError):
            u.check()
        self.assertFalse((self.state / 'last-install.json').exists())
        self.adb.return_value = 'Success\n'
        with self.assertRaises(RuntimeError):
            u.check()
        self.assertFalse((self.state / 'last-install.json').exists())

    def test_corrupt_download_never_promoted(self):
        from io import BytesIO
        self.patch('fetch', return_value=BytesIO(b'corrupted apk'))
        with self.assertRaises(ValueError):
            u.download(INFO)
        self.assertEqual(list(self.state.glob('*.apk')), [])
        self.assertEqual(sorted(p.name for p in self.state.iterdir()), ['config.json'])

    def test_cached_apk_rehashed_before_reuse(self):
        from io import BytesIO
        cached = self.state / f"AutoPilot-Pixel10-{INFO['version']}.apk"
        cached.write_bytes(b'corrupt')
        fetch = self.patch('fetch', return_value=BytesIO(b'also corrupt'))
        with self.assertRaises(ValueError):
            u.download(INFO)
        fetch.assert_called_once()


if __name__ == '__main__':
    unittest.main()

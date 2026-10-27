# Native OCR fixture probe

The diagnostic instrumentation APK runs bundled ML Kit on the two existing
570×150 RGB footer fixtures. It preserves the original 1130–1265 strip, tests
reference and enlarged native-like resolutions, and includes a test-only large
stat masking comparison. It also exercises the production worker on one fixture.
No accessibility/capture permission, network or purchase action is used. Main APK
assets contain no probe fixtures. Only the test APK has these assets and runner.

Build with `tools/cloud-gradle.sh -PsplitApks assembleDebug assembleDebugAndroidTest`.
The helper `tools/run-ocr-probe.py` uses Python adb-shell 0.4.4 and refuses physical
devices (`ro.kernel.qemu` must be 1). Default connection is localhost:5555; keep one
ADB connection while it runs. Use `--quick` for one full-strip case plus the
production worker. Default output is dist/native-ocr-probe.json. JSON is numeric
readings and raw game text, not images. A run can complete while strict parsing
rejects an individual frame; evaluate expected levels/costs, not only its exit code.

Cloud setup used official SDK packages `emulator` and
`system-images;android-30;default;x86_64`, with a Pixel 4 AVD in workspace tooling.
No /dev/kvm is available. Headless software emulation uses `-no-window -no-audio
-no-boot-anim -no-snapshot -accel off -gpu swiftshader -memory 2048 -cores 2
-port 5554 -skip-adb-auth`. Skip-auth is only for this disposable local test emulator.
ANDROID_USER_HOME/ANDROID_AVD_HOME/ANDROID_EMULATOR_HOME point under workspace.
Native adb requires an unwritable home here; the helper uses the direct Python
transport without changing HOME. For this session dependencies are installed in
/workspace/tooling/python-libs, so invoke the helper with PYTHONPATH set there.
Do not run this helper against a phone or expose the test emulator externally.

Software emulator wall times, cold model loading and synthetic enlargement cannot
establish Pixel 10 performance. Native parsing evidence can identify SDK-level
recognition failures that JVM token/geometry tests cannot. Test-only masking is
not enabled in the application. The actual phone remains necessary for throughput,
rendering, interruption and thermal validation. CI builds the test APK but does
not claim to execute these native checks without an emulator.

Native JSON evidence is retained under docs/native-ocr/. baseline.json records
the original strict parsing behavior; marker-proof.json records the full probe
with image-backed marker recovery; final-heading.json records the final unmasked
1080-wide unavailable fixture and production worker. These are sequential software
emulator runs, not a controlled timing benchmark. The synthetic enlarged available
fixture still yields price 2.621 instead of 2.62i in one native output; prices are
not corrected, and mixed suffix comparisons remain blocked. Known level values
are checked separately from complete generic parsing. A masking experiment still
fails one fixture and is not enabled in production. --quick --fixture
power-unavailable selects the final unavailable case explicitly.

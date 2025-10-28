# EMV TLV Parser & Android NFC Logger

Pixel 7_pro Emulator
<img width="405" height="851" alt="Screenshot 2025-10-28 at 4 16 30 PM" src="https://github.com/user-attachments/assets/da880838-9447-45e1-8117-9de436189450" />


## Overview
This repository houses the assessment deliverables for the EMV TLV parser (Task 1) and the upcoming Android NFC logger (Task 2). A shared `parser` module contains the reusable BER-TLV decoding and EMV interpretation logic that powers both the CLI tool and, later, the Android app.

```
emvNfc/
├─ app/                  # Android module (Empty Activity template)
├─ parser/               # Shared Kotlin JVM parser/interpretation logic
├─ cli-tool/             # JVM CLI front-end consuming the parser
├─ prod_spec/            # Planning & documentation assets
└─ README.md             # This file
```

## Prerequisites
- macOS with Android Studio (bundled JDK) **or** any locally installed JDK 17+
- Kotlin 2.0.21 / Gradle 8.13 (managed via Gradle Wrapper)

If you rely on Android Studio’s bundled JDK, point `JAVA_HOME` to it before running Gradle:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```
Add the exports to `~/.zshrc` (or your preferred shell profile) so they persist across sessions.

## Build & Run Instructions
### CLI (Task 1)
Run the CLI with the assessment’s sample TLV (demonstrates malformed-length handling):

```bash
./gradlew :cli-tool:run --args "6F1A8407A0000000031010A511500B5649534120435245444954"
```

Or start it in interactive mode (paste TLV data and press `Ctrl+D`/`Cmd+D` to finish):

```bash
./gradlew :cli-tool:run
```

To see a successful parse, run the CLI with a corrected TLV string (length fields adjusted to match the payload):

```bash
./gradlew :cli-tool:run --args "6F188407A0000000031010A50D500B5649534120435245444954"
```

<img width="873" height="129" alt="Screenshot 2025-10-28 at 4 18 30 PM" src="https://github.com/user-attachments/assets/4b1a448e-9568-4b69-b24f-5762cde6b54e" />


### Parser Library
The `parser` module exposes:
- `TlvParser` – BER-TLV decoding with multi-byte tag/length support and error reporting.
- `TagInterpreter` – EMV-specific interpretation and masking helpers.

These classes are reusable from both the CLI and the Android app.

### Android App (Task 2)
The `app` module currently contains the Empty Activity scaffold from Android Studio. NFC reader functionality will be implemented after Task 1 is finalized.

#### Build & Run
1. Open the project in Android Studio Iguana or newer (includes JDK 21). If you prefer the CLI:
   ```bash
   ./gradlew :app:assembleDebug
   ```
2. Install and run the debug APK on an NFC-capable device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n "com.example.emvnfc/com.example.emvnfc.MainActivity"
   ```
3. Grant NFC permission when prompted and ensure NFC is enabled in device settings.

#### Features Implemented
- Reader mode activation via `NfcAdapter.enableReaderMode`, fetching PPSE ➜ application ➜ READ RECORD TLVs.
- Shared parser module formats and masks values before display.
- Local log persistence to `files/logs/emv_logs.json` (app-internal storage).
- **Load sample TLV** button for development without physical cards.
- **Share logs** exports the JSON file through a FileProvider share sheet; falls back to plain-text if no file exists yet.
- **Verbose TLV mode** toggle surfaces the most recent transaction’s full interpreted TLV list.

### JVM Unit Tests
To run the parser/interpretation tests (covers success paths, malformed TLVs, invalid hex, and multi-byte tags):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :parser:test
```

If `JAVA_HOME` is not configured, Gradle will exit with `Unable to locate a Java Runtime` before tests execute. Rerun after exporting the Android Studio JDK or another JDK 17+.

### Android Instrumentation Tests
To execute the masking regression test on an attached device or emulator:

```bash
./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.emvnfc.NfcMaskingInstrumentedTest
```

This ensures the Android layer still receives masked PAN output (bullet characters) from the shared parser.

## Example Input & Output

### Spec TLV & Error Handling

```
$ ./gradlew :cli-tool:run --args "6F1A8407A0000000031010A511500B5649534120435245444954"
Error: Declared length exceeds available bytes for tag 6F
```

Explanation: tag `6F` advertises `0x1A` (26) bytes, but only 24 bytes follow, so `TlvParser` raises `MalformedTlvException` @parser/src/main/kotlin/com/example/emvnfc/parser/TlvParser.kt#18-28. The nested `A5` template has a similar mismatch (`0x11` vs actual 13 bytes). This confirms the requirement to flag malformed TLVs.

### Corrected TLV (Aligned Length Fields)

```
$ ./gradlew :cli-tool:run --args "6F188407A0000000031010A50D500B5649534120435245444954"
Tag | Length | Value                                            | Interpretation
----+--------+--------------------------------------------------+-------------------------------------------------
6F  | 24     | 8407A0000000031010A50D500B5649534120435245444954 | 8407A0000000031010A50D500B5649534120435245444954
```

You can further test decoded, interpreted tags (e.g., `9F02`, `5A`, `9F26`, `9F10`, `84`) using a composite TLV payload that references each tag.

## Assumptions & Notes
1. **Spec sample correction:** The provided example TLV in the assessment doc has mismatched length fields; the parser intentionally rejects it. A corrected version (`6F1884…`) is used for manual verification.
2. **Currency handling:** Amount interpretations assume two decimal places (major/minor currency units) per EMV convention.
3. **PAN masking:** The CLI displays masked PANs (`6 leading / 4 trailing digits`), ensuring sensitive data is obscured.
4. **Unicode bullet mask:** The middle digits are replaced with Unicode bullet characters (`•`) for clarity in logs/CLI output.


## APK
https://drive.google.com/file/d/1yo4EccJoE8pvGImyA41Xl7JTq7YIv-hc/view?usp=sharing


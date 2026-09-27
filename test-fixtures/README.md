# Synthetic Android download fixtures

Two small native test apps labelled Qwen Test and ChatGPT Test exercise the actual Android app-launch, usage-event and shared-storage paths. They are stand-ins, not AI provider apps, and use no network or account. Their exports use MediaStore Downloads with pending/completed writes to Downloads/FileMateTests.

The fixtures export a clearly named AI file, an unrelated receipt and an ambiguous generic file. The tests must distinguish fixture evidence from downloads by real ChatGPT/Qwen apps on a physical phone. These APKs are never included in the user-facing FileMate APK.

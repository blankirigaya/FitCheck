The Phase 1 model is intentionally not bundled in the APK. Reuse the verified
local file named `gemma-3n-E4B-it-int4.litertlm` (4,919,541,760 bytes) and push
it to the app-specific external model directory:

    adb shell mkdir -p /sdcard/Android/data/com.fitcheck.app/files/models
    adb push gemma-3n-E4B-it-int4.litertlm /sdcard/Android/data/com.fitcheck.app/files/models/
    adb shell ls -l /sdcard/Android/data/com.fitcheck.app/files/models/gemma-3n-E4B-it-int4.litertlm

After pushing, open AI Lab, tap Refresh, then Initialize. The runtime probes
that directory through Android APIs and uses the file in place; it does not
make a second 4.58 GB copy.

If the file is absent from the local machine, AI Lab correctly reports
`No .litertlm model found`; restore the existing verified file before changing
the app or downloading another model.

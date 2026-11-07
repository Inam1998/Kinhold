#!/bin/bash
# Puts the tree saved by phone-backup.sh back into the freshly reinstalled app (install the APK first, then run this).
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
SERIAL=R5CT40PGEHF
SRC="${1:-/d/Android App Releases/Kinhold/phone-data}"
timeout 20 "$A" -s $SERIAL shell am force-stop com.inamibrahim.kinhold
timeout 20 "$A" -s $SERIAL shell run-as com.inamibrahim.kinhold mkdir -p databases files
for f in kinhold.db kinhold.db-wal kinhold.db-shm; do
  if [ -s "$SRC/$f" ]; then timeout 60 "$A" -s $SERIAL exec-in run-as com.inamibrahim.kinhold sh -c "cat > databases/$f" < "$SRC/$f" 2>/dev/null || cat "$SRC/$f" | timeout 60 "$A" -s $SERIAL shell "run-as com.inamibrahim.kinhold sh -c 'cat > databases/$f'"; fi
done
[ -s "$SRC/safety-before-restore.json" ] && cat "$SRC/safety-before-restore.json" | timeout 60 "$A" -s $SERIAL shell "run-as com.inamibrahim.kinhold sh -c 'cat > files/safety-before-restore.json'"
echo "restored; open the app"

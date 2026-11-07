#!/bin/bash
# Copies the Kinhold tree off the phone BEFORE running connected tests (they uninstall the app and delete its data).
# Usage: scripts/phone-backup.sh [folder]   (default: D:/Android App Releases/Kinhold/phone-data)
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
SERIAL=R5CT40PGEHF
DEST="${1:-/d/Android App Releases/Kinhold/phone-data}"
mkdir -p "$DEST"
for f in kinhold.db kinhold.db-wal kinhold.db-shm; do
  timeout 60 "$A" -s $SERIAL exec-out run-as com.inamibrahim.kinhold cat databases/$f > "$DEST/$f" 2>/dev/null || rm -f "$DEST/$f"
done
timeout 60 "$A" -s $SERIAL exec-out run-as com.inamibrahim.kinhold cat files/safety-before-restore.json > "$DEST/safety-before-restore.json" 2>/dev/null || rm -f "$DEST/safety-before-restore.json"
ls -la "$DEST"

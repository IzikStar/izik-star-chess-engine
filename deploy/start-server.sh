#!/usr/bin/env bash
# Starts the game server on this machine's Tailscale address (the systemd service runs this).
# Waits for Tailscale to come up after a reboot, so the server never listens on a public address.
set -euo pipefail
for _ in $(seq 60); do
    ADDR=$(tailscale ip -4 2>/dev/null | head -1 || true)
    [ -n "$ADDR" ] && break
    sleep 2
done
if [ -z "${ADDR:-}" ]; then
    echo "Tailscale has no address; not starting (is 'tailscale up' done?)" >&2
    exit 1
fi
JAR=$(ls /opt/izikstar/app/target/izikstar-chess-*.jar | head -1)
exec java -jar "$JAR" --no-browser --host "$ADDR" --port 7070 \
    --runs /var/lib/izikstar/runs --games /var/lib/izikstar/games --variants /var/lib/izikstar/variants

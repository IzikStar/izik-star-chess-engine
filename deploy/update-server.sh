#!/usr/bin/env bash
# Pulls the latest main, rebuilds and restarts the server. Runs and saved games are kept: they live
# in /var/lib/izikstar, outside the checkout. A Lab run in progress stops with the restart; resume
# it from the Lab page.
set -euo pipefail
APP=/opt/izikstar/app
git -C "$APP" fetch -q origin main
git -C "$APP" reset -q --hard origin/main
(cd "$APP" && ./mvnw -q package -DskipTests)
sudo install -m 755 "$APP/deploy/start-server.sh" /usr/local/bin/izikstar-start
sudo install -m 644 "$APP/deploy/izikstar-chess.service" /etc/systemd/system/izikstar-chess.service
sudo systemctl daemon-reload
sudo systemctl restart izikstar-chess
echo "Updated to $(git -C "$APP" log --oneline -1)"

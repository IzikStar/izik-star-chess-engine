#!/usr/bin/env bash
# Sets up IzikStar Chess on a fresh Ubuntu cloud machine (Oracle Cloud Always Free, Hetzner, ...).
# Run it once, as a user with sudo, from anywhere:
#
#   curl -fsSL https://raw.githubusercontent.com/IzikStar/izik-star-chess-engine/main/deploy/setup-server.sh | bash
#
# It installs Java 21, Stockfish, Fairy-Stockfish (built from source) and Tailscale, builds the jar,
# copies the runs that ship with the repository, and installs a service that starts the game server
# on boot and restarts it if it stops. The server listens on the machine's Tailscale address only:
# nothing is opened to the internet, and only your own devices on your tailnet can reach it.
# Running it again is safe: it updates what is there. See docs/cloud-server.md.
set -euo pipefail

REPO="${IZIKSTAR_REPO:-https://github.com/IzikStar/izik-star-chess-engine}"
BRANCH="${IZIKSTAR_BRANCH:-main}"
APP=/opt/izikstar/app          # the checkout and the jar
DATA=/var/lib/izikstar         # runs, saved games, variants, sync state
CONF=/etc/izikstar             # cloud.env (the D1 key, when you add one)
USER_NAME=izikstar

say() { printf '\n== %s\n' "$*"; }

say "Packages: Java 21, git, Stockfish, a C++ compiler for Fairy-Stockfish"
export DEBIAN_FRONTEND=noninteractive
sudo -E apt-get update -q
sudo -E apt-get install -y -q openjdk-21-jdk-headless git stockfish build-essential curl iptables-persistent

say "Tailscale"
if ! command -v tailscale >/dev/null; then
    curl -fsSL https://tailscale.com/install.sh | sh
fi
if ! tailscale ip -4 >/dev/null 2>&1; then
    echo "Open the link below and sign in with the same account as your phone:"
    sudo tailscale up --hostname izikstar-chess
fi
# Oracle's Ubuntu images reject everything but SSH in iptables; let the tailnet in (and only it).
if ! sudo iptables -C INPUT -i tailscale0 -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 1 -i tailscale0 -j ACCEPT
    sudo netfilter-persistent save
fi

say "User and folders"
id "$USER_NAME" >/dev/null 2>&1 || sudo useradd --system --home "$DATA" --shell /usr/sbin/nologin "$USER_NAME"
sudo mkdir -p "$APP" "$DATA/runs" "$DATA/games" "$DATA/variants" "$CONF"
sudo chown -R "$USER_NAME:$USER_NAME" "$DATA"
sudo chown "$(id -un)" "$APP"

say "Source"
if [ -d "$APP/.git" ]; then
    git -C "$APP" fetch -q origin "$BRANCH"
    git -C "$APP" checkout -q "$BRANCH"
    git -C "$APP" reset -q --hard "origin/$BRANCH"
else
    git clone -q --branch "$BRANCH" "$REPO" "$APP"
fi

say "Fairy-Stockfish (the yardstick for variants in the Lab)"
if [ ! -x "$APP/engine/fairy-stockfish" ]; then
    case "$(uname -m)" in
        aarch64|arm64) ARCH=armv8 ;;
        *) ARCH=x86-64-modern ;;
    esac
    tmp=$(mktemp -d)
    git clone -q --depth 1 https://github.com/fairy-stockfish/Fairy-Stockfish "$tmp/fsf"
    make -s -C "$tmp/fsf/src" -j"$(nproc)" build ARCH="$ARCH" >/dev/null
    mkdir -p "$APP/engine"
    for exe in fairy-stockfish stockfish; do
        if [ -x "$tmp/fsf/src/$exe" ]; then cp "$tmp/fsf/src/$exe" "$APP/engine/fairy-stockfish"; break; fi
    done
    rm -rf "$tmp"
fi

say "Build (the first build downloads Maven and Node.js and takes a few minutes)"
(cd "$APP" && ./mvnw -q package -DskipTests)

say "Runs that ship with the repository (copied once; the server's own runs are never overwritten)"
for f in "$APP"/runs/*.db; do
    [ -e "$f" ] || continue
    [ -e "$DATA/runs/$(basename "$f")" ] || sudo -u "$USER_NAME" cp "$f" "$DATA/runs/"
done
if [ -d "$APP/runs/hall-of-fame" ]; then
    sudo -u "$USER_NAME" mkdir -p "$DATA/runs/hall-of-fame"
    sudo -u "$USER_NAME" cp -n "$APP"/runs/hall-of-fame/* "$DATA/runs/hall-of-fame/" 2>/dev/null || true
fi

say "Service"
[ -e "$CONF/cloud.env" ] || sudo install -m 600 -o "$USER_NAME" /dev/null "$CONF/cloud.env"
sudo install -m 755 "$APP/deploy/start-server.sh" /usr/local/bin/izikstar-start
sudo install -m 644 "$APP/deploy/izikstar-chess.service" /etc/systemd/system/izikstar-chess.service
sudo systemctl daemon-reload
sudo systemctl enable -q izikstar-chess
sudo systemctl restart izikstar-chess

ADDR=$(tailscale ip -4 | head -1)
NAME=$(tailscale status --json 2>/dev/null | grep -o '"DNSName": *"[^"]*"' | head -1 | sed 's/.*"\([^.]*\)\..*/\1/')
say "Done"
echo "Open it on your phone (Tailscale on): http://${NAME:-$ADDR}:7070/   or   http://$ADDR:7070/"
echo "Logs: journalctl -u izikstar-chess -f      Update later: bash $APP/deploy/update-server.sh"

# The game on a cloud server

The jar runs the same on a small cloud machine as on your computer. The phone (or any computer)
then opens it in a browser, and Lab runs keep going on the server while the phone is off: their
results are there when you open the Lab again. The computer at home can keep running its own copy
as before.

Access is through [Tailscale](https://tailscale.com), the same as `--lan` away from home: the
server listens on its Tailscale address only (`--host`), so nothing is opened to the internet and
there is no password to set. Only devices signed in to your tailnet reach it.

## 1. A machine (Oracle Cloud Always Free)

Oracle's free tier includes an ARM machine with up to 4 cores and 24 GB of memory, free with no
time limit. That is plenty for play, analysis and Lab runs (a run plays several games at once, one
per core).

1. Sign up at <https://www.oracle.com/cloud/free/>. A credit card is asked for to check your
   identity; the Always Free resources are not charged. Pick a home region near you (it cannot
   be changed later).
2. In the console: **Compute → Instances → Create instance**.
   - **Image:** Canonical Ubuntu 24.04.
   - **Shape:** Ampere, `VM.Standard.A1.Flex`, 4 OCPUs and 24 GB memory.
   - **Networking:** keep the defaults (a public address is needed for SSH and for downloads).
   - **SSH keys:** let it generate a key pair and download the private key.
3. Create. If it says "Out of capacity", try another availability domain, or again later; free ARM
   machines are popular.
4. Connect: `ssh -i <the key> ubuntu@<the public address>`.

**Idle machines.** Oracle may reclaim an Always Free machine that stays nearly idle for a week
(CPU, network and memory all low). If that worries you, upgrade the account to *Pay As You Go*
(Billing → Upgrade): Always Free resources stay free, and idle machines are no longer reclaimed.

Any other Ubuntu machine works the same (Hetzner, a spare PC). Without Oracle's firewall rules the
script's iptables step is a no-op.

## 2. Install

On the server:

```bash
curl -fsSL https://raw.githubusercontent.com/IzikStar/izik-star-chess-engine/main/deploy/setup-server.sh | bash
```

[`deploy/setup-server.sh`](../deploy/setup-server.sh) installs Java 21, Stockfish, Fairy-Stockfish
(built from source, the Lab's yardstick for variants) and Tailscale; it prints a Tailscale sign-in
link the first time: sign in with the account your phone uses. It then builds the jar, copies the
runs that ship with the repository, and installs the `izikstar-chess` service, which starts on boot
and restarts after a crash. At the end it prints the address to open, for example
`http://izikstar-chess:7070/`.

| What | Where |
|---|---|
| Checkout and jar | `/opt/izikstar/app` |
| Runs, saved games, variants | `/var/lib/izikstar` (kept across updates) |
| Settings (the cloud database key, later) | `/etc/izikstar/cloud.env` |
| Logs | `journalctl -u izikstar-chess -f` |

## 3. Use it

Turn Tailscale on in the phone and open the printed address; "Add to Home screen" gives it an
icon. A Lab run started there runs on the server; close the phone and look again later.

## 4. Update

```bash
bash /opt/izikstar/app/deploy/update-server.sh
```

pulls `main`, rebuilds and restarts. Data in `/var/lib/izikstar` is kept. A Lab run in progress
stops with the restart; resume it from the Lab page.

# The game on a cloud server

The jar runs the same on a small cloud machine as on your computer. The phone (or any computer)
then opens it in a browser, and Lab runs keep going on the server while the phone is off: their
results are there when you open the Lab again. The computer at home can keep running its own copy
as before.

Access is through [Tailscale](https://tailscale.com), the same as `--lan` away from home: the
server listens on its Tailscale address only (`--host`), so nothing is opened to the internet and
there is no password to set. Only devices signed in to your tailnet reach it.

## 1. A machine (Oracle Cloud Always Free)

Oracle's free tier includes ARM (Ampere A1) time with no end date: 1,500 OCPU hours and 9,000 GB
hours a month, which is one machine with **2 cores and 12 GB of memory** running all month (Oracle
cut it from 4 cores and 24 GB on 15 June 2026). Enough to play, analyse and run the Lab around the
clock, two games at a time. For a bigger burst, rent an hourly machine (Hetzner, below) for the run
and delete it afterwards.

1. Sign up at <https://www.oracle.com/cloud/free/>. A credit card is asked for to check your
   identity; the Always Free resources are not charged. Pick a home region near you (it cannot
   be changed later).
2. In the console: **Compute → Instances → Create instance**.
   - **Image:** Canonical Ubuntu 24.04.
   - **Shape:** Ampere, `VM.Standard.A1.Flex`, 2 OCPUs and 12 GB memory (more is billed on a
     paid account and stopped on a free one).
   - **Networking:** keep the defaults (a public address is needed for SSH and for downloads).
   - **SSH keys:** let it generate a key pair and download the private key.
3. Create. If it says "Out of capacity", try another availability domain, or again later; free ARM
   machines are popular.
4. Connect: `ssh -i <the key> ubuntu@<the public address>`.

**Idle machines.** Oracle may reclaim an Always Free machine that stays nearly idle for a week
(CPU, network and memory all low). If that worries you, upgrade the account to *Pay As You Go*
(Billing → Upgrade): Oracle does not charge for Always Free resources after the upgrade, only for
use above them, so keep the machine at 2 OCPUs / 12 GB and set a budget alert (Billing → Budgets).
A paid account also gets past "Out of capacity" more easily.

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
| Shared database settings (section 5) | `/etc/izikstar/cloud.env` |
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

## 5. The shared database (Cloudflare D1)

Optional. When the server is the only copy that plays and stores (the phone and the computer just
open it in the browser), there is nothing to share and this section can be skipped.

With a shared database, every copy (the server, the home computer) sees the others' runs, saved
games, variants and hall of fame. Each copy keeps writing its own files exactly as before, so
nothing waits on the network; a background pass every 30 seconds sends what changed and brings in
what the other copies sent (`cloud.CloudSync`). If the database cannot be reached, or a daily
quota runs out, the copy keeps working and sends everything that waited once it gets through. The
Settings dialog shows the copy's name, the last sync and anything still waiting.

- **Saved games, variants (with their piece pictures), hall of fame:** one row per file. The last
  copy to send a file wins; a file changed on two copies before they synced keeps each copy's own
  version until one of them sends again (Settings shows a note when that happens).
- **Runs:** a run belongs to the copy that plays it. Each finished generation is one row (its
  summary, yardsticks and members) plus its games packed and gzipped, so a generation of hundreds
  of games costs a few row writes, not hundreds. The other copies build a read-only copy of the run
  file that the Lab lists like any run; resume or delete it on the copy that plays it.

D1's free plan allows 5 GB and 100,000 row writes a day (check the current limits on Cloudflare's
pricing page); a generation costs about ten.

### Set it up

The database `izikstar-chess` already exists in the Cloudflare account. Each copy needs three
values and its own name:

1. **Account ID:** in the Cloudflare dashboard, *Workers & Pages* (or *Account home*) shows it on the right.
2. **Database ID:** `c0ce3d98-8203-48b4-a8bc-d0dd0de5aaaf` (*Storage & Databases → D1 → izikstar-chess*).
3. **API token:** *My Profile → API Tokens → Create Token → Create Custom Token*; permission
   **Account · D1 · Edit**, account resources: your account. Copy the token once; it is shown only then.

On the server, put them in `/etc/izikstar/cloud.env` and restart:

```bash
sudo tee /etc/izikstar/cloud.env >/dev/null <<'ENV'
IZIKSTAR_D1_ACCOUNT=<account id>
IZIKSTAR_D1_DATABASE=c0ce3d98-8203-48b4-a8bc-d0dd0de5aaaf
IZIKSTAR_D1_TOKEN=<token>
IZIKSTAR_COPY=server
ENV
sudo systemctl restart izikstar-chess
```

On the home computer, put a `cloud.properties` file in the folder you start the jar from (it is
git-ignored; keep the token out of commits):

```properties
account=<account id>
database=c0ce3d98-8203-48b4-a8bc-d0dd0de5aaaf
token=<token>
copy=home
```

Without these values a copy simply keeps everything to itself, as before.

### Looking at it directly

The database is SQLite, so it can be queried in the Cloudflare dashboard's D1 console, with
`wrangler d1 execute izikstar-chess --remote --command "..."`, or by Claude through the Cloudflare
connector. `runs` and `run_generations` hold everything but the games themselves (e.g.
`SELECT number, champion_score, yardstick_elo FROM run_generations WHERE file = 'antichess-zero-1.db'`);
the games of a generation are gzipped JSON in `blobs` under the key `games:<file>:<generation>`.

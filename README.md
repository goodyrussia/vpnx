# vpnx

Android VPN client (Xray-core + SSH). Fresh app, package `com.vpnx.dope`.

Built from the Albert codebase (`com.albert.smartvpn`), renamed so it installs
alongside Albert. Engines kept as-is: SSH (direct / HTTP proxy / TLS-SNI /
TLS+proxy, with payload support), Xray (VLESS / VMess / Trojan / Shadowsocks),
Hysteria2, WireGuard, OpenVPN, SlowDNS / Slipstream.

## Build

Every build runs on GitHub Actions (`.github/workflows/build.yml`): it bumps
the version, restores the signing keystore from the `VPNX_KEYSTORE_*` repo
secrets, builds the signed arm64-v8a release APK, and publishes it as a
GitHub Release with `update.json` for the in-app updater.

No keystore or password is committed to this repo.

## Tunnel modes

Settings → Tunnel Mode, applies on the next connect:

- **VPN Service** — standard Android VPN (default, like Albert).
- **Root (hev)** — runs the hev-socks5-tunnel CLI executable as root, which
  creates the `vpnx0` TUN interface and forwards packets to the local SOCKS5
  server the proxy engines already run. No VpnService, no VPN permission
  prompt. Needs a rooted device (KernelSU / Magisk); grant root when asked
  on first connect. Root commands go through libsu's persistent shell and
  hev is launched with nohup + setsid so it survives the shell that started
  it. The proxy's own outbound traffic is kept out of the tunnel by policy
  routing (uidrange, iptables owner-mark fallback), and a watchdog stops the
  tunnel cleanly if hev dies. All routing is removed on disconnect. A failed
  start prints diagnostics to the log (app-side vs root-side view of vpnx0,
  routing rules, hev process, hev log).

## Status

v1.0.1 — first public build (VpnService baseline). v1.0.2 adds the Root (hev)
mode above. Miuix rebuild is planned later, after hev root works.

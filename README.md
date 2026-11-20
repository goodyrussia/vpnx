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

## Status

v1.0.0 — first public build. Hev root mode (TUN via hev-socks5-tunnel as a
root option instead of VpnService) is planned next; the current build uses
VpnService like Albert.

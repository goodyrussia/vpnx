# VPNX

Android VPN client — `com.vpnx.dope`

Open-source Android VPN app built fresh, following the working flow of the Albert / Techfeeds Smart VPN app, with a clean black Miuix UI and two ways to connect.

## Connection modes (Settings, Root is default)

- **TUN2SOCKS (ROOT)** — default. Runs `hev-socks5-tunnel` as a root process (via libsu): hev creates the TUN device itself and sends all traffic to the app's local SOCKS5 point. No Android VPN permission popup, no VPN key icon. Root routing rules are added on connect and cleaned up on disconnect.
- **Normal (VpnService)** — Android's built-in VPN way, same as Albert. Works without root; Android asks for VPN permission on first connect.

Both modes share everything else: same configs, same engines, same screens. Only the way phone traffic enters the app changes.

## Protocols

- VLESS, VMess, Trojan, Shadowsocks (via Xray-core)
- WireGuard (via Xray outbound)
- Hysteria2 (native engine)
- SSH, in 4 clean types:
  - SSH (any port)
  - SSH Proxy
  - SSH TLS
  - SSH Proxy TLS
  - Each with an optional **Use Payload** tick — payload is a setting, never part of the protocol name.

Not included: DNSTT / SlowDNS / Slipstream, OpenVPN, UDP Custom, Psiphon.

## Import

Standard links only: `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://` / `hysteria2://`, `ssh://`, and WireGuard config text. No custom encoded links, no locked configs, no device limits, no expiry or burn-after-use features.

## UI

- [Miuix](https://github.com/compose-miuix-ui/miuix) (Compose) — fixed black theme only. No Material UI, no light theme, no theme options.

## Building

**All builds run on GitHub Actions. Nothing is built locally.**
Push code → Actions builds the native parts and the APK → the APK is published to GitHub Releases. The signing key lives in GitHub Secrets, never in the code.

## Status

Planning phase — see [PLAN.md](PLAN.md) for the full build plan and references.

## References (method and parts)

- Albert / Techfeeds Smart VPN — the base app whose working engine flow VPNX follows
- [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — TUN2SOCKS engine (MIT)
- [Asterisk4Magisk/AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) and AsteriskBOX — reference for TUN2SOCKS(ROOT) run mode
- [2dust/v2rayNG](https://github.com/2dust/v2rayNG) — reference for root mode with the core staying in-app
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) — UI library (Apache-2.0)
- [topjohnwu/libsu](https://github.com/topjohnwu/libsu) — root shell library

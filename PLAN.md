# VPNX — Build Plan

Status: READY TO START. Decisions locked 2026-11-20. No app code written yet, nothing built yet.

## Locked decisions

| Item | Decision |
|---|---|
| App name | VPNX |
| Package | `com.vpnx.dope` |
| Repo | `goodyrussia/vpnx`, public, open source |
| Base | Build fresh, following Albert (Techfeeds Smart VPN)'s working flow step for step |
| UI | Miuix library only, fixed black theme, no Material UI, no theme options |
| Modes | TUN2SOCKS (ROOT) default + Normal VpnService, switch in Settings |
| Builds | GitHub Actions ONLY. No local builds, ever |
| Signing | Keystore in GitHub Secrets. Never in code (Albert hardcoded it — do not repeat) |

## How the app works (Albert's flow, kept)

1. User picks a config and taps Connect.
2. The engine for that protocol starts and opens a local SOCKS5 point at `127.0.0.1:<port>`:
   - Xray-core → VLESS, VMess, Trojan, Shadowsocks, WireGuard (Xray outbound)
   - Hysteria2 native binary → Hysteria2
   - SSH engine (sshj) → all SSH types
3. The TUN layer pulls all phone traffic into that SOCKS5 point (two modes, below).
4. On Disconnect: engine stops, TUN stops, and (root mode) every routing rule added is removed.

## The two modes

### Normal mode (VpnService)
- Exactly how Albert works today: `VpnService.Builder` creates the TUN, its fd is handed to the real hev library (`TProxyStartService`) instead of Albert's hand-written Kotlin forwarder (deleted — hev replaces it in both modes).
- Needs Android's VPN permission popup on first connect. No root needed.

### Root mode (default)
Follows the proven pattern from AsteriskNG / AsteriskBOX / v2rayNG root mode:
1. Check root with libsu; if no root, refuse politely and suggest Normal mode.
2. Start the protocol engine in-app as usual (core stays in-app, like v2rayNG — unlike Asterisk, which runs its whole core as root).
3. Run the standalone `hev-socks5-tunnel` program as a root process: its config names the TUN device and points at the local SOCKS5 point; hev creates the TUN itself.
4. Root commands steer traffic: mark + dedicated routing table into the TUN, with the config's own server address bypassed so the connection cannot loop.
5. DNS is pushed through the tunnel (no leak to the LAN resolver).
6. On stop (including app kill where possible): kill hev, delete the routing table/rules and the TUN, flush only our own iptables chain — cleanup is a first-class feature, copied in spirit from Asterisk's `asteriskd` behaviour.

## Protocols and the clean profile model

One profile = only the fields the kept protocols actually need. Albert's profile carried ~30 lock / SlowDNS / cloud fields — all gone.

- VLESS, VMess, Trojan, Shadowsocks, WireGuard, Hysteria2: fields as in Albert's standard parsers.
- SSH — 4 types only, payload is a tick, not a name:
  - SSH (host, any port, user, pass)
  - SSH Proxy (+ proxy host/port)
  - SSH TLS (+ SNI)
  - SSH Proxy TLS (+ proxy + SNI)
  - Optional on all 4: ☑ Use Payload (+ payload text box)
  - Albert's SSH engine already works this way internally (proxy on/off, payload on/off, TLS on/off) — the 31 old mode names collapse into these 4 + tick with no engine rewrite.

## Import / export

- Import: standard links only — `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `hysteria2://`, `ssh://`, WireGuard config text, Xray JSON. From clipboard, file, or QR.
- Export: the same standard link for that config. Nothing else.
- Deleted from Albert: `svpn://`, `tfvpn://`, `tfvpnbz://` encoders, whitebox/AES bundle code, lock passphrase, HWID/device binding, expiry, quota, burn-after-use, block-rooted/emulator/sniffer checks, cloud upload.

## Screens (Miuix, black)

Home (connect button, status, traffic) · Configs (list, add/import, edit) · Logs · Settings (connection mode switch Root/Normal at top, local SOCKS port, DNS). Nothing else. No theme menu, no network-tools sprawl, no hotshare.

## What is carried over from Albert (code-level)

- `XrayCore.kt` — minus SlowDNS/Slipstream branches and `protect()` calls become mode-aware
- `Hysteria2Engine.kt` — as is
- `SshEngine.kt` — minus DNSTT/SlowDNS paths
- `PayloadGenerator.kt` — as is
- Standard parsers from `VpnConfigParser.kt` — standard schemes only
- Room profile storage pattern (new clean entity)
- Native provisioning approach from Albert's `build.gradle`, moved into the Actions workflow

## GitHub Actions design (`.github/workflows/build.yml`)

On every push to `main` (and manual run):
1. Set up JDK 21, Android SDK + pinned NDK, Go.
2. Build/fetch native parts: Xray, Hysteria2, hev library (normal mode) + hev standalone program (root mode), both from the pinned hev source like v2rayNG's `compile-hevtun.sh` approach.
3. `./gradlew assembleRelease` with signing from Secrets.
4. Upload the APK as a workflow artifact and to a GitHub Release (with `update.json`, like Albert, so the app can check for updates).

## Build phases

- **Phase 0** — Repo, this plan, empty app skeleton (Miuix black), Actions workflow green on the skeleton.
- **Phase 1** — Normal mode end-to-end with Xray protocols (VLESS first), standard import. This is the safe baseline: it must work exactly like Albert.
- **Phase 2** — Root mode (hev root process + routing + cleanup), Settings switch, Root as default. Test APK to the owner's phone.
- **Phase 3** — SSH 4 types + payload tick, Hysteria2, WireGuard, remaining import formats.
- **Phase 4** — Logs, traffic stats, update check, final polish, release.

## Device test checklist (owner's rooted phone)

- [ ] Root mode connects, whole-phone traffic flows, no VPN icon/popup
- [ ] Server address does not loop; disconnect leaves no leftover rules (check by reconnecting + by reboot)
- [ ] DNS resolves through the tunnel in root mode
- [ ] Normal mode still works with root mode off
- [ ] Each protocol connects at least once: VLESS, VMess, Trojan, SS, WG, Hy2, SSH x4, SSH with payload ticked

## Open items

- Repo created 2026-11-20: `github.com/goodyrussia/vpnx`, public, empty — created by the owner signed in as goodyrussia. This plan is the first commit. Next: Phase 0.

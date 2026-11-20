# vpnx

Brand-new modern Android VPN application built with **Miuix** (Xiaomi HyperOS Compose styling) and dual-mode capture engines: **VpnService** (non-root) and **hev TUN2SOCKS** (KernelSU-Next root).

## Key Features
- **Pure HyperOS Design:** 4-tab bottom navigation (Home, Config, Logs, Settings) powered by Miuix 0.9.4.
- **Dual Capture Engines:**
  - **VPN Service Mode:** Seamless Android OS VpnService capture using in-process JNI `libhev-socks5-tunnel.so`.
  - **Root (hev) Mode:** Kernel TUN (`vpnx0`) routing with policy routing rules (`pref 5`, `10`, `20`) via standalone `libhev-socks5-tunnel-cli.so` and `libsu:6.0.0`.
- **Supported Protocols:**
  - **SSH:** Direct TCP, HTTP Proxy, TLS (SNI), and HTTP Proxy + TLS with custom HTTP payload injection engine.
  - **Xray:** VLESS, VMess, Trojan, Shadowsocks, and Hysteria2 via `AndroidLibXrayLite` (`v26.11.9-p1`).
  - Strict routing-free design: Xray runs solely as local SOCKS5 inbound + proxy outbound; hev handles all TUN routing.
- **Hotspot & Tethering Sharing:**
  - Root mode: kernel iptables PREROUTING marks + hardware TC offload bypass.
  - VPN mode: local SOCKS5/HTTP proxy hotshare.

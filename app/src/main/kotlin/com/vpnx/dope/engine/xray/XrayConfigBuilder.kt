package com.vpnx.dope.engine.xray

import com.vpnx.dope.data.model.XrayProfile
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    private const val DEFAULT_HY2_HOP_INTERVAL_SECONDS = 30

    /**
     * Builds a strictly routing-free Xray JSON configuration.
     * Contains ONLY:
     * - One in-process SOCKS5 inbound listening on 127.0.0.1:10808 with UDP enabled.
     * - One outbound for the selected remote proxy server.
     * - Zero routing rules, zero DNS hijacking, zero geoip/geosite dependencies.
     */
    fun buildRoutingFreeConfig(profile: XrayProfile, socksPort: Int = 10808): String {
        val root = JSONObject()

        // Log configuration
        val logObj = JSONObject()
        logObj.put("loglevel", "warning")
        root.put("log", logObj)

        // Local SOCKS5 inbound
        val inboundsArray = JSONArray()
        val socksInbound = JSONObject()
        socksInbound.put("tag", "socks-in")
        socksInbound.put("listen", "127.0.0.1")
        socksInbound.put("port", socksPort)
        socksInbound.put("protocol", "socks")
        val settingsObj = JSONObject()
        settingsObj.put("auth", "noauth")
        settingsObj.put("udp", true)
        socksInbound.put("settings", settingsObj)
        val sniffingObj = JSONObject()
        sniffingObj.put("enabled", false)
        socksInbound.put("sniffing", sniffingObj)
        inboundsArray.put(socksInbound)
        root.put("inbounds", inboundsArray)

        // Remote proxy outbound
        val outboundsArray = JSONArray()
        val proxyOutbound = JSONObject()
        proxyOutbound.put("tag", "proxy")

        val protocolUpper = profile.protocol.uppercase()
        when (protocolUpper) {
            "VLESS" -> buildVlessOutbound(proxyOutbound, profile)
            "VMESS" -> buildVmessOutbound(proxyOutbound, profile)
            "TROJAN" -> buildTrojanOutbound(proxyOutbound, profile)
            "SHADOWSOCKS" -> buildShadowsocksOutbound(proxyOutbound, profile)
            "HYSTERIA2" -> buildHysteria2Outbound(proxyOutbound, profile)
            else -> buildVlessOutbound(proxyOutbound, profile)
        }

        // Hysteria2 constructs its own Xray hysteria streamSettings + finalmask
        if (protocolUpper != "HYSTERIA2") {
            val streamSettings = JSONObject()
            val net = profile.transport.lowercase().ifBlank { "tcp" }
            streamSettings.put("network", net)

            when (profile.security.lowercase()) {
                "tls" -> {
                    streamSettings.put("security", "tls")
                    val tlsSettings = JSONObject()
                    val sni = profile.sni.ifBlank { profile.server }
                    tlsSettings.put("serverName", sni)
                    tlsSettings.put("allowInsecure", profile.allowInsecure)
                    tlsSettings.put("fingerprint", profile.fingerprint.ifBlank { "chrome" })

                    val alpnArray = JSONArray()
                    profile.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        alpnArray.put(it)
                    }
                    if (alpnArray.length() > 0) {
                        tlsSettings.put("alpn", alpnArray)
                    }
                    streamSettings.put("tlsSettings", tlsSettings)
                }
                "reality" -> {
                    streamSettings.put("security", "reality")
                    val realitySettings = JSONObject()
                    val sni = profile.sni.ifBlank { profile.server }
                    realitySettings.put("serverName", sni)
                    realitySettings.put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                    realitySettings.put("publicKey", profile.realityPublicKey.trim())
                    realitySettings.put("shortId", profile.realityShortId.trim())
                    if (profile.realitySpiderX.isNotBlank()) {
                        realitySettings.put("spiderX", profile.realitySpiderX.trim())
                    }
                    realitySettings.put("show", false)
                    streamSettings.put("realitySettings", realitySettings)
                }
                else -> {
                    streamSettings.put("security", "none")
                }
            }

            when (net) {
                "ws" -> {
                    val wsSettings = JSONObject()
                    wsSettings.put("path", profile.wsPath.ifBlank { "/" })
                    val headers = JSONObject()
                    headers.put("Host", profile.wsHost.ifBlank { profile.sni.ifBlank { profile.server } })
                    wsSettings.put("headers", headers)
                    streamSettings.put("wsSettings", wsSettings)
                }
                "grpc" -> {
                    val grpcSettings = JSONObject()
                    grpcSettings.put("serviceName", profile.grpcServiceName.trim())
                    streamSettings.put("grpcSettings", grpcSettings)
                }
                "httpupgrade" -> {
                    val httpUpgradeSettings = JSONObject()
                    val path = profile.httpUpgradePath.ifBlank { profile.wsPath.ifBlank { "/" } }
                    val host = profile.httpUpgradeHost.ifBlank {
                        profile.wsHost.ifBlank { profile.sni.ifBlank { profile.server } }
                    }
                    httpUpgradeSettings.put("path", path)
                    if (host.isNotBlank()) {
                        httpUpgradeSettings.put("host", host)
                    }
                    streamSettings.put("httpupgradeSettings", httpUpgradeSettings)
                }
            }

            proxyOutbound.put("streamSettings", streamSettings)
        }

        outboundsArray.put(proxyOutbound)
        root.put("outbounds", outboundsArray)

        return root.toString(2)
    }

    private fun buildVlessOutbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "vless")
        val settings = JSONObject()
        val vnext = JSONArray()
        val serverObj = JSONObject()
        serverObj.put("address", profile.server)
        serverObj.put("port", profile.port)

        val users = JSONArray()
        val userObj = JSONObject()
        userObj.put("id", profile.uuid)
        userObj.put("encryption", "none")
        if (profile.flow.isNotBlank()) {
            val net = profile.transport.lowercase().ifBlank { "tcp" }
            val sec = profile.security.lowercase()
            val flowSupported = (net == "tcp" || net == "raw") && (sec == "tls" || sec == "reality")
            if (flowSupported) {
                userObj.put("flow", profile.flow.trim())
            } else {
                XrayLogTailer.warn(
                    "Omitting VLESS flow '${profile.flow}' because it requires tcp/raw + tls/reality (got $net/$sec)"
                )
            }
        }
        users.put(userObj)
        serverObj.put("users", users)
        vnext.put(serverObj)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
    }

    private fun buildVmessOutbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "vmess")
        val settings = JSONObject()
        val vnext = JSONArray()
        val serverObj = JSONObject()
        serverObj.put("address", profile.server)
        serverObj.put("port", profile.port)

        val users = JSONArray()
        val userObj = JSONObject()
        userObj.put("id", profile.uuid)
        userObj.put("security", "auto")
        userObj.put("alterId", 0)
        users.put(userObj)
        serverObj.put("users", users)
        vnext.put(serverObj)
        settings.put("vnext", vnext)
        outbound.put("settings", settings)
    }

    private fun buildTrojanOutbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "trojan")
        val settings = JSONObject()
        val servers = JSONArray()
        val serverObj = JSONObject()
        serverObj.put("address", profile.server)
        serverObj.put("port", profile.port)
        serverObj.put("password", profile.trojanPassword)
        servers.put(serverObj)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }

    private fun buildShadowsocksOutbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "shadowsocks")
        val settings = JSONObject()
        val servers = JSONArray()
        val serverObj = JSONObject()
        serverObj.put("address", profile.server)
        serverObj.put("port", profile.port)
        serverObj.put("method", profile.ssMethod.ifBlank { "2022-blake3-aes-128-gcm" })
        serverObj.put("password", profile.ssPassword)
        servers.put(serverObj)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }

    /**
     * Xray-core Hysteria2 outbound schema (matching AsteriskNG Hysteria2.toXrayOutbound):
     * protocol = "hysteria", settings = { version: 2, address, port },
     * streamSettings = { network: "hysteria", hysteriaSettings: { version: 2, auth },
     * security: "tls", tlsSettings, finalmask }.
     */
    private fun buildHysteria2Outbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "hysteria")

        val settings = JSONObject()
        settings.put("version", 2)
        settings.put("address", profile.server)
        settings.put("port", profile.port)
        outbound.put("settings", settings)

        val streamSettings = JSONObject()
        streamSettings.put("network", "hysteria")

        val hysteriaSettings = JSONObject()
        hysteriaSettings.put("version", 2)
        hysteriaSettings.put("auth", profile.hy2Auth)
        streamSettings.put("hysteriaSettings", hysteriaSettings)

        streamSettings.put("security", "tls")
        val tlsSettings = JSONObject()
        val sni = profile.sni.ifBlank { profile.server }
        if (sni.isNotBlank()) {
            tlsSettings.put("serverName", sni)
        }
        if (profile.hy2PinSha256.isNotBlank()) {
            tlsSettings.put("pinnedPeerCertSha256", profile.hy2PinSha256.trim())
        }
        if (profile.allowInsecure) {
            tlsSettings.put("allowInsecure", true)
        }
        streamSettings.put("tlsSettings", tlsSettings)

        val finalMask = JSONObject()
        if (profile.hy2UpBandwidth > 0 || profile.hy2DownBandwidth > 0) {
            val quicParams = JSONObject()
            quicParams.put("congestion", "brutal")
            if (profile.hy2UpBandwidth > 0) {
                quicParams.put("brutalUp", "${profile.hy2UpBandwidth} mbps")
            }
            if (profile.hy2DownBandwidth > 0) {
                quicParams.put("brutalDown", "${profile.hy2DownBandwidth} mbps")
            }
            finalMask.put("quicParams", quicParams)
        }

        val useSalamander = profile.hy2ObfsType.equals("salamander", ignoreCase = true) &&
            profile.hy2ObfsPassword.isNotBlank()
        val mport = profile.hy2Ports.trim()
        if (useSalamander || mport.isNotBlank()) {
            val udpArray = JSONArray()
            if (useSalamander) {
                val salamanderObj = JSONObject()
                salamanderObj.put("type", "salamander")
                val salSettings = JSONObject()
                salSettings.put("password", profile.hy2ObfsPassword)
                salamanderObj.put("settings", salSettings)
                udpArray.put(salamanderObj)
            }
            if (mport.isNotBlank()) {
                // Xray wraps masks in reverse order; udphop must be outermost.
                val udpHopObj = JSONObject()
                udpHopObj.put("type", "udphop")
                val hopSettings = JSONObject()
                hopSettings.put("mode", "intervalLocal,intervalRemote")
                hopSettings.put("remotePorts", mport)
                hopSettings.put(
                    "interval",
                    profile.hy2HopInterval.trim().toIntOrNull()?.takeIf { it > 0 }
                        ?: DEFAULT_HY2_HOP_INTERVAL_SECONDS
                )
                udpHopObj.put("settings", hopSettings)
                udpArray.put(udpHopObj)
            }
            finalMask.put("udp", udpArray)
        }

        if (finalMask.length() > 0) {
            streamSettings.put("finalmask", finalMask)
        }

        outbound.put("streamSettings", streamSettings)
    }
}
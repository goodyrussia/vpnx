package com.vpnx.dope.engine.xray

import com.vpnx.dope.data.model.XrayProfile
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

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

        when (profile.protocol.uppercase()) {
            "VLESS" -> buildVlessOutbound(proxyOutbound, profile)
            "VMESS" -> buildVmessOutbound(proxyOutbound, profile)
            "TROJAN" -> buildTrojanOutbound(proxyOutbound, profile)
            "SHADOWSOCKS" -> buildShadowsocksOutbound(proxyOutbound, profile)
            "HYSTERIA2" -> buildHysteria2Outbound(proxyOutbound, profile)
            else -> buildVlessOutbound(proxyOutbound, profile)
        }

        // Stream Settings (TLS, WebSocket, Transport)
        val streamSettings = JSONObject()
        val net = profile.transport.lowercase().ifBlank { "tcp" }
        streamSettings.put("network", net)

        if (profile.security.equals("tls", ignoreCase = true) || profile.security.equals("reality", ignoreCase = true)) {
            streamSettings.put("security", profile.security.lowercase())
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
        } else {
            streamSettings.put("security", "none")
        }

        if (net == "ws") {
            val wsSettings = JSONObject()
            wsSettings.put("path", profile.wsPath.ifBlank { "/" })
            val headers = JSONObject()
            headers.put("Host", profile.wsHost.ifBlank { profile.sni.ifBlank { profile.server } })
            wsSettings.put("headers", headers)
            streamSettings.put("wsSettings", wsSettings)
        }

        proxyOutbound.put("streamSettings", streamSettings)
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
            userObj.put("flow", profile.flow)
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

    private fun buildHysteria2Outbound(outbound: JSONObject, profile: XrayProfile) {
        outbound.put("protocol", "hysteria2")
        val settings = JSONObject()
        val servers = JSONArray()
        val serverObj = JSONObject()
        serverObj.put("address", profile.server)
        serverObj.put("port", profile.port)
        serverObj.put("password", profile.hy2Auth)
        if (profile.hy2UpBandwidth > 0) serverObj.put("up_mbps", profile.hy2UpBandwidth)
        if (profile.hy2DownBandwidth > 0) serverObj.put("down_mbps", profile.hy2DownBandwidth)
        if (profile.hy2Ports.isNotBlank()) serverObj.put("ports", profile.hy2Ports)
        servers.put(serverObj)
        settings.put("servers", servers)
        outbound.put("settings", settings)
    }
}

package com.vpnx.dope.data.model

import com.vpnx.dope.data.entity.ProfileEntity

data class XrayProfile(
    val id: String,
    val name: String,
    val protocol: String, // "VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "HYSTERIA2"
    val server: String,
    val port: Int,
    val uuid: String,
    val flow: String,
    val security: String,
    val sni: String,
    val alpn: String,
    val fingerprint: String,
    val allowInsecure: Boolean,
    val transport: String,
    val wsPath: String,
    val wsHost: String,
    val ssMethod: String,
    val ssPassword: String,
    val trojanPassword: String,
    val hy2Auth: String,
    val hy2UpBandwidth: Int,
    val hy2DownBandwidth: Int,
    val hy2Ports: String,
    val hy2ObfsType: String,
    val hy2ObfsPassword: String
) {
    fun toEntity(isSelected: Boolean = false): ProfileEntity = ProfileEntity(
        id = id,
        name = name,
        protocolType = "XRAY",
        server = server,
        port = port,
        isSelected = isSelected,
        xrayProtocol = protocol,
        uuid = uuid,
        flow = flow,
        security = security,
        sni = sni,
        alpn = alpn,
        fingerprint = fingerprint,
        allowInsecure = allowInsecure,
        transport = transport,
        wsPath = wsPath,
        wsHost = wsHost,
        ssMethod = ssMethod,
        ssPassword = ssPassword,
        trojanPassword = trojanPassword,
        hy2Auth = hy2Auth,
        hy2UpBandwidth = hy2UpBandwidth,
        hy2DownBandwidth = hy2DownBandwidth,
        hy2Ports = hy2Ports,
        hy2ObfsType = hy2ObfsType,
        hy2ObfsPassword = hy2ObfsPassword
    )

    companion object {
        fun fromEntity(e: ProfileEntity): XrayProfile = XrayProfile(
            id = e.id,
            name = e.name,
            protocol = e.xrayProtocol,
            server = e.server,
            port = e.port,
            uuid = e.uuid,
            flow = e.flow,
            security = e.security,
            sni = e.sni,
            alpn = e.alpn,
            fingerprint = e.fingerprint,
            allowInsecure = e.allowInsecure,
            transport = e.transport,
            wsPath = e.wsPath,
            wsHost = e.wsHost,
            ssMethod = e.ssMethod,
            ssPassword = e.ssPassword,
            trojanPassword = e.trojanPassword,
            hy2Auth = e.hy2Auth,
            hy2UpBandwidth = e.hy2UpBandwidth,
            hy2DownBandwidth = e.hy2DownBandwidth,
            hy2Ports = e.hy2Ports,
            hy2ObfsType = e.hy2ObfsType,
            hy2ObfsPassword = e.hy2ObfsPassword
        )
    }
}

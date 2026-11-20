package com.vpnx.dope.data.model

import com.vpnx.dope.data.entity.ProfileEntity

data class SshProfile(
    val id: String,
    val name: String,
    val server: String,
    val port: Int,
    val user: String,
    val pass: String,
    val mode: String, // "DIRECT", "PROXY", "TLS", "PROXY_TLS"
    val payloadEnabled: Boolean,
    val payload: String,
    val sni: String,
    val proxyHost: String,
    val proxyPort: Int,
    val proxyAuth: Boolean,
    val proxyUser: String,
    val proxyPass: String,
    val tlsVersion: String
) {
    fun toEntity(isSelected: Boolean = false): ProfileEntity = ProfileEntity(
        id = id,
        name = name,
        protocolType = "SSH",
        server = server,
        port = port,
        isSelected = isSelected,
        sshUser = user,
        sshPass = pass,
        sshMode = mode,
        payloadEnabled = payloadEnabled,
        payload = payload,
        sni = sni,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        proxyAuth = proxyAuth,
        proxyUser = proxyUser,
        proxyPass = proxyPass,
        tlsVersion = tlsVersion
    )

    companion object {
        fun fromEntity(e: ProfileEntity): SshProfile = SshProfile(
            id = e.id,
            name = e.name,
            server = e.server,
            port = e.port,
            user = e.sshUser,
            pass = e.sshPass,
            mode = e.sshMode,
            payloadEnabled = e.payloadEnabled,
            payload = e.payload,
            sni = e.sni,
            proxyHost = e.proxyHost,
            proxyPort = e.proxyPort,
            proxyAuth = e.proxyAuth,
            proxyUser = e.proxyUser,
            proxyPass = e.proxyPass,
            tlsVersion = e.tlsVersion
        )
    }
}

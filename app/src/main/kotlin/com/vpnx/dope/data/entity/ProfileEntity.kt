package com.vpnx.dope.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default Profile",
    val protocolType: String = "SSH", // "SSH" or "XRAY"
    val server: String = "",
    val port: Int = 22,
    val isSelected: Boolean = false,

    // --- SSH Fields ---
    val sshUser: String = "",
    val sshPass: String = "",
    val sshMode: String = "DIRECT", // "DIRECT", "PROXY", "TLS", "PROXY_TLS"
    val payloadEnabled: Boolean = false,
    val payload: String = "",
    val sni: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val proxyAuth: Boolean = false,
    val proxyUser: String = "",
    val proxyPass: String = "",
    val tlsVersion: String = "DEFAULT",

    // --- Xray Fields ---
    val xrayProtocol: String = "VLESS", // "VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "HYSTERIA2"
    val uuid: String = "",
    val flow: String = "",
    val security: String = "tls", // "none", "tls", "reality"
    val fingerprint: String = "chrome",
    val alpn: String = "h2,http/1.1",
    val allowInsecure: Boolean = true,
    @ColumnInfo(defaultValue = "")
    val realityPublicKey: String = "",
    @ColumnInfo(defaultValue = "")
    val realityShortId: String = "",
    @ColumnInfo(defaultValue = "")
    val realitySpiderX: String = "",
    val transport: String = "tcp", // "tcp", "ws", "grpc", "httpupgrade"
    val wsPath: String = "/",
    val wsHost: String = "",
    @ColumnInfo(defaultValue = "")
    val grpcServiceName: String = "",
    @ColumnInfo(defaultValue = "")
    val httpUpgradePath: String = "",
    @ColumnInfo(defaultValue = "")
    val httpUpgradeHost: String = "",
    val ssMethod: String = "2022-blake3-aes-128-gcm",
    val ssPassword: String = "",
    val trojanPassword: String = "",
    val hy2Auth: String = "",
    val hy2UpBandwidth: Int = 50,
    val hy2DownBandwidth: Int = 100,
    val hy2Ports: String = "",
    val hy2ObfsType: String = "",
    val hy2ObfsPassword: String = "",
    @ColumnInfo(defaultValue = "")
    val hy2PinSha256: String = "",
    @ColumnInfo(defaultValue = "")
    val hy2HopInterval: String = "",

    // Telemetry
    val lastPingMs: Long = -1L,
    val createdAt: Long = System.currentTimeMillis()
)
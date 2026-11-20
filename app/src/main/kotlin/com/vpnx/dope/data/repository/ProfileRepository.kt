package com.vpnx.dope.data.repository

import android.net.Uri
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.entity.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.UUID

class ProfileRepository(private val db: AppDatabase) {

    val allProfiles: Flow<List<ProfileEntity>> = db.profileDao().getAllProfiles()
    val selectedProfile: Flow<ProfileEntity?> = db.profileDao().getSelectedProfile()

    suspend fun getSelectedProfileSync(): ProfileEntity? = withContext(Dispatchers.IO) {
        db.profileDao().getSelectedProfileSync()
    }

    suspend fun getProfileById(id: String): ProfileEntity? = withContext(Dispatchers.IO) {
        db.profileDao().getProfileById(id)
    }

    suspend fun insertProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        db.profileDao().insert(profile)
    }

    suspend fun updateProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        db.profileDao().update(profile)
    }

    suspend fun deleteProfile(profile: ProfileEntity) = withContext(Dispatchers.IO) {
        db.profileDao().delete(profile)
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        db.profileDao().deleteById(id)
    }

    suspend fun setSelected(id: String) = withContext(Dispatchers.IO) {
        db.profileDao().setSelected(id)
    }

    suspend fun updatePing(id: String, pingMs: Long) = withContext(Dispatchers.IO) {
        db.profileDao().updatePing(id, pingMs)
    }

    suspend fun parseAndImportUri(rawUri: String): Result<ProfileEntity> = withContext(Dispatchers.Default) {
        runCatching {
            val text = rawUri.trim()
            when {
                text.startsWith("vless://", ignoreCase = true) -> parseVless(text)
                text.startsWith("trojan://", ignoreCase = true) -> parseTrojan(text)
                text.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(text)
                text.startsWith("hy2://", ignoreCase = true) || text.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(text)
                text.startsWith("ssh://", ignoreCase = true) -> parseSsh(text)
                else -> throw IllegalArgumentException("Unsupported URI scheme. Supported: vless://, trojan://, ss://, hy2://, ssh://")
            }
        }.onSuccess { profile ->
            insertProfile(profile)
        }
    }

    private fun parseVless(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val uuid = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 443
        val name = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault("VLESS Import").ifBlank { "VLESS Import" }

        val security = uri.getQueryParameter("security") ?: "tls"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val alpn = uri.getQueryParameter("alpn") ?: "h2,http/1.1"
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val type = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path") ?: "/"
        val wsHost = uri.getQueryParameter("host") ?: sni
        val flow = uri.getQueryParameter("flow") ?: ""

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "VLESS",
            server = host,
            port = port,
            uuid = uuid,
            flow = flow,
            security = security,
            sni = sni,
            alpn = alpn,
            fingerprint = fp,
            allowInsecure = true,
            transport = type,
            wsPath = path,
            wsHost = wsHost
        )
    }

    private fun parseTrojan(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val password = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 443
        val name = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault("Trojan Import").ifBlank { "Trojan Import" }

        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val alpn = uri.getQueryParameter("alpn") ?: "h2,http/1.1"
        val type = uri.getQueryParameter("type") ?: "tcp"
        val path = uri.getQueryParameter("path") ?: "/"
        val wsHost = uri.getQueryParameter("host") ?: sni

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "TROJAN",
            server = host,
            port = port,
            trojanPassword = password,
            security = "tls",
            sni = sni,
            alpn = alpn,
            fingerprint = "chrome",
            allowInsecure = true,
            transport = type,
            wsPath = path,
            wsHost = wsHost
        )
    }

    private fun parseShadowsocks(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val name = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault("Shadowsocks Import").ifBlank { "SS Import" }
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 8388

        var method = "2022-blake3-aes-128-gcm"
        var password = ""
        val userInfo = uri.userInfo.orEmpty()
        if (userInfo.isNotEmpty()) {
            if (userInfo.contains(":")) {
                val parts = userInfo.split(":", limit = 2)
                method = parts[0]
                password = parts[1]
            } else {
                val decoded = runCatching { String(android.util.Base64.decode(userInfo, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                if (decoded != null && decoded.contains(":")) {
                    val parts = decoded.split(":", limit = 2)
                    method = parts[0]
                    password = parts[1]
                } else {
                    password = userInfo
                }
            }
        }

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "SHADOWSOCKS",
            server = host,
            port = port,
            ssMethod = method,
            ssPassword = password
        )
    }

    private fun parseHysteria2(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val auth = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 443
        val name = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault("Hysteria2 Import").ifBlank { "HY2 Import" }

        val sni = uri.getQueryParameter("sni") ?: host
        val obfs = uri.getQueryParameter("obfs") ?: ""
        val obfsPass = uri.getQueryParameter("obfs-password") ?: ""
        val mport = uri.getQueryParameter("mport") ?: ""

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "HYSTERIA2",
            server = host,
            port = port,
            hy2Auth = auth,
            sni = sni,
            hy2Ports = mport,
            hy2ObfsType = obfs,
            hy2ObfsPassword = obfsPass,
            allowInsecure = true
        )
    }

    private fun parseSsh(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val userInfo = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 22
        val name = runCatching { URLDecoder.decode(uri.fragment.orEmpty(), "UTF-8") }.getOrDefault("SSH Import").ifBlank { "SSH Import" }

        var user = "root"
        var pass = ""
        if (userInfo.contains(":")) {
            val parts = userInfo.split(":", limit = 2)
            user = parts[0]
            pass = parts[1]
        } else if (userInfo.isNotEmpty()) {
            user = userInfo
        }

        val mode = uri.getQueryParameter("mode")?.uppercase() ?: "DIRECT"
        val sni = uri.getQueryParameter("sni") ?: ""
        val payload = uri.getQueryParameter("payload") ?: ""

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "SSH",
            server = host,
            port = port,
            sshUser = user,
            sshPass = pass,
            sshMode = mode,
            payloadEnabled = payload.isNotEmpty(),
            payload = payload,
            sni = sni
        )
    }
}

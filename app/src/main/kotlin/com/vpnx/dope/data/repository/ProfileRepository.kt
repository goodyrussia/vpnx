package com.vpnx.dope.data.repository

import android.net.Uri
import android.util.Base64
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.entity.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
                text.startsWith("vmess://", ignoreCase = true) -> parseVmess(text)
                text.startsWith("trojan://", ignoreCase = true) -> parseTrojan(text)
                text.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(text)
                text.startsWith("hy2://", ignoreCase = true) || text.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(text)
                text.startsWith("ssh://", ignoreCase = true) -> parseSsh(text)
                else -> throw IllegalArgumentException(
                    "Unsupported URI scheme. Supported: vless://, vmess://, trojan://, ss://, hy2://, ssh://"
                )
            }
        }.onSuccess { profile ->
            insertProfile(profile)
        }
    }

    private fun parseVless(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val uuid = uri.userInfo.orEmpty().trim()
        val host = uri.host.orEmpty().trim()
        val port = if (uri.port > 0) uri.port else 443
        require(host.isNotBlank()) { "VLESS URI is missing server host" }
        require(port in 1..65535) { "VLESS URI has invalid port: $port" }
        require(uuid.isNotBlank()) { "VLESS URI is missing UUID" }

        val name = decodeFragment(uri.fragment, "VLESS Import")
        val security = uri.getQueryParameter("security")?.lowercase()?.ifBlank { "tls" } ?: "tls"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val alpn = uri.getQueryParameter("alpn") ?: "h2,http/1.1"
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val pbk = uri.getQueryParameter("pbk").orEmpty().trim()
        val sid = uri.getQueryParameter("sid").orEmpty().trim()
        val spx = uri.getQueryParameter("spx").orEmpty().trim()
        val allowInsecure = parseBoolParam(
            uri.getQueryParameter("allowInsecure") ?: uri.getQueryParameter("insecure"),
            default = true
        )

        if (security == "reality") {
            require(pbk.isNotBlank()) { "VLESS Reality URI is missing public key (pbk)" }
        }

        val type = uri.getQueryParameter("type")?.lowercase()?.ifBlank { "tcp" } ?: "tcp"
        val path = uri.getQueryParameter("path") ?: "/"
        val hostParam = uri.getQueryParameter("host") ?: sni
        val serviceName = uri.getQueryParameter("serviceName").orEmpty().trim()
        val flow = uri.getQueryParameter("flow").orEmpty().trim()

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
            allowInsecure = allowInsecure,
            realityPublicKey = pbk,
            realityShortId = sid,
            realitySpiderX = spx,
            transport = type,
            wsPath = path,
            wsHost = hostParam,
            grpcServiceName = serviceName,
            httpUpgradePath = if (type == "httpupgrade") path else "",
            httpUpgradeHost = if (type == "httpupgrade") hostParam else ""
        )
    }

    private fun parseVmess(uriStr: String): ProfileEntity {
        val payload = uriStr.substringAfter("://").trim()
        require(payload.isNotBlank()) { "VMess URI is empty" }

        val decodedJson = decodeBase64Flexible(payload)
            ?: throw IllegalArgumentException("VMess URI is not valid Base64")
        val obj = runCatching { JSONObject(decodedJson) }
            .getOrElse { throw IllegalArgumentException("VMess URI does not contain valid JSON") }

        val host = obj.optString("add", "").trim()
        val port = obj.optString("port", "").trim().toIntOrNull() ?: obj.optInt("port", -1)
        val uuid = obj.optString("id", "").trim()
        require(host.isNotBlank()) { "VMess URI is missing server address (add)" }
        require(port in 1..65535) { "VMess URI has invalid port: $port" }
        require(uuid.isNotBlank()) { "VMess URI is missing UUID (id)" }

        val name = obj.optString("ps", "").trim().ifBlank { "VMess Import" }
        val net = obj.optString("net", "tcp").trim().lowercase().ifBlank { "tcp" }
        val rawTls = obj.optString("tls", "").trim().lowercase()
        val security = when (rawTls) {
            "tls" -> "tls"
            "reality" -> "reality"
            else -> "none"
        }
        val sni = obj.optString("sni", "").trim().ifBlank {
            obj.optString("host", "").trim().ifBlank { host }
        }
        val hostHeader = obj.optString("host", "").trim().ifBlank { sni }
        val path = obj.optString("path", "").trim().ifBlank { "/" }
        val serviceName = obj.optString("serviceName", "").trim().ifBlank {
            if (net == "grpc") obj.optString("path", "").trim() else ""
        }
        val alpn = obj.optString("alpn", "h2,http/1.1").trim()
        val fp = obj.optString("fp", "chrome").trim().ifBlank { "chrome" }
        val allowInsecure = parseBoolParam(
            obj.optString("allowInsecure", "").ifBlank { obj.optString("insecure", "") },
            default = true
        )

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "VMESS",
            server = host,
            port = port,
            uuid = uuid,
            security = security,
            sni = sni,
            alpn = alpn,
            fingerprint = fp,
            allowInsecure = allowInsecure,
            transport = net,
            wsPath = path,
            wsHost = hostHeader,
            grpcServiceName = serviceName,
            httpUpgradePath = if (net == "httpupgrade") path else "",
            httpUpgradeHost = if (net == "httpupgrade") hostHeader else ""
        )
    }

    private fun parseTrojan(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val password = uri.userInfo.orEmpty().trim()
        val host = uri.host.orEmpty().trim()
        val port = if (uri.port > 0) uri.port else 443
        require(host.isNotBlank()) { "Trojan URI is missing server host" }
        require(port in 1..65535) { "Trojan URI has invalid port: $port" }
        require(password.isNotBlank()) { "Trojan URI is missing password" }

        val name = decodeFragment(uri.fragment, "Trojan Import")
        val security = uri.getQueryParameter("security")?.lowercase()?.ifBlank { "tls" } ?: "tls"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: host
        val alpn = uri.getQueryParameter("alpn") ?: "h2,http/1.1"
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val pbk = uri.getQueryParameter("pbk").orEmpty().trim()
        val sid = uri.getQueryParameter("sid").orEmpty().trim()
        val spx = uri.getQueryParameter("spx").orEmpty().trim()
        val allowInsecure = parseBoolParam(
            uri.getQueryParameter("allowInsecure") ?: uri.getQueryParameter("insecure"),
            default = true
        )

        if (security == "reality") {
            require(pbk.isNotBlank()) { "Trojan Reality URI is missing public key (pbk)" }
        }

        val type = uri.getQueryParameter("type")?.lowercase()?.ifBlank { "tcp" } ?: "tcp"
        val path = uri.getQueryParameter("path") ?: "/"
        val hostParam = uri.getQueryParameter("host") ?: sni
        val serviceName = uri.getQueryParameter("serviceName").orEmpty().trim()

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "TROJAN",
            server = host,
            port = port,
            trojanPassword = password,
            security = security,
            sni = sni,
            alpn = alpn,
            fingerprint = fp,
            allowInsecure = allowInsecure,
            realityPublicKey = pbk,
            realityShortId = sid,
            realitySpiderX = spx,
            transport = type,
            wsPath = path,
            wsHost = hostParam,
            grpcServiceName = serviceName,
            httpUpgradePath = if (type == "httpupgrade") path else "",
            httpUpgradeHost = if (type == "httpupgrade") hostParam else ""
        )
    }

    private fun parseShadowsocks(uriStr: String): ProfileEntity {
        val withoutScheme = uriStr.substringAfter("://")
        val fragmentPart = if (withoutScheme.contains("#")) withoutScheme.substringAfter("#") else null
        val beforeFragment = withoutScheme.substringBefore("#")
        val authorityPart = beforeFragment.substringBefore("?").trim()
        val name = decodeFragment(fragmentPart, "SS Import")

        var host = ""
        var port = 8388
        var method = ""
        var password = ""

        if (authorityPart.contains("@")) {
            // SIP002: ss://BASE64(method:password)@host:port or ss://method:password@host:port
            val userInfoRaw = authorityPart.substringBeforeLast("@")
            val hostPortRaw = authorityPart.substringAfterLast("@")
            val (parsedHost, parsedPort) = parseHostAndPort(hostPortRaw, 8388)
            host = parsedHost
            port = parsedPort

            val userInfoDecoded = runCatching { URLDecoder.decode(userInfoRaw, "UTF-8") }.getOrDefault(userInfoRaw)
            if (userInfoDecoded.contains(":")) {
                val parts = userInfoDecoded.split(":", limit = 2)
                method = parts[0].trim()
                password = parts[1]
            } else {
                val b64Decoded = decodeBase64Flexible(userInfoRaw)
                    ?: throw IllegalArgumentException("Shadowsocks URI has invalid Base64 credentials")
                require(b64Decoded.contains(":")) { "Shadowsocks credentials must be method:password" }
                val parts = b64Decoded.split(":", limit = 2)
                method = parts[0].trim()
                password = parts[1]
            }
        } else {
            // Legacy fully-base64 form: ss://BASE64(method:password@host:port)
            val decoded = decodeBase64Flexible(authorityPart)
                ?: throw IllegalArgumentException("Shadowsocks URI is not valid Base64")
            require(decoded.contains("@")) { "Shadowsocks Base64 URI is missing '@host:port'" }
            val credsPart = decoded.substringBeforeLast("@")
            val hostPortRaw = decoded.substringAfterLast("@")
            require(credsPart.contains(":")) { "Shadowsocks Base64 URI is missing 'method:password'" }
            val credParts = credsPart.split(":", limit = 2)
            method = credParts[0].trim()
            password = credParts[1]
            val (parsedHost, parsedPort) = parseHostAndPort(hostPortRaw, 8388)
            host = parsedHost
            port = parsedPort
        }

        require(host.isNotBlank()) { "Shadowsocks URI is missing server host" }
        require(port in 1..65535) { "Shadowsocks URI has invalid port: $port" }
        require(method.isNotBlank()) { "Shadowsocks URI is missing cipher method" }
        require(password.isNotBlank()) { "Shadowsocks URI is missing password" }

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
        val auth = uri.userInfo.orEmpty().trim()
        val host = uri.host.orEmpty().trim()
        val port = if (uri.port > 0) uri.port else 443
        require(host.isNotBlank()) { "Hysteria2 URI is missing server host" }
        require(port in 1..65535) { "Hysteria2 URI has invalid port: $port" }
        require(auth.isNotBlank()) { "Hysteria2 URI is missing auth/password" }

        val name = decodeFragment(uri.fragment, "HY2 Import")
        val sni = uri.getQueryParameter("sni") ?: host
        val obfs = uri.getQueryParameter("obfs").orEmpty().trim()
        val obfsPass = uri.getQueryParameter("obfs-password").orEmpty()
        val mport = (uri.getQueryParameter("mport") ?: uri.getQueryParameter("ports")).orEmpty().trim()
        val hopInterval = (uri.getQueryParameter("mportHopInt") ?: uri.getQueryParameter("hop-interval")).orEmpty().trim()
        val pinSha256 = uri.getQueryParameter("pinSHA256").orEmpty().trim()
        val upBw = parseBandwidthMbps(uri.getQueryParameter("up"))
        val downBw = parseBandwidthMbps(uri.getQueryParameter("down"))
        val allowInsecure = parseBoolParam(
            uri.getQueryParameter("insecure") ?: uri.getQueryParameter("allowInsecure"),
            default = false
        )

        return ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            protocolType = "XRAY",
            xrayProtocol = "HYSTERIA2",
            server = host,
            port = port,
            hy2Auth = auth,
            sni = sni,
            hy2UpBandwidth = upBw,
            hy2DownBandwidth = downBw,
            hy2Ports = mport,
            hy2ObfsType = obfs,
            hy2ObfsPassword = obfsPass,
            hy2PinSha256 = pinSha256,
            hy2HopInterval = hopInterval,
            allowInsecure = allowInsecure
        )
    }

    private fun parseSsh(uriStr: String): ProfileEntity {
        val uri = Uri.parse(uriStr)
        val userInfo = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty().trim()
        val port = if (uri.port > 0) uri.port else 22
        require(host.isNotBlank()) { "SSH URI is missing server host" }
        require(port in 1..65535) { "SSH URI has invalid port: $port" }

        val name = decodeFragment(uri.fragment, "SSH Import")

        var user = ""
        var pass = ""
        if (userInfo.contains(":")) {
            val parts = userInfo.split(":", limit = 2)
            user = parts[0].trim()
            pass = parts[1]
        } else if (userInfo.isNotEmpty()) {
            user = userInfo.trim()
        }
        require(user.isNotBlank()) { "SSH URI is missing username" }

        val mode = uri.getQueryParameter("mode")?.uppercase()?.ifBlank { "DIRECT" } ?: "DIRECT"
        val sni = uri.getQueryParameter("sni").orEmpty().trim()
        val payload = uri.getQueryParameter("payload").orEmpty()
        val proxyHost = (
            uri.getQueryParameter("proxyHost")
                ?: uri.getQueryParameter("proxy_host")
                ?: uri.getQueryParameter("proxy")
            ).orEmpty().trim()
        val proxyPort = (
            uri.getQueryParameter("proxyPort")
                ?: uri.getQueryParameter("proxy_port")
            )?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8080
        val proxyUser = (
            uri.getQueryParameter("proxyUser")
                ?: uri.getQueryParameter("proxy_user")
            ).orEmpty().trim()
        val proxyPass = (
            uri.getQueryParameter("proxyPass")
                ?: uri.getQueryParameter("proxy_pass")
            ).orEmpty()
        val proxyAuth = parseBoolParam(
            uri.getQueryParameter("proxyAuth") ?: uri.getQueryParameter("proxy_auth"),
            default = proxyUser.isNotBlank()
        )

        if (mode == "PROXY" || mode == "PROXY_TLS") {
            require(proxyHost.isNotBlank()) { "SSH $mode URI is missing proxyHost" }
        }

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
            sni = sni,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyAuth = proxyAuth,
            proxyUser = proxyUser,
            proxyPass = proxyPass
        )
    }

    private fun decodeFragment(raw: String?, fallback: String): String {
        return runCatching { URLDecoder.decode(raw.orEmpty(), "UTF-8") }
            .getOrDefault(fallback)
            .trim()
            .ifBlank { fallback }
    }

    private fun decodeBase64Flexible(raw: String): String? {
        val cleaned = raw.trim()
            .replace("\r", "")
            .replace("\n", "")
            .replace('-', '+')
            .replace('_', '/')
        if (cleaned.isEmpty()) return null
        val mod = cleaned.length % 4
        val padded = when (mod) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }
        return runCatching {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun parseHostAndPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = hostPort.trim().removeSuffix("/")
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx > 0) {
                val ipv6 = trimmed.substring(1, closeIdx)
                val remainder = trimmed.substring(closeIdx + 1)
                val port = if (remainder.startsWith(":")) {
                    remainder.substring(1).toIntOrNull() ?: defaultPort
                } else {
                    defaultPort
                }
                return ipv6 to port
            }
        }
        val colonIdx = trimmed.lastIndexOf(':')
        return if (colonIdx > 0) {
            val h = trimmed.substring(0, colonIdx).trim()
            val p = trimmed.substring(colonIdx + 1).trim().toIntOrNull() ?: defaultPort
            h to p
        } else {
            trimmed to defaultPort
        }
    }

    private fun parseBoolParam(raw: String?, default: Boolean): Boolean {
        return when (raw?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
    }

    private fun parseBandwidthMbps(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val digits = raw.trim().takeWhile { it.isDigit() }
        return digits.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
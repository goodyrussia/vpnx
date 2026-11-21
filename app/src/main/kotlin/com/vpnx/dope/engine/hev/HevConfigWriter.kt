package com.vpnx.dope.engine.hev

import java.io.File

object HevConfigWriter {

    fun writeVpnModeConfig(outFile: File, mtu: Int, socksPort: Int = 10808): String {
        val yaml = """
            tunnel:
              mtu: $mtu
            socks5:
              port: $socksPort
              address: '127.0.0.1'
              udp: 'udp'
            misc:
              limit-nofile: 65535
              log-level: 'warn'
        """.trimIndent()
        outFile.parentFile?.mkdirs()
        outFile.writeText(yaml)
        return outFile.absolutePath
    }

    fun writeRootModeConfig(
        outFile: File,
        tunName: String = "vpnx0",
        mtu: Int = 1400,
        ipv4: String = "10.0.0.1",
        ipv6: String = "fc00::1",
        socksPort: Int = 10808,
        fwmark: Int = 438,
        logFile: File,
        pidFile: File
    ): String {
        val yaml = """
            tunnel:
              name: '${tunName.escapeYamlSingleQuoted()}'
              mtu: $mtu
              multi-queue: true
              ipv4: '${ipv4.escapeYamlSingleQuoted()}'
              ipv6: '${ipv6.escapeYamlSingleQuoted()}'
            socks5:
              port: $socksPort
              address: '127.0.0.1'
              udp: 'udp'
              mark: $fwmark
              tcp-fastopen: true
            misc:
              limit-nofile: 65535
              log-file: '${logFile.absolutePath.escapeYamlSingleQuoted()}'
              log-level: 'warn'
              pid-file: '${pidFile.absolutePath.escapeYamlSingleQuoted()}'
        """.trimIndent()
        outFile.parentFile?.mkdirs()
        outFile.writeText(yaml)
        return outFile.absolutePath
    }

    private fun String.escapeYamlSingleQuoted(): String = replace("'", "''")
}
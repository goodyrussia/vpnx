package com.vpnx.dope

import android.app.Application
import com.topjohnwu.superuser.Shell

/**
 * Application entry point.
 *
 * libsu requires Shell.setDefaultBuilder() to be called before ANY Shell
 * API is used (including Shell.rootAccess() and Shell.getShell()). Without
 * it, libsu uses a zero-configuration shell that fails immediately on
 * KernelSU-Next / Android 16 because:
 *   - FLAG_MOUNT_MASTER is absent (required by many KernelSU builds)
 *   - timeout defaults to 0, so the su permission prompt has no time to appear
 *   - the shell is rebuilt with defaults on every cold start, caching nothing
 *
 * The static initializer here runs before any Activity or Service, which is
 * the only safe point: libsu documents that setDefaultBuilder() must be
 * called before any Shell operation[1].
 *
 * [1] https://github.com/topjohnwu/libsu#initialization
 */
class VpnxApplication : Application() {
    companion object {
        init {
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10) // 10 s — enough for KernelSU to show the grant dialog
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
    }
}

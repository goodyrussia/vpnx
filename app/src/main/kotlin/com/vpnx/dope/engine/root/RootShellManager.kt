package com.vpnx.dope.engine.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

data class ShellExecResult(
    val code: Int,
    val stdout: List<String>,
    val stderr: List<String>
) {
    val isSuccess: Boolean get() = code == 0
    val output: String get() = (stdout + stderr).joinToString("\n")
}

object RootShellManager {
    private const val DEFAULT_EXEC_TIMEOUT_MS = 10_000L

    private val lock = Any()
    @Volatile
    private var initialized = false

    private val execExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "RootShellExec").apply { isDaemon = true }
    }

    /**
     * Configures libsu 6.0.0 exactly once from Application.onCreate() (PLAN.md section 6.1).
     * Never called twice; never configured in a static initializer.
     */
    fun init() {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            Shell.enableVerboseLogging = false
            runCatching {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER)
                        .setTimeout(10) // 10s timeout for KernelSU-Next prompt
                )
            }
            initialized = true
        }
    }

    /**
     * Non-blocking root check with strict 8-second timeout on Dispatchers.IO.
     * Guaranteed never to run on the Main thread.
     */
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        init()
        runCatching {
            withTimeout(8000) {
                Shell.getShell().isRoot
            }
        }.getOrDefault(false)
    }

    /**
     * Executes a shell command asynchronously on Dispatchers.IO with a strict timeout.
     */
    suspend fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS
    ): ShellExecResult = withContext(Dispatchers.IO) {
        init()
        val deferred = CompletableDeferred<ShellExecResult>()
        val future = execExecutor.submit {
            try {
                val stdout = mutableListOf<String>()
                val stderr = mutableListOf<String>()
                val job = Shell.cmd(command).to(stdout, stderr).exec()
                deferred.complete(ShellExecResult(job.code, stdout, stderr))
            } catch (t: Throwable) {
                deferred.complete(
                    ShellExecResult(
                        code = -1,
                        stdout = emptyList(),
                        stderr = listOf(t.message ?: "Root shell command failed")
                    )
                )
            }
        }
        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            future.cancel(true)
            ShellExecResult(
                code = -1,
                stdout = emptyList(),
                stderr = listOf("Root command timed out after ${timeoutMs}ms: $command")
            )
        }
    }
}
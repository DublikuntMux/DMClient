package com.dublikunt.dmclient.crash

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process.killProcess
import java.io.File
import kotlin.system.exitProcess

object CrashReporter {
    const val PENDING_REPORT_FILE_NAME = "last_crash_report.txt"

    @Volatile
    private var installed = false

    fun pendingReportFile(filesDir: File): File = File(filesDir, PENDING_REPORT_FILE_NAME)

    fun pendingReportFile(context: Context): File = pendingReportFile(context.filesDir)

    fun hasPendingReport(context: Context): Boolean =
        pendingReportFile(context).let { it.exists() && it.length() > 0 }

    fun savePendingReport(filesDir: File, report: String) {
        val target = pendingReportFile(filesDir)
        val tmp = File(filesDir, "$PENDING_REPORT_FILE_NAME.tmp")
        tmp.writeText(report)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun consumePendingReport(filesDir: File): String? {
        val file = pendingReportFile(filesDir)
        if (!file.exists()) return null
        return try {
            file.readText().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun consumePendingReport(context: Context): String? = consumePendingReport(context.filesDir)

    fun clearPendingReport(filesDir: File) {
        try {
            pendingReportFile(filesDir).delete()
        } catch (_: Exception) {
        }
    }

    fun clearPendingReport(context: Context) = clearPendingReport(context.filesDir)

    fun collectEnvironment(context: Context, thread: Thread): CrashEnvironment {
        var versionName = "unknown"
        var versionCode = 0L
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "unknown"
            @Suppress("DEPRECATION")
            versionCode = packageInfo.versionCode.toLong()
        } catch (_: Exception) {
        }
        return CrashEnvironment(
            appVersionName = versionName,
            appVersionCode = versionCode,
            deviceManufacturer = Build.MANUFACTURER ?: "unknown",
            deviceModel = Build.MODEL ?: "unknown",
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            androidSdkInt = Build.VERSION.SDK_INT,
            timestampUtc = CrashEnvironment.nowUtc(),
            threadName = thread.name
        )
    }

    fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val environment = collectEnvironment(context, thread)
            val report = formatCrashReport(environment, throwable)
            savePendingReport(context.filesDir, report)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun install(application: Application) {
        if (installed) return
        installed = true
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrash(application, thread, throwable)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    @Synchronized
    internal fun resetForTests() {
        installed = false
    }
}

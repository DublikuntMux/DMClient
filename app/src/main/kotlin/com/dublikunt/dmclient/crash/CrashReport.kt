package com.dublikunt.dmclient.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

const val CRASH_ISSUES_URL = "https://github.com/DublikuntMux/DMClient/issues/new"

private const val MAX_STACKTRACE_CHARS = 32_000

data class CrashEnvironment(
    val appVersionName: String = "unknown",
    val appVersionCode: Long = 0L,
    val deviceManufacturer: String = "unknown",
    val deviceModel: String = "unknown",
    val androidRelease: String = "unknown",
    val androidSdkInt: Int = 0,
    val timestampUtc: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
    val threadName: String = "unknown"
) {
    companion object {
        fun nowUtc(): String =
            DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
    }
}

fun stackTraceAsString(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    val full = writer.toString()
    return if (full.length > MAX_STACKTRACE_CHARS) {
        full.take(MAX_STACKTRACE_CHARS) + "\n... (truncated, full length=${full.length} chars)"
    } else {
        full
    }
}

fun formatCrashReport(environment: CrashEnvironment, throwable: Throwable): String {
    val message = throwable.message?.takeIf { it.isNotBlank() }
    val exceptionLine =
        if (message == null) throwable.javaClass.name else "${throwable.javaClass.name}: $message"
    return buildString {
        appendLine("# DMClient Crash Report")
        appendLine()
        appendLine("- App: ${environment.appVersionName} (${environment.appVersionCode})")
        appendLine("- Device: ${environment.deviceManufacturer} ${environment.deviceModel}".trim())
        appendLine("- Android: ${environment.androidRelease} (API ${environment.androidSdkInt})")
        appendLine("- Timestamp (UTC): ${environment.timestampUtc}")
        appendLine("- Thread: ${environment.threadName}")
        appendLine()
        appendLine("## Exception")
        appendLine(exceptionLine)
        appendLine()
        appendLine("## Stack trace")
        appendLine("```")
        val stack = stackTraceAsString(throwable)
        append(stack)
        if (!stack.endsWith("\n")) appendLine()
        appendLine("```")
        appendLine()
        appendLine("Please describe what you were doing when the crash happened.")
    }.trimEnd() + "\n"
}

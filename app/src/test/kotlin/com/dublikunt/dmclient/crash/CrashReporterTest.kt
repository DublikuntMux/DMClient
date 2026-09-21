package com.dublikunt.dmclient.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashReporterTest {

    private fun testEnv() = CrashEnvironment(
        appVersionName = "1.1.0",
        appVersionCode = 12,
        deviceManufacturer = "Google",
        deviceModel = "Pixel",
        androidRelease = "14",
        androidSdkInt = 34,
        timestampUtc = "2026-09-21T00:00:00Z",
        threadName = "main"
    )

    @Test
    fun `format includes metadata exception and causes`() {
        val cause = IllegalArgumentException("bad arg")
        val error = RuntimeException("boom", cause)

        val report = formatCrashReport(testEnv(), error)

        assertTrue(report.contains("DMClient Crash Report"))
        assertTrue(report.contains("1.1.0 (12)"))
        assertTrue(report.contains("Google Pixel"))
        assertTrue(report.contains("14 (API 34)"))
        assertTrue(report.contains("RuntimeException: boom"))
        assertTrue(report.contains("IllegalArgumentException: bad arg"))
        assertTrue(report.contains("Caused by:"))
        assertTrue(report.contains("```"))
    }

    @Test
    fun `format handles null message without trailing colon`() {
        val error = RuntimeException(null as String?)

        val report = formatCrashReport(testEnv(), error)

        assertTrue(report.contains("java.lang.RuntimeException"))
        assertTrue(!report.contains("java.lang.RuntimeException:"))
    }

    @Test
    fun `pending report roundtrip through filesDir`() {
        val dir: File = Files.createTempDirectory("crash-test").toFile()
        try {
            assertNull(CrashReporter.consumePendingReport(dir))

            val report = formatCrashReport(testEnv(), IllegalStateException("saved"))
            CrashReporter.savePendingReport(dir, report)

            assertTrue(CrashReporter.pendingReportFile(dir).exists())
            assertEquals(report, CrashReporter.consumePendingReport(dir))

            CrashReporter.clearPendingReport(dir)
            assertNull(CrashReporter.consumePendingReport(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `blank pending report is treated as missing`() {
        val dir: File = Files.createTempDirectory("crash-blank").toFile()
        try {
            CrashReporter.savePendingReport(dir, "   ")
            assertNull(CrashReporter.consumePendingReport(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `long stacktrace is truncated`() {
        val deep = generateSequence<Throwable>(RuntimeException("root")) { prev ->
            RuntimeException("wrap ".repeat(500), prev)
        }.take(500).last()

        val report = formatCrashReport(testEnv(), deep)

        assertNotNull(report)
        assertTrue(report.contains("truncated"))
        assertTrue(report.length < 60_000)
    }
}

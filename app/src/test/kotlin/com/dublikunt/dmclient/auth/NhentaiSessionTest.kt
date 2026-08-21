package com.dublikunt.dmclient.auth

import com.dublikunt.dmclient.scrapper.EasyCookieJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NhentaiSessionTest {

    private val jar = EasyCookieJar()
    private val authRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val storage = FakeSessionTokenStorage()

    private fun TestScope.session() = NhentaiSession(
        jar, authRequired, storage,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    private fun jarCookie(host: String, name: String): String? =
        jar.loadForRequest("https://$host/".toHttpUrl()).firstOrNull { it.name == name }?.value

    @Test
    fun `restore activates with persisted cookies fanned out to every host`() = runTest {
        storage.saved = SessionCookies(cfClearance = "cf1", csrfToken = "t1", sessionAffinity = "s1")
        val s = session()

        s.restore()

        assertEquals(SessionStatus.Active, s.status.value)
        assertEquals("cf1", jarCookie("i1.nhentai.net", "cf_clearance"))
        assertEquals("t1", jarCookie("t.nhentai.net", "csrftoken"))
        assertEquals("s1", jarCookie("nhentai.net", "session-affinity"))
    }

    @Test
    fun `restore without persisted cookies demands a challenge`() = runTest {
        val s = session()

        s.restore()

        assertEquals(SessionStatus.NeedsChallenge, s.status.value)
    }

    @Test
    fun `adopt persists cookies and activates`() = runTest {
        val s = session()

        s.adopt(
            listOf(
                "_ga" to "irrelevant",
                "cf_clearance" to "cf9",
                "csrftoken" to "t9",
                "session-affinity" to "s9"
            )
        )

        assertEquals(SessionStatus.Active, s.status.value)
        assertEquals(
            SessionCookies("cf9", "t9", "s9"),
            storage.saved
        )
        assertEquals("cf9", jarCookie("i1.nhentai.net", "cf_clearance"))
    }

    @Test
    fun `adopting cookies without clearance does not activate`() = runTest {
        val s = session()

        s.adopt(listOf("csrftoken" to "t1"))

        assertEquals(SessionStatus.NeedsChallenge, s.status.value)
    }

    @Test
    fun `wipe clears both jar and persistence`() = runTest {
        storage.saved = SessionCookies("cf1", "t1", "s1")
        val s = session()
        s.restore()

        s.wipe()

        assertEquals(SessionStatus.NeedsChallenge, s.status.value)
        assertEquals(null, storage.saved)
        assertTrue(jar.loadForRequest("https://nhentai.net/".toHttpUrl()).isEmpty())
    }

    @Test
    fun `rejection while active invalidates the session`() = runTest {
        storage.saved = SessionCookies("cf1", "t1", "s1")
        val s = session()
        s.restore()

        authRequired.tryEmit(Unit)

        assertEquals(SessionStatus.NeedsChallenge, s.status.value)
        assertEquals(null, storage.saved)
    }

    @Test
    fun `rejection while inactive is ignored`() = runTest {
        val s = session()
        s.restore()
        val before = storage.saved

        authRequired.tryEmit(Unit)

        assertEquals(SessionStatus.NeedsChallenge, s.status.value)
        assertEquals(before, storage.saved)
    }
}

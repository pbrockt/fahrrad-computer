package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.update.AppVersion
import com.pbrockt.fahrradcomputer.update.ReleaseInfo
import com.pbrockt.fahrradcomputer.update.UpdateChannel
import com.pbrockt.fahrradcomputer.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelTest {

    private fun v(s: String) = AppVersion.parse(s)!!

    private fun release(tag: String, pre: Boolean = tag.contains("alpha")) =
        ReleaseInfo(v(tag), pre, "", "a.apk", "https://api/asset")

    @Test
    fun parse() {
        assertEquals(AppVersion(1, 2, 3), v("1.2.3"))
        assertEquals(AppVersion(1, 2, 3, 4), v("v1.2.3-alpha.4"))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun alphaSortsBeforeRelease() {
        assertTrue(v("1.2.0-alpha.3") < v("1.2.0"))
        assertTrue(v("1.2.0-alpha.2") < v("1.2.0-alpha.10"))
        assertTrue(v("1.2.0") < v("1.3.0-alpha.1"))
        assertTrue(v("0.9.9") < v("0.10.0"))
    }

    private val releases = listOf(
        release("0.1.0"),
        release("0.2.0-alpha.1"),
        release("0.2.0-alpha.2"),
    )

    @Test
    fun releaseChannelIgnoresAlphas() {
        assertNull(UpdateChecker.pick(releases, UpdateChannel.RELEASE, v("0.1.0")))
    }

    @Test
    fun alphaChannelTakesNewestAlpha() {
        assertEquals(v("0.2.0-alpha.2"), UpdateChecker.pick(releases, UpdateChannel.ALPHA, v("0.1.0"))?.version)
    }

    @Test
    fun alphaUserGetsFinalRelease() {
        val withFinal = releases + release("0.2.0")
        assertEquals(v("0.2.0"), UpdateChecker.pick(withFinal, UpdateChannel.ALPHA, v("0.2.0-alpha.2"))?.version)
        assertEquals(v("0.2.0"), UpdateChecker.pick(withFinal, UpdateChannel.RELEASE, v("0.2.0-alpha.2"))?.version)
    }

    @Test
    fun alphaTagWithoutPrereleaseFlagStaysOutOfReleaseChannel() {
        val sloppy = listOf(release("0.3.0-alpha.1", pre = false))
        assertNull(UpdateChecker.pick(sloppy, UpdateChannel.RELEASE, v("0.1.0")))
    }

    @Test
    fun nothingNewer() {
        assertNull(UpdateChecker.pick(releases, UpdateChannel.ALPHA, v("0.2.0-alpha.2")))
    }
}

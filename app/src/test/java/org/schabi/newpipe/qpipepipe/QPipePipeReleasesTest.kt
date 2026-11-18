package org.schabi.newpipe.qpipepipe

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QPipePipeReleasesTest {
    private val arm64 = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val release542 = InstalledBuild("5.4.0-q2", nightly = null)
    private val nightly36 = InstalledBuild("5.5.0-q1", nightly = 36)

    private fun release(
        tag: String,
        name: String = tag,
        prerelease: Boolean = false,
        apks: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            .map { "QPipePipe_${tag.removePrefix("v")}-$it-release.apk" }
    ): String {
        val assets = apks.joinToString(",") {
            """{"name":"$it","browser_download_url":"https://dl/$tag/$it"}"""
        }
        return """{"tag_name":"$tag","name":"$name","prerelease":$prerelease,""" +
            """"html_url":"https://page/$tag","assets":[$assets]}"""
    }

    private fun nightly(version: String, number: Int, commit: String = "def5678") =
        release(
            "nightly_$version", name = "Nightly $version", prerelease = true,
            apks = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").map {
                "QPipePipe_$version-nightly$number-$commit-$it-debug.apk"
            }
        )

    private fun find(
        installed: InstalledBuild,
        vararg releases: String,
        includePreRelease: Boolean = false,
        abis: Array<String> = arm64
    ) = QPipePipeReleases.findUpdate(
        JsonParser.array().from("[" + releases.joinToString(",") + "]"),
        installed, includePreRelease, abis
    )

    @Test
    fun aReleaseOffersTheNewestRelease() {
        val update = find(release542, release("v5.4.0-q3"), release("v5.4.0-q1"))!!
        assertEquals("5.4.0-q3", update.version)
        assertEquals("https://dl/v5.4.0-q3/QPipePipe_5.4.0-q3-arm64-v8a-release.apk", update.apkUrl)
        assertEquals("https://page/v5.4.0-q3", update.releaseUrl)
    }

    @Test
    fun aReleaseUpToDateOffersNothing() {
        assertNull(find(release542, release("v5.4.0-q2"), release("v5.4.0-q1")))
    }

    @Test
    fun aReleaseSkipsPreReleasesUnlessAsked() {
        assertNull(find(release542, release("v5.5.0-q1", prerelease = true)))
        assertEquals(
            "5.5.0-q1",
            find(release542, release("v5.5.0-q1", prerelease = true), includePreRelease = true)
                ?.version
        )
    }

    @Test
    fun aReleaseIgnoresNightliesEvenWithPreReleases() {
        assertEquals(
            "5.4.0-q3",
            find(release542, nightly("5.5.0-q1", 36), release("v5.4.0-q3"), includePreRelease = true)
                ?.version
        )
        // A nightly named like a release is still a nightly.
        assertNull(
            find(
                release542, release("nightly_5.5.0-q1", name = "v5.5.0-q1", prerelease = true),
                includePreRelease = true
            )
        )
    }

    @Test
    fun aNameThatIsNotAVersionSkipsThatReleaseOnly() {
        assertEquals(
            "5.4.0-q3",
            find(release542, release("v5.4.0-q9", name = "Test build"), release("v5.4.0-q3"))
                ?.version
        )
    }

    @Test
    fun theApkMatchesTheDeviceAbi() {
        val x86 = find(release542, release("v5.4.0-q3"), abis = arrayOf("x86"))!!
        assertEquals("https://dl/v5.4.0-q3/QPipePipe_5.4.0-q3-x86-release.apk", x86.apkUrl)
        val unknownAbi = find(
            release542,
            release("v5.4.0-q3", apks = listOf("QPipePipe_5.4.0-q3-universal-release.apk")),
            abis = arrayOf("riscv64")
        )!!
        assertEquals("https://dl/v5.4.0-q3/QPipePipe_5.4.0-q3-universal-release.apk", unknownAbi.apkUrl)
        assertNull(find(release542, release("v5.4.0-q3"), abis = arrayOf("riscv64"))!!.apkUrl)
    }

    @Test
    fun aNightlyOffersANewerBuildNumber() {
        val update = find(nightly36, release("v5.6.0-q1"), nightly("5.5.0-q1", 37))!!
        assertEquals("5.5.0-q1-nightly37 (def5678)", update.version)
        assertEquals(
            "https://dl/nightly_5.5.0-q1/QPipePipe_5.5.0-q1-nightly37-def5678-arm64-v8a-debug.apk",
            update.apkUrl
        )
        assertEquals("https://page/nightly_5.5.0-q1", update.releaseUrl)
    }

    @Test
    fun aNightlyApkForX86IsNotTheX8664One() {
        val update = find(nightly36, nightly("5.5.0-q1", 37), abis = arrayOf("x86"))!!
        assertEquals(
            "https://dl/nightly_5.5.0-q1/QPipePipe_5.5.0-q1-nightly37-def5678-x86-debug.apk",
            update.apkUrl
        )
    }

    @Test
    fun aNightlyOffersANewerVersion() {
        assertEquals(
            "5.5.0-q2-nightly36 (def5678)",
            find(nightly36, nightly("5.5.0-q2", 36))?.version
        )
    }

    @Test
    fun aNightlyUpToDateOffersNothingAndIgnoresReleases() {
        assertNull(find(nightly36, nightly("5.5.0-q1", 36), release("v5.6.0-q1")))
        assertNull(find(nightly36, nightly("5.4.0-q2", 30)))
    }

    @Test
    fun aNightlyPicksTheHighestNumber() {
        assertEquals(
            "5.5.0-q2-nightly40 (def5678)",
            find(nightly36, nightly("5.5.0-q1", 38), nightly("5.5.0-q2", 40))?.version
        )
    }

    @Test
    fun aNightlyWithoutANumberedApkIsSkipped() {
        val unnumbered = release(
            "nightly_5.5.0-q2", prerelease = true,
            apks = listOf("QPipePipe_5.5.0-q2-arm64-v8a-debug.apk")
        )
        val badTag = release(
            "nightly_next", prerelease = true,
            apks = listOf("QPipePipe_next-nightly50-def5678-arm64-v8a-debug.apk")
        )
        assertEquals(
            "5.5.0-q1-nightly37 (def5678)",
            find(nightly36, unnumbered, badTag, nightly("5.5.0-q1", 37))?.version
        )
    }

    @Test
    fun aDebugBuildWithoutANumberTakesAnyNightly() {
        assertEquals(
            "5.5.0-q1-nightly36 (def5678)",
            find(InstalledBuild("5.5.0-q1", nightly = 0), nightly("5.5.0-q1", 36))?.version
        )
    }
}

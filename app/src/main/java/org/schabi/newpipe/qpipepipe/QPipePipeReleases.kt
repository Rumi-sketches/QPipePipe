package org.schabi.newpipe.qpipepipe

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.BuildConfig

/**
 * The build the update check compares with: its version ("5.5.0-q1") and, for a debug build,
 * its nightly number (0 without one). [nightly] is null for a release build.
 */
data class InstalledBuild(val version: String, val nightly: Int?)

/** An update to offer: the version to show, the APK for this device and the release page. */
data class QPipePipeUpdate(val version: String, val apkUrl: String?, val releaseUrl: String?)

/**
 * Picks the update to offer among the GitHub releases of QPipePipe ([NewVersionWorker]).
 * A release build follows the releases, a debug build the nightlies: pre-releases tagged
 * `nightly_<version>`, whose APKs are named `QPipePipe_<version>-nightly<n>-<commit>-<abi>-debug.apk`
 * (FORK.md, "Nightly builds"). A release that cannot be read is skipped.
 */
object QPipePipeReleases {
    private const val NIGHTLY_TAG_PREFIX = "nightly_"
    private val RELEASE_APK_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
    private val QPIPEPIPE_RELEASE_SUFFIX = Regex("-q([0-9]+)$")
    private val NIGHTLY_APK = Regex("-nightly([0-9]+)(?:-([0-9a-f]{7,40}))?-")

    /** This build. */
    @JvmField
    val INSTALLED = InstalledBuild(
        BuildConfig.QPIPEPIPE_VERSION,
        if (BuildConfig.DEBUG) BuildConfig.QPIPEPIPE_NIGHTLY else null
    )

    /** The newest release, or nightly, newer than [installed]; null if there is none. */
    @JvmStatic
    fun findUpdate(
        releases: JsonArray,
        installed: InstalledBuild,
        includePreRelease: Boolean,
        abis: Array<String>
    ): QPipePipeUpdate? {
        val all = (0 until releases.size).map { releases.getObject(it) }
        return if (installed.nightly == null) {
            findRelease(all, installed.version, includePreRelease, abis)
        } else {
            findNightly(all, installed.version, installed.nightly, abis)
        }
    }

    private fun findRelease(
        releases: List<JsonObject>,
        installedVersion: String,
        includePreRelease: Boolean,
        abis: Array<String>
    ): QPipePipeUpdate? {
        val installed = parseVersion(installedVersion) ?: return null
        val newest = releases
            .filter { !isNightly(it) && (includePreRelease || !it.getBoolean("prerelease")) }
            .mapNotNull { release ->
                parseVersion(release.getString("name", "").removePrefix("v"))
                    ?.let { release to it }
            }
            .maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
            ?.takeIf { compareVersions(it.second, installed) > 0 }
            ?.first
            ?: return null
        return QPipePipeUpdate(
            newest.getString("name").removePrefix("v"),
            findCompatibleApkUrl(newest, abis), newest.getString("html_url")
        )
    }

    private class Nightly(
        val release: JsonObject,
        val version: String,
        val parsedVersion: Version,
        val number: Int,
        val commit: String?
    )

    private fun findNightly(
        releases: List<JsonObject>,
        installedVersion: String,
        installedNumber: Int,
        abis: Array<String>
    ): QPipePipeUpdate? {
        val installed = parseVersion(installedVersion) ?: return null
        val newest = releases.filter(::isNightly).mapNotNull(::readNightly)
            .maxWithOrNull(
                compareBy<Nightly> { it.number }
                    .then { a, b -> compareVersions(a.parsedVersion, b.parsedVersion) }
            )
            ?.takeIf {
                it.number > installedNumber || compareVersions(it.parsedVersion, installed) > 0
            }
            ?: return null
        val label = "${newest.version}-nightly${newest.number}" +
            (newest.commit?.let { " ($it)" } ?: "")
        return QPipePipeUpdate(
            label, findCompatibleApkUrl(newest.release, abis), newest.release.getString("html_url")
        )
    }

    private fun isNightly(release: JsonObject) =
        release.getString("tag_name", "").startsWith(NIGHTLY_TAG_PREFIX)

    /** The version of the tag and the number and commit in the APK names, or null. */
    private fun readNightly(release: JsonObject): Nightly? {
        val version = release.getString("tag_name", "").removePrefix(NIGHTLY_TAG_PREFIX)
        val parsedVersion = parseVersion(version) ?: return null
        val match = apkNames(release).firstNotNullOfOrNull { NIGHTLY_APK.find(it) } ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        val commit = match.groups[2]?.value
        return Nightly(release, version, parsedVersion, number, commit)
    }

    private fun apkAssets(release: JsonObject): List<JsonObject> {
        val assets = release.getArray("assets") ?: return emptyList()
        return (0 until assets.size)
            .map { assets.getObject(it) }
            .filter { it.getString("name", "").endsWith(".apk") }
    }

    private fun apkNames(release: JsonObject) = apkAssets(release).map { it.getString("name") }

    private fun findCompatibleApkUrl(release: JsonObject, abis: Array<String>): String? {
        val apkAssets = apkAssets(release)
        abis.forEach { abi ->
            val compatibleAsset = apkAssets.firstOrNull { asset ->
                getReleaseApkAbi(asset.getString("name")) == abi
            }
            if (compatibleAsset != null) {
                return compatibleAsset.getString("browser_download_url")
            }
        }

        return apkAssets.firstOrNull { it.getString("name").contains("universal") }
            ?.getString("browser_download_url")
    }

    private fun getReleaseApkAbi(name: String): String? {
        return RELEASE_APK_ABIS.firstOrNull { name.contains("-$it-") }
    }

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val betaVersion: Int?, // null for a stable version
        val qpipepipeRelease: Int // N in "-qN", 0 when absent
    )

    /** "5.4.0-q2", "v5.5.0-beta1"...; null if it is not a version. */
    private fun parseVersion(versionStr: String): Version? {
        val qpipepipeMatch = QPIPEPIPE_RELEASE_SUFFIX.find(versionStr)
        val qpipepipeRelease = qpipepipeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val normalized = versionStr.removePrefix("v").removeSuffix(qpipepipeMatch?.value ?: "")
        val parts = normalized.split("-beta", limit = 2)
        val mainParts = parts[0].split(".").map { it.toIntOrNull() ?: return null }
        if (mainParts.size < 3) return null
        val (major, minor, patch) = mainParts
        val betaVersion = when {
            parts.size == 1 -> null
            parts[1].isEmpty() -> 0
            else -> parts[1].toIntOrNull()
        }
        return Version(major, minor, patch, betaVersion, qpipepipeRelease)
    }

    private fun compareVersions(v1: Version, v2: Version): Int {
        val mainCompare = when {
            v1.major != v2.major -> v1.major.compareTo(v2.major)
            v1.minor != v2.minor -> v1.minor.compareTo(v2.minor)
            v1.patch != v2.patch -> v1.patch.compareTo(v2.patch)
            else -> 0
        }
        if (mainCompare != 0) return mainCompare
        return when {
            v1.betaVersion == null && v2.betaVersion == null ->
                v1.qpipepipeRelease.compareTo(v2.qpipepipeRelease)
            v1.betaVersion == null -> 1
            v2.betaVersion == null -> -1
            else -> v1.betaVersion.compareTo(v2.betaVersion)
        }
    }
}

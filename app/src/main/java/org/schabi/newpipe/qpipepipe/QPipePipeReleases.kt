package org.schabi.newpipe.qpipepipe

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.BuildConfig

/** The build the update check compares with. [nightly] is null for a release build. */
data class InstalledBuild(val version: String, val nightly: Int?)

/** An update to offer: the version to show, the APK for this device and the release page. */
data class QPipePipeUpdate(val version: String, val apkUrl: String?, val releaseUrl: String?)

/** Picks the update to offer among the GitHub releases of QPipePipe ([NewVersionWorker]). */
object QPipePipeReleases {
    private val RELEASE_APK_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
    private val QPIPEPIPE_RELEASE_SUFFIX = Regex("-q([0-9]+)$")

    /** This build. */
    @JvmField
    val INSTALLED = InstalledBuild(BuildConfig.VERSION_NAME, null)

    /** The newest release newer than [installed], or null. [releases] is the GitHub API list. */
    @JvmStatic
    fun findUpdate(
        releases: JsonArray,
        installed: InstalledBuild,
        includePreRelease: Boolean,
        abis: Array<String>
    ): QPipePipeUpdate? {
        var selectedRelease: JsonObject? = null
        for (i in 0 until releases.size) {
            val release = releases.getObject(i)
            if (!includePreRelease && release.getBoolean("prerelease")) continue
            if (selectedRelease == null || isNewerRelease(release, selectedRelease)) {
                selectedRelease = release
            }
        }
        val release = selectedRelease ?: return null
        val versionName = release.getString("name").removePrefix("v")
        if (compareVersions(parseVersion(installed.version), parseVersion(versionName)) >= 0) {
            return null
        }
        return QPipePipeUpdate(
            versionName, findCompatibleApkUrl(release, abis), release.getString("html_url")
        )
    }

    private fun isNewerRelease(newRelease: JsonObject, currentRelease: JsonObject): Boolean {
        val newVersion = parseVersion(newRelease.getString("name").removePrefix("v"))
        val currentVersion = parseVersion(currentRelease.getString("name").removePrefix("v"))
        return compareVersions(newVersion, currentVersion) > 0
    }

    private fun findCompatibleApkUrl(release: JsonObject, abis: Array<String>): String? {
        val assets = release.getArray("assets")
        val apkAssets = (0 until assets.size)
            .map { assets.getObject(it) }
            .filter { it.getString("name").endsWith(".apk") }

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

    private fun parseVersion(versionStr: String): Version {
        val qpipepipeMatch = QPIPEPIPE_RELEASE_SUFFIX.find(versionStr)
        val qpipepipeRelease = qpipepipeMatch?.groupValues?.get(1)?.toInt() ?: 0
        val normalized = versionStr.removePrefix("v").removeSuffix(qpipepipeMatch?.value ?: "")
        val parts = normalized.split("-beta", limit = 2)
        val mainParts = parts[0].split(".").map {
            it.toIntOrNull() ?: throw IllegalArgumentException("Invalid version part: $it")
        }
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

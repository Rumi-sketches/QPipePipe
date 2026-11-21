package org.schabi.newpipe.player.mediabrowser

import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

internal val ID_AUTHORITY: String get() = BuildConfig.APPLICATION_ID
internal val ID_ROOT: String get() = "//$ID_AUTHORITY"
internal const val ID_BOOKMARKS = "playlists"
internal const val ID_HISTORY = "history"
internal const val ID_INFO_ITEM = "item"

/** The queue played last, see [org.schabi.newpipe.qpipepipe.LastPlayback] */
internal const val ID_RESUME = "resume"

internal const val ID_LOCAL = "local"
internal const val ID_REMOTE = "remote"
internal const val ID_URL = "url"
internal const val ID_STREAM = "stream"
internal const val ID_PLAYLIST = "playlist"
internal const val ID_CHANNEL = "channel"

internal fun infoItemTypeToString(type: InfoType): String {
    return when (type) {
        InfoType.STREAM -> ID_STREAM
        InfoType.PLAYLIST -> ID_PLAYLIST
        InfoType.CHANNEL -> ID_CHANNEL
        else -> throw IllegalStateException("Unexpected value: $type")
    }
}

internal fun infoItemTypeFromString(type: String): InfoType {
    return when (type) {
        ID_STREAM -> InfoType.STREAM
        ID_PLAYLIST -> InfoType.PLAYLIST
        ID_CHANNEL -> InfoType.CHANNEL
        else -> throw IllegalStateException("Unexpected value: $type")
    }
}

/** @return the keys whose value differs between [old] and [new], including added and removed */
internal fun <K, V> changedKeys(old: Map<K, V>, new: Map<K, V>): Set<K> {
    return (old.keys + new.keys).filterTo(HashSet()) { old[it] != new[it] }
}

internal fun parseError(mediaId: String): ContentNotAvailableException {
    return ContentNotAvailableException("Failed to parse media ID $mediaId")
}

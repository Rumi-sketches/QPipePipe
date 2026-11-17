package org.schabi.newpipe.qpipepipe.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem
import org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException
import org.schabi.newpipe.player.playqueue.IncompletePlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Issue #34: a repeat-all queue of videos that can't be loaded looped forever, with a toast and
 * a bug report notification every 2 seconds. Each test lands on the items the way the player
 * does, round after round, and records what is reported and when playback stops.
 */
class UnplayableQueueTest {
    private var playQueue: PlayQueue = queueOf(3)
    private var repeatAll = true
    private var stops = 0
    private val shown = mutableListOf<String>()
    private val notice = object : UnplayableQueue.Notice {
        override fun show(item: PlayerMediaItem, hasNext: Boolean) {
            shown += item.title + if (hasNext) " -> next" else ""
        }

        override fun hide() {
            shown += "hidden"
        }
    }
    private val unplayable = UnplayableQueue({ playQueue }, { repeatAll }, { stops++ }, notice)

    private fun queueOf(size: Int): PlayQueue = SinglePlayQueue(
        (1..size).map {
            StreamInfoItem(0, "https://www.youtube.com/watch?v=video$it", "video $it",
                StreamType.VIDEO_STREAM).apply { uploaderName = "uploader" }
        },
        0
    )

    private fun queued(index: Int): PlayerMediaItem = playQueue.getItem(index)!!

    private fun broken(index: Int): PlayerMediaItem = queued(index)
        .withErrors(listOf(StreamInfoLoadException(RuntimeException("video removed"))))

    private fun offline(index: Int): PlayerMediaItem = queued(index)
        .withErrors(listOf(Exception(UnknownHostException("www.youtube.com"))))

    /** The item once its stream info loaded, as LoadedMediaSource tags it. */
    private fun loaded(index: Int): PlayerMediaItem = PlayerMediaItem
        .forStreamInfo(
            StreamInfo(0, "video$index", queued(index).url, "video $index").apply {
                streamType = StreamType.VIDEO_STREAM
                uploaderName = "uploader"
            }
        )
        .withUuid(queued(index).uuid)

    /** Lands on [items] in order and returns which of them may be reported. */
    private fun land(vararg items: PlayerMediaItem): List<Boolean> =
        items.map { unplayable.onLanding(it) }

    @Test
    fun repeatAllQueueOfBrokenVideosStopsAfterOneRoundWithOneMessage() {
        val reports = land(broken(0), broken(1), broken(2))

        assertEquals(1, stops)
        // The first two are reported; the last one ends the round, and the stop message says it.
        assertEquals(listOf(true, true, false), reports)
    }

    @Test
    fun playingTheStoppedQueueAgainStopsAfterAnotherQuietRound() {
        land(broken(0), broken(1), broken(2))

        val reports = land(broken(0), broken(1), broken(2))

        assertEquals(2, stops)
        assertEquals(listOf(false, false, false), reports)
    }

    @Test
    fun singleBrokenVideoOnRepeatAllStopsAtOnce() {
        playQueue = queueOf(1)

        assertEquals(listOf(false), land(broken(0)))
        assertEquals(1, stops)
    }

    @Test
    fun brokenVideosAmongPlayableOnesAreSkippedAndReportedOnce() {
        val firstRound = land(broken(0), loaded(1), broken(2))
        val secondRound = land(broken(0), loaded(1), broken(2))

        assertEquals(0, stops)
        assertEquals(listOf(true, true, true), firstRound)
        assertEquals(listOf(false, true, false), secondRound)
    }

    @Test
    fun withoutRepeatAllBrokenVideosAreReportedAndNothingStops() {
        repeatAll = false

        assertEquals(listOf(true, true, true), land(broken(0), broken(1), broken(2)))
        assertEquals(0, stops)
    }

    @Test
    fun anItemLeftForTheNetworkRecoveryDoesNotCountAsBroken() {
        val reports = land(broken(0), offline(1), broken(2), broken(0), offline(1), broken(2))

        assertEquals(0, stops)
        assertEquals(listOf(true, true, true, false, true, false), reports)
    }

    // A video that played, then broke (its reload failed), must not end the round it played in.
    @Test
    fun aVideoThatLoadedStartsANewRound() {
        playQueue = queueOf(2)

        land(broken(0), loaded(1), broken(1))
        assertEquals(0, stops)

        land(broken(0))
        assertEquals(1, stops)
    }

    // The player can land on the placeholder of an item whose extraction is still running.
    @Test
    fun aPlaceholderDoesNotStartANewRound() {
        land(broken(0), broken(1), PlayerMediaItem.placeholder(), broken(2))

        assertEquals(1, stops)
    }

    // The first page of a remote playlist: more videos come once the player reaches its end.
    @Test
    fun aQueueWithMoreVideosToFetchDoesNotStop() {
        playQueue = IncompletePlayQueue(queueOf(3).streams)

        land(broken(0), broken(1), broken(2))

        assertEquals(0, stops)
    }

    // Issue #41: the player holds on a broken video and says why, counting down to the next one.
    @Test
    fun aBrokenVideoShowsItsReasonWithACountdownWhenAnotherVideoFollows() {
        repeatAll = false

        land(broken(0), loaded(1), broken(2))

        assertEquals(listOf("video 1 -> next", "hidden", "video 3"), shown)
    }

    // The first page of a remote playlist: more videos come after its last loaded one.
    @Test
    fun theLastLoadedVideoOfAQueueWithMoreToFetchCountsDown() {
        repeatAll = false
        playQueue = IncompletePlayQueue(queueOf(2).streams)

        land(loaded(0), broken(1))

        assertEquals(listOf("hidden", "video 2 -> next"), shown)
    }

    @Test
    fun withRepeatAllTheLastBrokenVideoCountsDownToTheFirst() {
        playQueue = queueOf(2)

        land(loaded(0), broken(1))

        assertEquals(listOf("hidden", "video 2 -> next"), shown)
    }

    // Issue #34 still holds: the round that found nothing playable ends with one message, no
    // countdown that would start it again.
    @Test
    fun theStopAtTheEndOfABrokenRoundShowsNoCountdown() {
        land(broken(0), broken(1), broken(2))

        assertEquals(1, stops)
        assertEquals(listOf("video 1 -> next", "video 2 -> next", "hidden"), shown)
    }

    @Test
    fun anItemLeftForTheNetworkRecoveryHidesTheNotice() {
        land(broken(0), offline(1))

        assertEquals(listOf("video 1 -> next", "hidden"), shown)
    }

    // Phase review of issue-aperte: the extractor wraps a network error of a later request in a
    // ParsingException, and MediaSourceManager took it for a broken video: countdown, "can't be
    // played", and a repeat-all queue stopped while only the connection was missing.
    @Test
    fun aNetworkErrorTheExtractorWrappedIsNotABrokenVideo() {
        val wrapped = ParsingException("Failed to fetch latest player metadata",
            UnknownHostException("www.youtube.com"))

        assertEquals(false, UnplayableQueue.isBrokenSource(wrapped))
        assertEquals(false, UnplayableQueue.isBrokenSource(
            IllegalStateException("SABR session", SocketTimeoutException("timeout"))))
        assertEquals(false, UnplayableQueue.isBrokenSource(UnknownHostException("www.youtube.com")))
    }

    @Test
    fun aVideoTheExtractorCantLoadIsStillBroken() {
        assertEquals(true, UnplayableQueue.isBrokenSource(ContentNotAvailableException("removed")))
        assertEquals(true, UnplayableQueue.isBrokenSource(
            ParsingException("Failed to fetch latest player metadata", ReCaptchaException("", ""))))
        assertEquals(true, UnplayableQueue.isBrokenSource(IllegalStateException("no SABR probe")))
    }

    @Test
    fun aRepeatAllQueueWithoutConnectionDoesNotStopAsBroken() {
        // How MediaSourceManager tags each item, as in its onErrorReturn
        val error = ParsingException("Failed to call decode API", UnknownHostException("api"))
        val tagged = if (UnplayableQueue.isBrokenSource(error)) StreamInfoLoadException(error)
            else Exception(error)

        land(*(0..2).map { queued(it).withErrors(listOf(tagged)) }.toTypedArray())

        assertEquals(0, stops)
        assertEquals(listOf("hidden", "hidden", "hidden"), shown)
    }

    // Issue #41: «Resume» on a playlist whose resume video is broken showed only the video
    // page's error, and the playlist never reached the player. Upstream starts the player only
    // once the first video loaded: false for every queue.
    @Test
    fun aQueueStillPlaysWhenItsFirstVideoCantBeLoaded() {
        assertEquals(true, UnplayableQueue.playsDespiteLoadError(queueOf(3)))
        assertEquals(false, UnplayableQueue.playsDespiteLoadError(queueOf(1)))
        assertEquals(false, UnplayableQueue.playsDespiteLoadError(null))
    }
}

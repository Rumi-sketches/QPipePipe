package org.schabi.newpipe.qpipepipe.player

import androidx.annotation.StringRes
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorPanelHelper

/** Why a queue item can't be played, in words for the user. */
object UnplayableReason {

    /**
     * The reason the extractor gave in [errors] (removed, private, age-restricted, blocked in
     * the country...), looked up in the whole cause chain since the player wraps it; when none of
     * them says why, as with a video YouTube removed (the extractor only reports an invalid
     * player response), a plain "This video can't be played".
     */
    @StringRes
    @JvmStatic
    fun of(errors: List<Throwable>): Int {
        for (error in errors) {
            var cause: Throwable? = error
            while (cause != null) {
                val description = ErrorPanelHelper.getExceptionDescription(cause)
                if (description != R.string.error_snackbar_message &&
                    description != R.string.network_error
                ) {
                    return description
                }
                cause = cause.cause.takeIf { it !== cause }
            }
        }
        return R.string.unplayable_video
    }
}

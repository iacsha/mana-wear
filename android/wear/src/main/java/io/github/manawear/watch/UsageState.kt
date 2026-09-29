package io.github.manawear.watch

import io.github.manawear.shared.FetchResult
import io.github.manawear.shared.Payload

/** What the screen shows. A failed fetch never replaces the last good reading. */
data class UsageState(
    val loading: Boolean = false,
    val lastGood: Payload? = null,
    val problem: Problem? = null,
    /** Short diagnostic for the problem, such as `relay-timeout` or `HTTP 502`. */
    val detail: String? = null,
)

enum class Problem {
    UNREACHABLE, TOKEN_REJECTED, HTTP_ERROR, UNSUPPORTED, MALFORMED,
    NOT_CONFIGURED, NO_PHONE, NO_PHONE_APP,
}

fun UsageState.after(result: FetchResult): UsageState = when (result) {
    is FetchResult.Ok -> copy(loading = false, lastGood = result.payload, problem = null, detail = null)
    FetchResult.Unauthorized -> copy(loading = false, problem = Problem.TOKEN_REJECTED, detail = null)
    is FetchResult.Unreachable -> copy(loading = false, problem = Problem.UNREACHABLE, detail = result.reason)
    is FetchResult.HttpError -> copy(loading = false, problem = Problem.HTTP_ERROR, detail = "HTTP ${result.code}")
    // A reading in a format this build cannot read is dropped, not shown with a guessed meaning.
    is FetchResult.Unsupported -> copy(loading = false, lastGood = null, problem = Problem.UNSUPPORTED, detail = null)
    FetchResult.Malformed -> copy(loading = false, problem = Problem.MALFORMED, detail = null)
    FetchResult.NotConfigured -> copy(loading = false, problem = Problem.NOT_CONFIGURED, detail = null)
    FetchResult.NoPhone -> copy(loading = false, problem = Problem.NO_PHONE, detail = null)
    FetchResult.NoPhoneApp -> copy(loading = false, problem = Problem.NO_PHONE_APP, detail = null)
}

fun Problem.label(): String = when (this) {
    Problem.UNREACHABLE -> "unreachable"
    Problem.TOKEN_REJECTED -> "token rejected"
    Problem.HTTP_ERROR -> "collector error"
    Problem.UNSUPPORTED -> "update the app"
    Problem.MALFORMED -> "bad data"
    Problem.NOT_CONFIGURED -> "set up on phone"
    Problem.NO_PHONE -> "phone not connected"
    Problem.NO_PHONE_APP -> "install Mana on phone"
}

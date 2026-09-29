package io.github.claudeclip.watch

/** What the screen shows. A failed fetch never replaces the last good reading. */
data class UsageState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val lastGood: Payload? = null,
    val problem: Problem? = null,
)

enum class Problem { UNREACHABLE, TOKEN_REJECTED, HTTP_ERROR, UNSUPPORTED, MALFORMED }

fun UsageState.after(result: FetchResult): UsageState = when (result) {
    is FetchResult.Ok -> copy(loading = false, lastGood = result.payload, problem = null)
    FetchResult.Unauthorized -> copy(loading = false, problem = Problem.TOKEN_REJECTED)
    is FetchResult.Unreachable -> copy(loading = false, problem = Problem.UNREACHABLE)
    is FetchResult.HttpError -> copy(loading = false, problem = Problem.HTTP_ERROR)
    // A reading in a format this build cannot read is dropped, not shown with a guessed meaning.
    is FetchResult.Unsupported -> copy(loading = false, lastGood = null, problem = Problem.UNSUPPORTED)
    FetchResult.Malformed -> copy(loading = false, problem = Problem.MALFORMED)
}

fun Problem.label(): String = when (this) {
    Problem.UNREACHABLE -> "unreachable"
    Problem.TOKEN_REJECTED -> "token rejected"
    Problem.HTTP_ERROR -> "collector error"
    Problem.UNSUPPORTED -> "update the app"
    Problem.MALFORMED -> "bad data"
}

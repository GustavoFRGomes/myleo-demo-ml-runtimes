package org.ggomes.myleo.llm.litertlm.data

/**
 * Tries [backends] in order and returns the first that [attempt] succeeds for, as
 * `result to backend`. A later backend is a fallback when an earlier one throws —
 * e.g. the GPU backend needs an OpenCL driver, which not every device exposes.
 *
 * Deliberately free of SDK types so the ordering/fallback logic is unit-tested
 * directly (the engines themselves can't run without a model + native libs).
 * [onFailure] is invoked for each failed attempt, in order, for logging.
 *
 * @throws Exception the last attempt's exception when every backend fails.
 */
internal fun <B, T> firstWorkingBackend(
    backends: List<B>,
    onFailure: (B, Throwable) -> Unit = { _, _ -> },
    attempt: (B) -> T,
): Pair<T, B> {
    var lastError: Exception? = null
    for (backend in backends) {
        try {
            return attempt(backend) to backend
        } catch (e: Exception) {
            lastError = e
            onFailure(backend, e)
        }
    }
    throw lastError ?: IllegalStateException("no backends configured")
}

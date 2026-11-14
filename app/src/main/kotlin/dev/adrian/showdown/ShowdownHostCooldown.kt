package dev.adrian.showdown

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers hosts that recently could not be reached so sprite fallbacks skip them
 * instead of paying a full connect timeout per candidate. Showdown's own host is exempt
 * because every asset has a bundled or same-host fallback that depends on it.
 */
internal class ShowdownHostCooldown(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val unreachableUntil = ConcurrentHashMap<String, Long>()

    fun isCoolingDown(host: String): Boolean {
        if (host == EXEMPT_HOST) return false
        val until = unreachableUntil[host] ?: return false
        if (clock() < until) return true
        unreachableUntil.remove(host, until)
        return false
    }

    fun recordUnreachable(host: String) {
        if (host == EXEMPT_HOST) return
        unreachableUntil[host] = clock() + cooldownMillis
    }

    fun recordReachable(host: String) {
        unreachableUntil.remove(host)
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 5 * 60 * 1000L
        const val EXEMPT_HOST = "play.pokemonshowdown.com"
    }
}

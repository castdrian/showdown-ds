package dev.adrian.showdown

/**
 * Local-build-only sign-in for development servers that run without guest security.
 *
 * A launch extra names the user; the app then signs in with an empty token instead of contacting a
 * login server. It only applies to the local build, so it can never affect a real account.
 */
object ShowdownDevLogin {
    const val EXTRA_USERNAME = "dev_login_username"

    fun usernameFrom(extra: String?, localBuild: Boolean): String? =
        extra?.trim()?.takeIf { localBuild && it.isNotEmpty() }

    /** The dev user to sign in as, unless a real credential or saved session exists, which always wins. */
    fun signInUsername(devUsername: String?, hasCredentials: Boolean, hasSession: Boolean): String? =
        devUsername.takeUnless { hasCredentials || hasSession }

    fun renameCommand(username: String): String = ShowdownAuthentication.renameCommand(username, "")
}

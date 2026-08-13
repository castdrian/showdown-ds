package dev.adrian.showdown

import java.io.IOException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ShowdownLoginClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    initialCookies: Map<String, String> = emptyMap(),
    private val onCookiesChanged: (Map<String, String>) -> Unit = {}
) {
    private val cookies = linkedMapOf<String, String>()

    init {
        cookies.putAll(initialCookies)
    }

    fun hasSession(): Boolean = synchronized(cookies) { cookies.isNotEmpty() }

    fun upkeep(endpoint: ShowdownServerEndpoint, challenge: String, callback: (Result<ShowdownAuthentication.UpkeepResult?>) -> Unit) {
        val url = runCatching {
            endpoint.upkeepUrl.toHttpUrl().newBuilder()
                .addQueryParameter("challstr", challenge)
                .build()
                .toString()
        }.getOrElse {
            callback(Result.failure(it))
            return
        }
        httpClient.newCall(getRequest(url)).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    rememberCookies(it)
                    val payload = it.body.string()
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("Showdown session refresh failed (HTTP ${it.code}).")))
                    } else {
                        callback(Result.success(ShowdownAuthentication.upkeep(payload)))
                    }
                }
            }
        })
    }

    fun login(endpoint: ShowdownServerEndpoint, credentials: ShowdownCredentials, challenge: String, callback: (Result<String>) -> Unit) {
        post(
            endpoint.loginUrl,
            FormBody.Builder()
                .add("name", credentials.username)
                .add("pass", credentials.password)
                .add("challstr", challenge)
                .build(),
            "login",
            callback
        )
    }

    fun changePassword(
        endpoint: ShowdownServerEndpoint,
        oldPassword: String,
        password: String,
        confirmation: String,
        callback: (Result<Unit>) -> Unit
    ) {
        postAction(
            endpoint.changePasswordUrl,
            FormBody.Builder()
                .add("oldpassword", oldPassword)
                .add("password", password)
                .add("cpassword", confirmation)
                .build(),
            "password change",
            callback
        )
    }

    fun clearSession() {
        val changed = synchronized(cookies) {
            if (cookies.isEmpty()) false else {
                cookies.clear()
                true
            }
        }
        if (changed) onCookiesChanged(emptyMap())
    }

    fun register(
        endpoint: ShowdownServerEndpoint,
        credentials: ShowdownCredentials,
        confirmation: String,
        captcha: String,
        challenge: String,
        callback: (Result<String>) -> Unit
    ) {
        post(
            endpoint.registrationUrl,
            FormBody.Builder()
                .add("username", credentials.username)
                .add("password", credentials.password)
                .add("cpassword", confirmation)
                .add("captcha", captcha)
                .add("challstr", challenge)
                .build(),
            "registration",
            callback
        )
    }

    private fun post(url: String, body: FormBody, operationName: String, callback: (Result<String>) -> Unit) {
        val request = request(url, body)
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    rememberCookies(it)
                    val payload = it.body.string()
                    val assertion = if (it.isSuccessful && ShowdownAuthentication.actionSucceeded(payload) != false) {
                        ShowdownAuthentication.assertion(payload)
                    } else {
                        null
                    }
                    val message = ShowdownAuthentication.actionError(payload)
                        ?: "Showdown rejected the $operationName (HTTP ${it.code})."
                    callback(assertion?.let(Result.Companion::success) ?: Result.failure(IOException(message)))
                }
            }
        })
    }

    private fun postAction(url: String, body: FormBody, operationName: String, callback: (Result<Unit>) -> Unit) {
        httpClient.newCall(request(url, body)).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) = callback(Result.failure(error))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    rememberCookies(it)
                    val payload = it.body.string()
                    val success = it.isSuccessful && ShowdownAuthentication.actionSucceeded(payload) == true
                    val message = ShowdownAuthentication.actionError(payload)
                        ?: "Showdown rejected the $operationName (HTTP ${it.code})."
                    callback(if (success) Result.success(Unit) else Result.failure(IOException(message)))
                }
            }
        })
    }

    private fun request(url: String, body: FormBody): Request = Request.Builder().url(url).post(body).apply {
        addCookies(this)
    }.build()

    private fun getRequest(url: String): Request = Request.Builder().url(url).get().apply {
        addCookies(this)
    }.build()

    private fun addCookies(builder: Request.Builder) {
        synchronized(cookies) {
            if (cookies.isNotEmpty()) builder.addHeader("Cookie", cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
        }
    }

    private fun rememberCookies(response: okhttp3.Response) {
        var changed = false
        response.headers("Set-Cookie").forEach { header ->
            val value = header.substringBefore(';')
            val name = value.substringBefore('=', "").trim()
            val cookie = value.substringAfter('=', "").trim()
            val maxAge = header.split(';')
                .drop(1)
                .map { it.trim() }
                .firstOrNull { it.startsWith("max-age=", true) }
                ?.substringAfter('=')
                ?.toLongOrNull()
            if (name.isNotBlank()) synchronized(cookies) {
                if (cookie.isBlank() || maxAge != null && maxAge <= 0) {
                    if (cookies.remove(name) != null) changed = true
                } else if (cookies[name] != cookie) {
                    cookies[name] = cookie
                    changed = true
                }
            }
        }
        if (changed) onCookiesChanged(synchronized(cookies) { cookies.toMap() })
    }
}

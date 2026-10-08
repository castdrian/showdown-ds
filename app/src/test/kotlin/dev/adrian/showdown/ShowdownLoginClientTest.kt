package dev.adrian.showdown

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownLoginClientTest {
    @Test
    fun loginPostsTheOfficialChallengeAndReturnsTheSignedAssertion() {
        val request = AtomicReference<Request>()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        val savedCookies = AtomicReference<Map<String, String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", "sid=new-session; Path=/")
                .body("]{\"actionsuccess\":true,\"assertion\":\"signed-assertion\",\"curuser\":{\"loggedin\":true,\"username\":\"Adrian\",\"userid\":\"adrian\"}}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val loginClient = ShowdownLoginClient(client, mapOf("sid" to "old-session")) {
            savedCookies.set(it)
        }
        val endpoint = checkNotNull(
            ShowdownServerEndpoint.fromInput("wss://sim3.psim.us/showdown/websocket")
        )

        loginClient.login(endpoint, ShowdownCredentials("Adrian", "secret"), "4|challenge|suffix") {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("POST", request.get().method)
        assertEquals("https://play.pokemonshowdown.com/api/login", request.get().url.toString())
        assertEquals("sid=old-session", request.get().header("Cookie"))
        val body = Buffer()
        request.get().body!!.writeTo(body)
        val encoded = body.readUtf8()
        assertTrue(encoded.contains("name=Adrian"))
        assertTrue(encoded.contains("pass=secret"))
        assertTrue(encoded.contains("challstr=4%7Cchallenge%7Csuffix"))
        assertEquals("signed-assertion", result.get().getOrThrow())
        assertEquals(mapOf("sid" to "new-session"), savedCookies.get())
    }

    @Test
    fun loginSurfacesOfficialLoginServerRejection() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionerror\":\"Invalid password.\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val endpoint = checkNotNull(
            ShowdownServerEndpoint.fromInput("wss://sim3.psim.us/showdown/websocket")
        )

        ShowdownLoginClient(client).login(endpoint, ShowdownCredentials("Adrian", "wrong"), "4|challenge") {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("Invalid password.", result.get().exceptionOrNull()?.message)
    }

    @Test
    fun upkeepUsesThePersistedSessionCookieAndChallenge() {
        val request = AtomicReference<Request>()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<ShowdownAuthentication.UpkeepResult?>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"username\":\"Adrian\",\"assertion\":\"token\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client, mapOf("sid" to "session-token")).upkeep(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            "1|challenge"
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("https://test/api/upkeep?challstr=1%7Cchallenge", request.get().url.toString())
        assertEquals("sid=session-token", request.get().header("Cookie"))
        assertEquals("Adrian", result.get().getOrThrow()?.username)
        assertEquals("token", result.get().getOrThrow()?.assertion)
    }

    @Test
    fun upkeepRemovesExpiredSessionCookies() {
        val completed = CountDownLatch(1)
        val savedCookies = AtomicReference<Map<String, String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", "sid=; Max-Age=0; Path=/")
                .body("]{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client, mapOf("sid" to "expired")) { savedCookies.set(it) }.upkeep(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            "1|challenge"
        ) {
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals(emptyMap<String, String>(), savedCookies.get())
    }

    @Test
    fun registrationPostsTheOfficialFieldsAndReturnsTheAssertion() {
        val request = AtomicReference<Request>()
        val result = AtomicReference<Result<String>>()
        val completed = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionsuccess\":true,\"assertion\":\"token\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client).register(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            ShowdownCredentials("Adrian", "secret"),
            "secret",
            "Pikachu",
            "1|challenge",
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("token", result.get().getOrThrow())
        assertEquals("https://test/api/register", request.get().url.toString())
        val body = Buffer()
        request.get().body!!.writeTo(body)
        val encoded = body.readUtf8()
        assertTrue(encoded.contains("username=Adrian"))
        assertTrue(encoded.contains("password=secret"))
        assertTrue(encoded.contains("cpassword=secret"))
        assertTrue(encoded.contains("captcha=Pikachu"))
        assertTrue(encoded.contains("challstr=1%7Cchallenge"))
    }

    @Test
    fun registrationSurfacesLoginServerErrors() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionsuccess\":false,\"error\":\"That username is already taken.\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client).register(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            ShowdownCredentials("Adrian", "secret"),
            "secret",
            "Pikachu",
            "1|challenge",
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("That username is already taken.", result.get().exceptionOrNull()?.message)
    }

    @Test
    fun passwordChangeUsesTheSessionCookieAndOfficialFields() {
        val request = AtomicReference<Request>()
        val loginCompleted = CountDownLatch(1)
        val changeCompleted = CountDownLatch(1)
        val result = AtomicReference<Result<Unit>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            if (chain.request().url.encodedPath.endsWith("/login")) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Set-Cookie", "sid=session-token; Path=/")
                    .body("]{\"actionsuccess\":true,\"assertion\":\"token\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("]{\"actionsuccess\":true}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }.build()
        val loginClient = ShowdownLoginClient(client)
        val endpoint = ShowdownServerEndpoint(
            "Test",
            "ws://test",
            "https://test/api/login",
            "https://test/api/register",
            "https://test/api/changepassword"
        )

        loginClient.login(endpoint, ShowdownCredentials("Adrian", "secret"), "1|challenge") {
            loginCompleted.countDown()
        }
        assertTrue(loginCompleted.await(2, TimeUnit.SECONDS))
        loginClient.changePassword(endpoint, "secret", "newsecret", "newsecret") {
            result.set(it)
            changeCompleted.countDown()
        }

        assertTrue(changeCompleted.await(2, TimeUnit.SECONDS))
        assertTrue(result.get().isSuccess)
        assertEquals("https://test/api/changepassword", request.get().url.toString())
        assertEquals("sid=session-token", request.get().header("Cookie"))
        val body = Buffer()
        request.get().body!!.writeTo(body)
        val encoded = body.readUtf8()
        assertTrue(encoded.contains("oldpassword=secret"))
        assertTrue(encoded.contains("password=newsecret"))
        assertTrue(encoded.contains("cpassword=newsecret"))
    }

    @Test
    fun passwordChangeSurfacesActionErrors() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<Unit>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("]{\"actionsuccess\":false,\"actionerror\":\"Your old password was incorrect.\"}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        ShowdownLoginClient(client).changePassword(
            ShowdownServerEndpoint("Test", "ws://test", "https://test/api/login"),
            "old",
            "new",
            "new"
        ) {
            result.set(it)
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("Your old password was incorrect.", result.get().exceptionOrNull()?.message)
    }
}

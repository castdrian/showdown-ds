package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScavioAnimatedSpriteIndexTest {
    @Test
    fun buildsEncodedTagQueries() {
        assertEquals(
            "https://scaviogifs.tumblr.com/api/read/json?tagged=Iron+Boulder",
            ScavioAnimatedSpriteIndex.apiUrl("Iron Boulder")
        )
    }

    @Test
    fun keepsOnlyOriginalAnimatedMediaUrls() {
        val body = """
            https:\/\/64.media.tumblr.com/a/post/s640x960/one.gif
            https:\/\/64.media.tumblr.com/a/post/s250x400/one.gif
            https:\/\/64.media.tumblr.com/b/post/s1280x1920/two.gif
        """.trimIndent()

        assertEquals(
            listOf(
                "https://64.media.tumblr.com/a/post/s640x960/one.gif",
                "https://64.media.tumblr.com/b/post/s1280x1920/two.gif"
            ),
            ScavioAnimatedSpriteIndex.candidates(body)
        )
        assertTrue(ScavioAnimatedSpriteIndex.candidates(body).all { it.endsWith(".gif") })
    }
}

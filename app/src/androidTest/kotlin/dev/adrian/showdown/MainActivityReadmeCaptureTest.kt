package dev.adrian.showdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityReadmeCaptureTest {
    @Test
    fun capturesLiveBattleAndMatchingMoveAndPartyDisplays() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val livePreferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousMaintainConnection = livePreferences.getBoolean("maintain_connection", false)
        val outputDirectory = checkNotNull(targetContext.getExternalFilesDir("readme-capture"))
        assertTrue(outputDirectory.mkdirs() || outputDirectory.isDirectory)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            livePreferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                val session = privateField(activity, "session") as BattleSession
                session.setLocalUsername("ADRIAN")
                session.applyProtocolPacket(liveBattleTranscript())
                session.setBattleParticipant(true)
                session.setLiveBattleActive(true)
                assertTrue(session.isLiveBattleActive())
                assertFalse(session.isReplayMode())
                assertTrue(session.isBattleParticipant())
                assertEquals("p1", session.battlePlayerSlot())
                assertEquals(BattleSession.Panel.MOVES, session.panel)
            }

            val spriteDeadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(30)
            var bothHighResolutionAnimatedSpritesLoaded = false
            while (!bothHighResolutionAnimatedSpritesLoaded && SystemClock.elapsedRealtime() < spriteDeadline) {
                activeScenario.onActivity { activity ->
                    val battleScene = privateField(activity, "battleScene") as BattleSceneView
                    val playerSprite = privateField(battleScene, "playerSprite") as? ShowdownSpriteCache.SpriteAsset
                    val opponentSprite = privateField(battleScene, "opponentSprite") as? ShowdownSpriteCache.SpriteAsset
                    bothHighResolutionAnimatedSpritesLoaded =
                        hasHighResolutionAnimatedFrame(playerSprite) && hasHighResolutionAnimatedFrame(opponentSprite)
                }
                if (!bothHighResolutionAnimatedSpritesLoaded) SystemClock.sleep(250L)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            activeScenario.onActivity { activity ->
                val session = privateField(activity, "session") as BattleSession
                val battleScene = privateField(activity, "battleScene") as BattleSceneView
                val commandDeck = checkNotNull(privateField(activity, "commandDeck") as? CommandDeckView)
                assertEquals(1920, battleScene.width)
                assertEquals(1080, battleScene.height)
                assertEquals(1240, commandDeck.width)
                assertEquals(1080, commandDeck.height)
                val requestedPlayerSprite = privateField(battleScene, "requestedPlayerSprite") as? BattleSpriteRequest
                val requestedOpponentSprite = privateField(battleScene, "requestedOpponentSprite") as? BattleSpriteRequest
                assertNotNull("The live scene did not request a player-side sprite", requestedPlayerSprite)
                assertNotNull("The live scene did not request an opponent-side sprite", requestedOpponentSprite)
                assertTrue(requestedPlayerSprite?.species.equals("Cinderace", true))
                assertTrue(requestedOpponentSprite?.species.equals("Dragapult", true))
                val playerSprite = checkNotNull(privateField(battleScene, "playerSprite") as? ShowdownSpriteCache.SpriteAsset)
                val opponentSprite = checkNotNull(privateField(battleScene, "opponentSprite") as? ShowdownSpriteCache.SpriteAsset)
                assertTrue("The player-side animated HD back sprite did not load within 30 seconds", playerSprite.isAnimated)
                assertTrue("The opponent-side animated HD front sprite did not load within 30 seconds", opponentSprite.isAnimated)
                assertHighResolutionSprite(playerSprite, "player-side back")
                assertHighResolutionSprite(opponentSprite, "opponent-side front")
                saveView(battleScene, File(outputDirectory, "battle-upper.png"))
                saveView(commandDeck, File(outputDirectory, "battle-lower.png"))
                session.selectPanel(BattleSession.Panel.TEAM)
            }

            SystemClock.sleep(300L)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            activeScenario.onActivity { activity ->
                val session = privateField(activity, "session") as BattleSession
                val battleScene = privateField(activity, "battleScene") as BattleSceneView
                val commandDeck = checkNotNull(privateField(activity, "commandDeck") as? CommandDeckView)
                assertEquals(BattleSession.Panel.TEAM, session.panel)
                saveView(battleScene, File(outputDirectory, "party-upper.png"))
                saveView(commandDeck, File(outputDirectory, "party-lower.png"))
            }
            publishCaptures(targetContext, outputDirectory)
        } finally {
            scenario?.close()
            livePreferences.edit().putBoolean("maintain_connection", previousMaintainConnection).commit()
        }
    }

    private fun saveView(view: View, destination: File) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            FileOutputStream(destination).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun publishCaptures(context: Context, outputDirectory: File) {
        val resolver = context.contentResolver
        val captures = checkNotNull(outputDirectory.listFiles()).filter { it.isFile && it.extension == "png" }
        assertEquals(4, captures.size)
        captures.forEach { capture ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, capture.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/showdown-readme-capture")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            checkNotNull(resolver.openOutputStream(uri)).use { output ->
                capture.inputStream().use { input -> input.copyTo(output) }
            }
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            assertEquals(1, resolver.update(uri, publishedValues, null, null))
        }
    }

    private fun privateField(target: Any, name: String): Any? = target.javaClass
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

    private fun assertHighResolutionSprite(sprite: ShowdownSpriteCache.SpriteAsset, label: String) {
        val largestDimension = largestSpriteDimension(sprite)
        assertTrue("The $label sprite resolved at only ${largestDimension} pixels", largestDimension >= 180)
    }

    private fun hasHighResolutionAnimatedFrame(sprite: ShowdownSpriteCache.SpriteAsset?): Boolean {
        if (sprite?.isAnimated != true) return false
        return largestSpriteDimension(sprite) >= 180
    }

    private fun largestSpriteDimension(sprite: ShowdownSpriteCache.SpriteAsset): Int {
        val width = privateField(sprite, "width") as Int
        val height = privateField(sprite, "height") as Int
        return maxOf(width, height)
    }

    private fun liveBattleTranscript() = listOf(
        "|init|battle",
        "|player|p1|ADRIAN|1|",
        "|player|p2|MIRA|2|",
        "|gametype|singles",
        "|gen|9",
        "|tier|[Gen 9] OU",
        "|teamsize|p1|6",
        "|teamsize|p2|6",
        "|poke|p1|Cinderace, L82, M",
        "|poke|p1|Corviknight, L82",
        "|poke|p1|Rotom-Wash, L82",
        "|poke|p1|Rillaboom, L82",
        "|poke|p1|Sylveon, L82",
        "|poke|p1|Tyranitar, L82",
        "|poke|p2|Dragapult, L82",
        "|poke|p2|Gholdengo, L82",
        "|poke|p2|Great Tusk, L82",
        "|poke|p2|Kingambit, L82",
        "|poke|p2|Ogerpon-Wellspring, L82",
        "|poke|p2|Iron Valiant, L82",
        "|switch|p1a: Cinderace|Cinderace, L82, M|235/235",
        "|switch|p2a: Dragapult|Dragapult, L82|251/251",
        "|request|${liveMoveRequest()}"
    )

    private fun liveMoveRequest() = """
        {"rqid":2,"active":[{"moves":[
          {"move":"Pyro Ball","id":"pyroball","pp":7,"maxpp":8,"basePower":120,"accuracy":90,"target":"normal","type":"Fire","category":"Physical"},
          {"move":"High Jump Kick","id":"highjumpkick","pp":16,"maxpp":16,"basePower":130,"accuracy":90,"target":"normal","type":"Fighting","category":"Physical"},
          {"move":"U-turn","id":"uturn","pp":32,"maxpp":32,"basePower":70,"accuracy":100,"target":"normal","type":"Bug","category":"Physical"},
          {"move":"Sucker Punch","id":"suckerpunch","pp":8,"maxpp":8,"basePower":70,"accuracy":100,"target":"normal","type":"Dark","category":"Physical"}
        ]}],"side":{"id":"p1","name":"ADRIAN","pokemon":[
          {"ident":"p1: Cinderace","details":"Cinderace, L82, M","condition":"235/235","active":true,"moves":["pyroball","highjumpkick","uturn","suckerpunch"]},
          {"ident":"p1: Corviknight","details":"Corviknight, L82","condition":"400/400","active":false,"moves":["bravebird","roost","uturn","defog"]},
          {"ident":"p1: Rotom-Wash","details":"Rotom-Wash, L82","condition":"251/251","active":false,"moves":["hydropump","voltswitch","willowisp","painsplit"]},
          {"ident":"p1: Rillaboom","details":"Rillaboom, L82","condition":"320/320","active":false,"moves":["grassyglide","woodhammer","uturn","knockoff"]},
          {"ident":"p1: Sylveon","details":"Sylveon, L82","condition":"300/300","active":false,"moves":["hypervoice","mysticalfire","wish","protect"]},
          {"ident":"p1: Tyranitar","details":"Tyranitar, L82","condition":"404/404","active":false,"moves":["stoneedge","crunch","earthquake","stealthrock"]}
        ]}}
    """.trimIndent().replace("\n", "")
}

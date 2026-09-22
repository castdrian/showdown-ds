package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestContractTest {
    @Test
    fun launcherIsClassifiedAsAGame() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:appCategory=\"game\""))
    }
}

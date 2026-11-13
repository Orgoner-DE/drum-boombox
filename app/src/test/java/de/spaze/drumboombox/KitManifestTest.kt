package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KitManifestTest {
    @Test
    fun manifestListsThreeKitsOfSixteen() {
        val root = File("src/main/assets/samples")
        val alt = File("../main/assets/samples")
        val dir = when {
            root.resolve("manifest.json").isFile -> root
            alt.resolve("manifest.json").isFile -> alt
            else -> File("/workspace/drum-boombox-android/app/src/main/assets/samples")
        }
        val text = File(dir, "manifest.json").readText()
        assertTrue(text.contains("\"street\""))
        assertTrue(text.contains("\"steel\""))
        assertTrue(text.contains("\"room\""))
        for (kit in listOf("street", "steel", "room")) {
            for (key in KitCatalog.DEFAULT_KEYS) {
                val f = File(dir, "$kit/$key.wav")
                assertTrue("missing ${f.path}", f.isFile && f.length() > 100)
                val bytes = f.readBytes()
                val decoded = WavIO.decode(bytes)
                assertTrue("$kit/$key $decoded", decoded is WavResult.Ok)
                val ok = decoded as WavResult.Ok
                assertEquals(44100, ok.sampleRate)
                assertTrue(ok.left.isNotEmpty())
            }
        }
    }

    @Test
    fun realKitsAreListedAndDecodable() {
        val root = File("src/main/assets/samples")
        val alt = File("../main/assets/samples")
        val dir = when {
            root.resolve("manifest.json").isFile -> root
            alt.resolve("manifest.json").isFile -> alt
            else -> File("/workspace/drum-boombox-android/app/src/main/assets/samples")
        }
        val text = File(dir, "manifest.json").readText()
        for (kit in KitCatalog.REAL_KIT_IDS) {
            assertTrue(KitCatalog.KIT_IDS.contains(kit))
            assertTrue(text.contains("\"$kit\""))
            for (key in KitCatalog.DEFAULT_KEYS) {
                val f = File(dir, "$kit/$key.wav")
                assertTrue("missing ${f.path}", f.isFile && f.length() > 100)
                val decoded = WavIO.decode(f.readBytes())
                assertTrue("$kit/$key $decoded", decoded is WavResult.Ok)
                assertEquals(44100, (decoded as WavResult.Ok).sampleRate)
            }
        }
        assertEquals(KitCatalog.KIT_IDS.size, KitCatalog.KIT_LABELS.size)
    }
}

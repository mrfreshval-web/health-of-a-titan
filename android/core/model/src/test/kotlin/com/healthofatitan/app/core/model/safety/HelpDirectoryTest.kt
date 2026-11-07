package com.healthofatitan.app.core.model.safety

import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Y6: help numbers by the phone's country, and the built-in table equals content/safety/resources.yaml. */
class HelpDirectoryTest {

    @Suppress("UNCHECKED_CAST")
    private val yaml: Map<String, Any?> by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content").isDirectory) dir = dir.parentFile
        val file = File(requireNotNull(dir) { "content/ not found" }, "content/safety/resources.yaml")
        Yaml(SafeConstructor(LoaderOptions())).load<Any?>(file.readText()) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.sub(key: String): Map<String, Any?>? = this[key] as Map<String, Any?>?

    @Test
    fun `built-in table equals resources yaml`() {
        val countries = yaml.sub("countries")!!
        assertEquals(countries.keys, HelpDirectory.byCountry.keys)
        countries.forEach { (iso, raw) ->
            @Suppress("UNCHECKED_CAST")
            val c = raw as Map<String, Any?>
            val lines = HelpDirectory.byCountry.getValue(iso)
            assertEquals(c.sub("emergency")?.get("number"), lines.emergency, iso)
            val crisis = c.sub("crisisLine")
            assertEquals(crisis?.get("number"), lines.crisisLine, iso)
            assertEquals(crisis?.get("shown"), lines.crisisShown, iso)
            assertEquals(crisis?.get("sms"), lines.crisisSms, iso)
            assertEquals(crisis?.get("chatUrl"), lines.crisisChatUrl, iso)
        }
        val fallback = yaml.sub("fallback")!!
        assertEquals(fallback.sub("emergency")?.get("number"), HelpDirectory.FALLBACK.emergency)
        assertEquals(fallback["directoryUrl"], HelpDirectory.DIRECTORY_URL)
    }

    @Test
    fun `us gets 988 and 911, russia gets 112 and the trust line`() {
        assertEquals("911", HelpDirectory.forCountry("us").emergency)
        assertEquals("988", HelpDirectory.forCountry("US").crisisLine)
        assertEquals("112", HelpDirectory.forCountry("ru").emergency)
        assertEquals("88002000122", HelpDirectory.forCountry("RU").crisisLine)
        assertEquals("988 · 911", HelpDirectory.US.caption)
        assertEquals("8-800-2000-122 · 112", HelpDirectory.RU.caption)
    }

    @Test
    fun `network wins over sim and region, unknown country falls back to 112`() {
        assertEquals(HelpDirectory.US, HelpDirectory.resolve("us", "ru", "RU"))
        assertEquals(HelpDirectory.RU, HelpDirectory.resolve("", "ru", "US"))
        assertEquals(HelpDirectory.RU, HelpDirectory.resolve(null, null, "RU"))
        // A Russian SIM roaming in Germany: 112 there, not the Russian 8-800 line.
        assertEquals(HelpDirectory.FALLBACK, HelpDirectory.resolve("de", "ru", "RU"))
        assertEquals(HelpDirectory.FALLBACK, HelpDirectory.resolve(null, "", null))
        assertEquals("112", HelpDirectory.FALLBACK.emergency)
        assertNull(HelpDirectory.FALLBACK.crisisLine)
        assertEquals("112", HelpDirectory.FALLBACK.caption)
    }
}

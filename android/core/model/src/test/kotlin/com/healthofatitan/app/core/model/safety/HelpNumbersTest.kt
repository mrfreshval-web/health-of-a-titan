package com.healthofatitan.app.core.model.safety

import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import kotlin.test.assertEquals

/** 112 and 911 everywhere, in this order; the built-in list equals content/safety/resources.yaml. */
class HelpNumbersTest {

    @Suppress("UNCHECKED_CAST")
    private val yaml: Map<String, Any?> by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content").isDirectory) dir = dir.parentFile
        val file = File(requireNotNull(dir) { "content/ not found" }, "content/safety/resources.yaml")
        Yaml(SafeConstructor(LoaderOptions())).load<Any?>(file.readText()) as Map<String, Any?>
    }

    @Test
    fun `built-in numbers equal resources yaml`() {
        @Suppress("UNCHECKED_CAST")
        val numbers = (yaml["numbers"] as List<Map<String, Any?>>).map { it["number"] }
        assertEquals(numbers, HelpNumbers.ALL)
        assertEquals(setOf("version", "reviewedBy", "status", "numbers"), yaml.keys)
    }

    @Test
    fun `112 first, 911 second, nothing else`() {
        assertEquals(listOf("112", "911"), HelpNumbers.ALL)
        assertEquals("112 · 911", HelpNumbers.CAPTION)
        assertEquals("112 или 911", HelpNumbers.IN_TEXT)
    }
}

package com.healthofatitan.app.domain.training

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/** The YAML sources of content/training and content/profile, as the experts wrote them. */
object Content {
    val dir: File = File(System.getProperty("hot.content.dir") ?: "../../../content").absoluteFile

    @Suppress("UNCHECKED_CAST")
    fun yaml(path: String): Map<String, Any?> =
        Yaml(SafeConstructor(LoaderOptions())).load<Any?>(File(dir, path).readText()) as Map<String, Any?>

    val exercises by lazy { yaml("training/exercises.yaml") }
    val templates by lazy { yaml("training/templates.yaml") }
    val rules by lazy { yaml("training/rules.yaml") }
    val poses by lazy { yaml("training/poses.yaml") }

    val library: TrainingLibrary by lazy { TrainingLibrary.fromTrees(exercises, templates, rules, poses) }

    /** The same tree as the JSON that contentCompile writes into the APK. */
    fun json(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is Number -> sb.append(v.toDouble().toString())
            is Map<*, *> -> {
                sb.append('{')
                v.entries.forEachIndexed { i, (k, x) ->
                    if (i > 0) sb.append(',')
                    str(sb, k.toString())
                    sb.append(':')
                    write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, x ->
                    if (i > 0) sb.append(',')
                    write(sb, x)
                }
                sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}

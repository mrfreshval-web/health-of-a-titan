package com.healthofatitan.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.security.MessageDigest

/**
 * `contentCompile` (A14 §2, ADR-05): expert content in `content/` (YAML) → JSON in APK assets
 * + `content/manifest.json` with versions and hashes. YAML is parsed at build time only, the
 * APK carries no YAML library.
 *
 * Validation (slice 0): every file must be a YAML mapping with the keys listed in
 * `content/_schema/common.schema.json` → `required` (`version`, `reviewedBy`). Full JSON
 * Schema validation per content type comes with the first real content (slice 2).
 * Any broken file fails the build with the file name and the reason.
 */
@CacheableTask
abstract class ContentCompileTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = contentDir.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val target = File(out, "content").apply { mkdirs() }

        val required = readRequiredKeys(File(src, "_schema/common.schema.json"))
        val errors = mutableListOf<String>()
        val manifest = mutableListOf<Map<String, Any?>>()

        src.walkTopDown()
            .onEnter { !it.name.startsWith("_") && !it.name.startsWith(".") }
            .filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }
            .sortedBy { it.relativeTo(src).invariantSeparatorsPath }
            .forEach { file ->
                val rel = file.relativeTo(src).invariantSeparatorsPath
                val parsed = try {
                    Yaml(SafeConstructor(LoaderOptions())).load<Any?>(file.readText())
                } catch (e: YAMLException) {
                    errors += "content/$rel: invalid YAML: ${e.message?.lines()?.take(4)?.joinToString(" ")}"
                    return@forEach
                }
                if (parsed !is Map<*, *>) {
                    errors += "content/$rel: top level must be a mapping"
                    return@forEach
                }
                val missing = required.filter { it !in parsed.keys }
                if (missing.isNotEmpty()) {
                    errors += "content/$rel: missing required key(s) ${missing.joinToString()}"
                    return@forEach
                }
                val json = Json.write(parsed)
                val dest = File(target, rel.substringBeforeLast('.') + ".json")
                dest.parentFile.mkdirs()
                dest.writeText(json)
                manifest += mapOf(
                    "path" to rel,
                    "version" to parsed["version"],
                    "sha256" to sha256(json),
                )
            }

        if (errors.isNotEmpty()) {
            throw GradleException("contentCompile failed:\n" + errors.joinToString("\n") { "  - $it" })
        }
        File(target, "manifest.json").writeText(Json.write(mapOf("files" to manifest)))
        logger.lifecycle("contentCompile: ${manifest.size} file(s) → assets/content")
    }

    private fun readRequiredKeys(schema: File): List<String> {
        if (!schema.isFile) throw GradleException("contentCompile: missing ${schema.path}")
        val parsed = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(schema.readText()) // JSON ⊂ YAML
        val req = (parsed as? Map<*, *>)?.get("required") as? List<*>
            ?: throw GradleException("contentCompile: ${schema.name} has no 'required' array")
        return req.map { it.toString() }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Minimal JSON writer for YAML-loaded values (maps, lists, scalars). */
internal object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is Number -> {
                val d = v.toDouble()
                if (d.isNaN() || d.isInfinite()) throw GradleException("contentCompile: non-finite number")
                sb.append(v.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                v.entries.forEachIndexed { i, (k, value) ->
                    if (i > 0) sb.append(',')
                    string(sb, k.toString()); sb.append(':'); append(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, item -> if (i > 0) sb.append(','); append(sb, item) }
                sb.append(']')
            }
            is java.util.Date -> string(sb, v.toInstant().toString())
            else -> string(sb, v.toString())
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}

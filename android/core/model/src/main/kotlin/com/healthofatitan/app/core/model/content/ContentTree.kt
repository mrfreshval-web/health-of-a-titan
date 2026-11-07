package com.healthofatitan.app.core.model.content

/**
 * Content as a plain tree (A14 §2, ADR-05): `contentCompile` turns the YAML of `content/` into JSON
 * assets; on the phone [ContentJson] reads them into maps, lists and scalars, and the JVM tests
 * load the same YAML into the same shape. Parsers of content types read only this tree, so one
 * parser serves the APK and the tests and no YAML library ships in the APK.
 */
typealias Tree = Any?

/** Small strict JSON reader for the compiled content (objects keep their key order). */
object ContentJson {

    fun parse(text: String): Tree {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "trailing characters at ${p.i}" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Tree {
            require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c' at $i")
            }
        }

        private fun lit(word: String, v: Tree): Tree {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Tree> {
            val m = LinkedHashMap<String, Tree>()
            i++
            ws()
            if (s[i] == '}') return m.also { i++ }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i] == ':') { "':' expected at $i" }
                i++
                ws()
                m[k] = value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    '}' -> return m.also { i++ }
                    else -> error("',' or '}' expected at $i")
                }
            }
        }

        private fun arr(): List<Tree> {
            val l = ArrayList<Tree>()
            i++
            ws()
            if (s[i] == ']') return l.also { i++ }
            while (true) {
                ws()
                l += value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    ']' -> return l.also { i++ }
                    else -> error("',' or ']' expected at $i")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "string expected at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Number {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}

/** Tree accessors: a missing or mistyped value reads as empty, never throws. */
fun Tree.obj(): Map<String, Tree> = (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

fun Tree.list(): List<Tree> = (this as? List<*>) ?: emptyList()

fun Tree.str(): String? = when (this) {
    null -> null
    is String -> this
    is Map<*, *>, is List<*> -> null
    else -> toString()
}

fun Tree.num(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

fun Tree.bool(): Boolean = this == true || this == "true"

fun Tree.strings(): List<String> = list().mapNotNull { it.str() }

fun Map<String, Tree>.str(key: String): String? = this[key].str()

fun Map<String, Tree>.strings(key: String): List<String> = this[key].strings()

fun Map<String, Tree>.obj(key: String): Map<String, Tree> = this[key].obj()

fun Map<String, Tree>.list(key: String): List<Tree> = this[key].list()

package com.healthofatitan.app.text

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.feature.checkin.CheckinTexts
import com.healthofatitan.app.feature.today.DailyTexts
import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The neutral form of a text never speaks of the person in a gender (A61 §4.1, D-055 п. 4), by
 * the dictionary of Agent 16 in `content/text/gender_stopforms.yaml` (review 2026-11-07): in `n`
 * both the masculine and the feminine forms are banned. This slice checks the three [G] examples
 * of A61 §4.2; all `*Texts` / `*Copy` follow with the T-edits.
 */
class GenderNeutralTextsTest {

    /** The dictionary: [pairs] are «masculine, feminine»; [phrases] are banned in `n` as they are. */
    private class StopForms(val phrases: List<String>, val pairs: List<Pair<String, String>>) {
        val masculine = pairs.map { it.first }
        val feminine = pairs.map { it.second }

        /** Everything the neutral form must not contain: the phrases and both forms of every pair. */
        fun neutralHits(text: String): List<String> = phrases.filter { text.contains(it, ignoreCase = true) } + formHits(text, masculine + feminine)

        /** The forms of [forms] in [text]: whole words, Cyrillic-safe (a plain `\b` is ASCII-only on the JDK). */
        fun formHits(text: String, forms: List<String>): List<String> = forms.filter { form ->
            if (form in PHRASE_ONLY) {
                // «один» / «одна» only as «ты один» / «ты одна»: as words they would hit «один шаг»
                word("ты $form").containsMatchIn(text)
            } else {
                word(form).containsMatchIn(text)
            }
        }

        private fun word(form: String) = Regex("(?U)(?<!\\p{L})(${Regex.escape(form)})(?!\\p{L})", RegexOption.IGNORE_CASE)

        companion object {
            val PHRASE_ONLY = setOf("один", "одна")
        }
    }

    private val dictionary: StopForms by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content").isDirectory) dir = dir.parentFile
        val file = File(requireNotNull(dir) { "content/ not found" }, "content/text/gender_stopforms.yaml")
        val yaml = Yaml(SafeConstructor(LoaderOptions())).load<Map<String, Any?>>(file.readText())
        StopForms(
            phrases = (yaml["phrases"] as List<*>).map(Any?::toString),
            pairs = (yaml["forms"] as List<*>).map { pair -> (pair as List<*>).let { it[0].toString() to it[1].toString() } },
        )
    }

    /** The three examples of A61 §4.2 in one gender. */
    private fun examples(address: AddressForm): Map<String, String> =
        mapOf(
            "CheckinTexts.STACK_HINT" to CheckinTexts.STACK_HINT.of(address),
            "CheckinTexts.discoveryLine(SENSEI)" to CheckinTexts.discoveryLine(MentorPersona.SENSEI, address),
        ) + DailyTexts.trainingOptions(address).associate { (value, label) -> "DailyTexts.trainingOptions.$value" to label }

    @Test
    fun `the dictionary bans both forms and keeps whole words`() {
        val d = dictionary
        assertTrue(d.pairs.isNotEmpty() && d.phrases.isNotEmpty())
        assertTrue(d.neutralHits("Ты устала — бывает.").isNotEmpty(), "a feminine form in n is caught too")
        assertTrue(d.neutralHits("Ты устал — бывает.").isNotEmpty())
        assertTrue(d.neutralHits("Готово. Приготовься к вечеру.").isEmpty(), "«готов» is a whole word only")
        assertTrue(d.neutralHits("Один шаг за раз.").isEmpty(), "«один» counts only as «ты один»")
        assertTrue(d.neutralHits("Ты одна не справишься?").isNotEmpty())
        assertTrue(d.neutralHits("Это тебе самому решать.").isNotEmpty())
    }

    @Test
    fun `the neutral forms of the three examples have no gender`() {
        val d = dictionary
        examples(AddressForm.NEUTRAL).forEach { (key, text) -> assertEquals(emptyList(), d.neutralHits(text), "$key: «$text»") }
        assertEquals("Это твоя закономерность. Связь, не причина.", CheckinTexts.discoveryLine(MentorPersona.SENSEI))
    }

    @Test
    fun `the masculine and feminine forms do not mix`() {
        val d = dictionary
        examples(AddressForm.MASCULINE).forEach { (key, text) -> assertEquals(emptyList(), d.formHits(text, d.feminine), "$key m: «$text»") }
        examples(AddressForm.FEMININE).forEach { (key, text) -> assertEquals(emptyList(), d.formHits(text, d.masculine), "$key f: «$text»") }
    }
}

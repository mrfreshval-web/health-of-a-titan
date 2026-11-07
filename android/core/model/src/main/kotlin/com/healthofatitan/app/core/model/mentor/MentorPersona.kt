package com.healthofatitan.app.core.model.mentor

/**
 * The mentor's character (D-030, A38 §1): one mentor, one memory, three manners. The character
 * changes the manner of speech only — never the day plan, the A09 rules or safety (A38 §6).
 *
 * @property id stable value stored on the device (A38 §10.2 `mentor_persona`)
 * @property nameForm how the mentor calls himself (A38 §4, proposal of Agent 15)
 */
enum class MentorPersona(val id: String, val nameForm: String) {
    SENSEI("sensei", "Сенсей Винс"),
    BRO("bro", "Винс"),
    BUTLER("butler", "Винсент"),
    ;

    companion object {
        /** «Пропустить» in the onboarding sets Сенсей (A38 §9, п. 1). */
        val DEFAULT = SENSEI

        fun fromId(id: String?): MentorPersona? = entries.firstOrNull { it.id == id }
    }
}

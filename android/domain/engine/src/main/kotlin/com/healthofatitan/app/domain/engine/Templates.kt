package com.healthofatitan.app.domain.engine

/** Places of A19 §3.1 Q2. Slice 2 has no place chip yet, so strength goes to the gym and cardio outside. */
enum class Place { HOME, HOME_DUMBBELLS, GYM, OUTDOOR, HOTEL }

/** A19 §2.1 templates and the §3.2 matrix «type × place × time». */
object Templates {

    private val names = mapOf(
        "T01" to "Тело-база", "T02" to "Тело-плюс", "T03" to "Гантели-база", "T04" to "Гантели-сплит",
        "T05" to "Зал A", "T06" to "Зал B", "T07" to "Мини-доза", "T08" to "Быстрая ходьба",
        "T09" to "Ровный кардио", "T10" to "Кардио в дороге", "T11" to "Интервалы 4×4",
        "T12" to "Короткие интервалы", "T13" to "Интервалы дома", "T14" to "Мобильность",
        "T15" to "Восстановление", "T16" to "Отель-силовая",
    )

    fun name(id: String): String = names[id] ?: id

    /** Durations offered by the ⏱ chip (A19 §3.1 Q1); the matrix rounds down to one of them. */
    val DURATIONS = listOf(10, 20, 30, 45, 60)

    fun snap(minutes: Int): Int = DURATIONS.lastOrNull { it <= minutes } ?: DURATIONS.first()

    fun defaultPlace(type: TrainingType): Place = if (type == TrainingType.STRENGTH) Place.GYM else Place.OUTDOOR

    /**
     * A19 §3.2. «Новичок» never gets T11–T13: [intermediate] = false must not ask for intervals
     * (the selector turns them into Zone 2 before). Returns null for rest.
     */
    fun pick(type: TrainingType, place: Place, minutes: Int, intermediate: Boolean): String? = when (type) {
        TrainingType.STRENGTH -> when (place) {
            Place.HOME -> when {
                minutes <= 10 -> "T07"
                minutes <= 20 -> "T01"
                else -> if (intermediate) "T02" else "T01"
            }
            Place.HOME_DUMBBELLS -> when {
                minutes <= 10 -> "T07"
                minutes <= 20 -> "T01"
                minutes <= 30 -> "T03"
                else -> if (intermediate) "T04" else "T03"
            }
            Place.GYM -> when {
                minutes <= 10 -> "T07"
                minutes <= 20 -> "T01"
                minutes <= 45 -> "T05"
                else -> if (intermediate) "T06" else "T05"
            }
            Place.OUTDOOR -> if (minutes <= 20) "T07" else "T01"
            Place.HOTEL -> if (minutes <= 10) "T07" else "T16"
        }
        TrainingType.ZONE2 -> when (place) {
            Place.GYM -> "T09"
            Place.HOTEL -> "T10"
            Place.OUTDOOR -> if (minutes <= 30) "T08" else "T09"
            Place.HOME, Place.HOME_DUMBBELLS -> "T08"
        }
        TrainingType.INTERVALS -> when (place) {
            Place.GYM, Place.OUTDOOR -> if (minutes < 40) "T12" else "T11"
            Place.HOME, Place.HOME_DUMBBELLS, Place.HOTEL -> "T13"
        }
        TrainingType.MOBILITY -> if (minutes <= 10) "T14" else "T15"
        TrainingType.WALK -> "T15"
        TrainingType.REST -> null
    }
}

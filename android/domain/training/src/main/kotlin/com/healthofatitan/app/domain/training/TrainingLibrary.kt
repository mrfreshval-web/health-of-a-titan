package com.healthofatitan.app.domain.training

/**
 * The training library: content/training/exercises.yaml, templates.yaml, rules.yaml and the
 * TitanPose keyframes of poses.yaml (A40 §4–§6). Built from the content tree; broken entries are
 * skipped, never guessed. [reviewed] says whether the experts signed the files (`reviewedBy`).
 */
class TrainingLibrary(
    val exercises: Map<String, Exercise>,
    val templates: Map<String, Template>,
    val rules: TrainingRules,
    val poses: PoseLibrary = PoseLibrary.EMPTY,
    val reviewed: Boolean = false,
) {
    fun exercise(id: String): Exercise? = exercises[id]?.takeIf { !it.retired }

    fun template(id: String): Template? = templates[id]

    companion object {
        const val EXERCISES_ASSET = "content/training/exercises.json"
        const val TEMPLATES_ASSET = "content/training/templates.json"
        const val RULES_ASSET = "content/training/rules.json"
        const val POSES_ASSET = "content/training/poses.json"

        /** From the JSON assets of the content pipeline. [poses] may be null (no keyframes yet). */
        fun fromJson(exercises: String, templates: String, rules: String, poses: String? = null): TrainingLibrary =
            fromTrees(Tree.parseJson(exercises), Tree.parseJson(templates), Tree.parseJson(rules), poses?.let(Tree::parseJson))

        fun fromTrees(
            exercises: Map<String, Any?>,
            templates: Map<String, Any?>,
            rules: Map<String, Any?>,
            poses: Map<String, Any?>? = null,
        ): TrainingLibrary {
            val ex = exercises.objects("exercises").mapNotNull(::exercise).associateBy { it.id }
            val tpl = templates.objects("templates").mapNotNull(::template).associateBy { it.id }
            val reviewed = listOf(exercises, templates, rules).all { it.list("reviewedBy").isNotEmpty() }
            return TrainingLibrary(ex, tpl, rules(rules), poses?.let(PoseLibrary::fromTree) ?: PoseLibrary.EMPTY, reviewed)
        }

        private fun exercise(m: Map<String, Any?>): Exercise? {
            val id = m.str("id") ?: return null
            val anim = m.obj("animation").orEmpty()
            val muscles = m.obj("muscles").orEmpty()
            return Exercise(
                id = id,
                nameRu = m.str("name_ru") ?: return null,
                level = Tier.of(m.str("level")) ?: return null,
                category = m.str("category") ?: return null,
                pattern = m.str("pattern").orEmpty(),
                primaryMuscles = muscles.strings("primary"),
                secondaryMuscles = muscles.strings("secondary"),
                equipment = m.strings("equipment").toSet(),
                place = m.strings("place").toSet(),
                measure = Measure.of(m.str("measure")),
                impact = m.str("impact") ?: "low",
                riskTags = m.strings("risk_tags").toSet(),
                default = Dose.of(m.obj("default")),
                techniqueRu = m.strings("technique_ru"),
                mistakesRu = m.strings("mistakes_ru"),
                cuesRu = m.strings("cues_ru"),
                breathingRu = m.str("breathing_ru"),
                contraindications = m.strings("contraindications").toSet(),
                regressions = m.strings("regressions"),
                progressions = m.strings("progressions"),
                swaps = m.strings("swaps"),
                animation = ExerciseAnimation(
                    view = anim.str("view") ?: "side",
                    loop = anim.bool("loop"),
                    props = anim.strings("props"),
                    poses = anim.objects("poses").mapNotNull { p -> p.str("key")?.let { PoseKey(it, p.str("ru").orEmpty()) } },
                ),
                retired = m.bool("retired"),
            )
        }

        private fun template(m: Map<String, Any?>): Template? {
            val id = m.str("id") ?: return null
            val modes = m.obj("modes").orEmpty()
            val parsedModes = DayMode.entries.associateWith { mode ->
                val v = modes.obj(mode.name)
                when {
                    v == null -> ModeUse.Full
                    v.str("swap") != null -> ModeUse.Swap(v.str("swap")!!)
                    v.str("use") == "lighter" -> ModeUse.Lighter
                    else -> ModeUse.Full
                }
            }
            val l = m.obj("lighter").orEmpty()
            val intensity = m.obj("intensity").orEmpty()
            val blocks = m.objects("blocks").mapNotNull { b ->
                TemplateBlock(
                    type = BlockType.of(b.str("type")) ?: return@mapNotNull null,
                    minutes = b.int("min") ?: 0,
                    format = b.str("format") ?: "flow",
                    rounds = b.int("rounds"),
                    restBetweenRoundsS = b.int("rest_between_rounds_s"),
                    items = b.objects("items").mapNotNull { i ->
                        TemplateItem(
                            ex = i.str("ex") ?: return@mapNotNull null,
                            dose = Dose.of(i),
                            perSide = i.bool("per_side"),
                            pair = i.str("pair"),
                            alt = i.strings("alt"),
                            noteRu = i.str("note_ru"),
                        )
                    },
                )
            }
            return Template(
                id = id,
                version = m.int("version") ?: 1,
                nameRu = m.str("name_ru") ?: id,
                level = m.str("level") ?: return null,
                method = m.str("method").orEmpty(),
                goal = m.strings("goal"),
                place = m.strings("place").toSet(),
                equipment = m.strings("equipment").toSet(),
                durationMin = m.int("duration_min") ?: blocks.sumOf { it.minutes },
                intensity = Intensity(intensity.str("rpe"), intensity.str("zone"), intensity.str("note_ru")),
                modes = parsedModes,
                lighter = Lighter(
                    setsDelta = l.int("sets_delta") ?: 0,
                    roundsDelta = l.int("rounds_delta") ?: 0,
                    durationPct = l.int("duration_pct") ?: 0,
                    volumePct = l.int("volume_pct") ?: 0,
                    rpeMax = l.int("rpe_max"),
                    zoneMax = l.str("zone_max"),
                    dropBlocks = l.strings("drop_blocks").mapNotNull(BlockType::of).toSet(),
                    dropExercises = l.strings("drop_exercises").toSet(),
                ),
                blocks = blocks,
                whyRu = m.str("why_ru"),
                contraindications = m.strings("contraindications").toSet(),
            )
        }

        private fun rules(m: Map<String, Any?>): TrainingRules {
            val caps = m.obj("regime_caps").orEmpty()
            val regimeCaps = DayMode.entries.associateWith { mode ->
                val c = caps.obj(mode.name).orEmpty()
                val allow = c["allow_methods"]
                val rpe = c.obj("rpe_max").orEmpty()
                RegimeCap(
                    allowMethods = (allow as? List<*>)?.mapNotNull { it?.toString() }?.toSet(),
                    rpeMax = Tier.entries.mapNotNull { t -> rpe.int(t.id)?.let { t to it } }.toMap(),
                    intervals = c.bool("intervals"),
                    forbidTags = c.strings("forbid_tags").toSet(),
                    zoneMax = c.str("zone_max"),
                )
            }
            val codes = m.obj("profile_codes").orEmpty().mapNotNull { (id, v) ->
                @Suppress("UNCHECKED_CAST")
                val c = v as? Map<String, Any?> ?: return@mapNotNull null
                id to ProfileCodeRule(
                    id = id,
                    forbidTags = c.strings("forbid_tags").toSet(),
                    forbidPatterns = c.strings("forbid_patterns").toSet(),
                    forbidMethods = c.strings("forbid_methods").toSet(),
                    rpeMax = c.int("rpe_max"),
                    hideTraining = c.bool("hide_training"),
                    noProgram = c.str("program") == "none",
                    maxLevel = Tier.of(c.str("max_level")),
                    route = c.str("route"),
                    from = c.obj("from").orEmpty(),
                )
            }.toMap()
            val player = m.obj("player").orEmpty()
            val rest = player.obj("rest_s").orEmpty()
            return TrainingRules(
                version = m.int("version") ?: 1,
                methods = m.objects("methods").mapNotNull { x -> x.str("id")?.let { it to (x.str("level") ?: "all") } }.toMap(),
                regimeCaps = regimeCaps,
                profileCodes = codes,
                player = PlayerDefaults(
                    restByCategory = rest.keys.mapNotNull { k -> rest.int(k)?.let { k to it } }.toMap(),
                    restHeavyS = player.int("rest_heavy_s") ?: 180,
                    perSideSwitchS = player.int("per_side_switch_s") ?: 5,
                    countdownS = player.int("countdown_s") ?: 3,
                    extendRestStepS = player.int("extend_rest_step_s") ?: 15,
                ),
                mobilityTemplates = m.obj("mobility_everywhere")?.strings("standalone_templates").orEmpty(),
                stopwords = m.strings("stopwords_ru"),
            )
        }
    }
}

package com.healthofatitan.app.integration.whoop

import com.healthofatitan.app.domain.engine.RuleSet
import com.healthofatitan.app.domain.metrics.Metrics
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The rule set `day@1` is written against fixed `metricId@ver` (A09 §1): if a metric changes its
 * version, the day engine must be revisited and this test fails first.
 */
class RuleSetMetricsTest {
    @Test
    fun `day rules read the metric versions that are computed`() {
        assertEquals(RuleSet.METRICS_REQUIRED.sorted(), Metrics.all.map { "${it.id}@${it.version}" }.sorted())
    }
}

package com.example.userprofile.platform

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs on every target. The assertion is deliberately platform-agnostic — the point is
 * that the `expect fun` has an `actual` on each target and the interface is honoured,
 * not what any particular OS reports.
 */
class PlatformTest {
    @Test
    fun deviceInfoIsAvailableOnThisTarget() {
        val info = deviceInfo()
        assertTrue(info.platform in setOf("jvm", "ios"), "unexpected platform ${info.platform}")
        assertTrue(info.osVersion.isNotBlank())
    }
}

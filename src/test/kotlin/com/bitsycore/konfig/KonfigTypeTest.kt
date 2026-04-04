package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KonfigTypeTest {

    @Test fun `exact enum name resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("DEBUG"))
    }

    @Test fun `exact enum name resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("RELEASE"))
    }

    @Test fun `task name containing debug resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("compileDebugKotlin"))
    }

    @Test fun `task name containing release resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("assembleRelease"))
    }

    @Test fun `ambiguous input returns null`() {
        assertNull(BuildType.resolve("debugRelease"))
    }

    @Test fun `unrelated string returns null`() {
        assertNull(BuildType.resolve("compileKotlinJvm"))
    }

    @Test fun `lowercase debug resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("debug"))
    }

    @Test fun `lowercase release resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("release"))
    }
}

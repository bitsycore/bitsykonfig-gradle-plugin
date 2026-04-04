package com.bitsycore.buildkonfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KonfigBuildTypeTest {

    @Test fun `exact enum name resolves DEBUG`() {
        assertEquals(KonfigBuildType.DEBUG, KonfigBuildType.resolve("DEBUG"))
    }

    @Test fun `exact enum name resolves RELEASE`() {
        assertEquals(KonfigBuildType.RELEASE, KonfigBuildType.resolve("RELEASE"))
    }

    @Test fun `task name containing debug resolves DEBUG`() {
        assertEquals(KonfigBuildType.DEBUG, KonfigBuildType.resolve("compileDebugKotlin"))
    }

    @Test fun `task name containing release resolves RELEASE`() {
        assertEquals(KonfigBuildType.RELEASE, KonfigBuildType.resolve("assembleRelease"))
    }

    @Test fun `ambiguous input returns null`() {
        assertNull(KonfigBuildType.resolve("debugRelease"))
    }

    @Test fun `unrelated string returns null`() {
        assertNull(KonfigBuildType.resolve("compileKotlinJvm"))
    }

    @Test fun `lowercase debug resolves DEBUG`() {
        assertEquals(KonfigBuildType.DEBUG, KonfigBuildType.resolve("debug"))
    }

    @Test fun `lowercase release resolves RELEASE`() {
        assertEquals(KonfigBuildType.RELEASE, KonfigBuildType.resolve("release"))
    }
}

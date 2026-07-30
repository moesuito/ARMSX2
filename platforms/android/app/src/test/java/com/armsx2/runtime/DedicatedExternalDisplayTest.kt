package com.armsx2.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedicatedExternalDisplayTest {
    @Test
    fun presentationRequiresEveryGateAndRejectsDesktopMode() {
        assertTrue(DedicatedExternalDisplay.shouldPresent(true, true, true, false, true))
        assertFalse(DedicatedExternalDisplay.shouldPresent(false, true, true, false, true))
        assertFalse(DedicatedExternalDisplay.shouldPresent(true, false, true, false, true))
        assertFalse(DedicatedExternalDisplay.shouldPresent(true, true, false, false, true))
        assertFalse(DedicatedExternalDisplay.shouldPresent(true, true, true, true, true))
        assertFalse(DedicatedExternalDisplay.shouldPresent(true, true, true, false, false))
    }
}

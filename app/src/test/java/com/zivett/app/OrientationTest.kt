package com.zivett.app

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/// Portrait on phones (and a folded foldable); free on large screens.
class OrientationTest {
    @Test fun phonesStayPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationFor(360))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationFor(599))
    }

    @Test fun largeScreensFollowTheDevice() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationFor(600))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationFor(840))
    }
}

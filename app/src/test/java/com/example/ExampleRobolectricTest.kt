package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShakePreferences
import com.example.model.ShakePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShakeWake", appName)
    }

    @Test
    fun `test shake preferences default values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ShakePreferences.getInstance(context)
        val settings = prefs.getSettings()

        assertTrue(settings.isEnabled)
        assertEquals(18.0f, settings.threshold, 0.1f)
        assertEquals(2, settings.shakesRequired)
        assertTrue(settings.pocketMode)
    }

    @Test
    fun `test preset thresholds`() {
        assertEquals(13.0f, ShakePreset.GENTLE.threshold, 0.1f)
        assertEquals(18.0f, ShakePreset.BALANCED.threshold, 0.1f)
        assertEquals(24.0f, ShakePreset.FIRM.threshold, 0.1f)
        assertEquals(30.0f, ShakePreset.STRONG.threshold, 0.1f)
    }
}

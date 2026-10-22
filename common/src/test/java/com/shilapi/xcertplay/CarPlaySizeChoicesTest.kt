package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en")
class CarPlaySizeChoicesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aPresetShowsItsLengthAndCustomStartsFromIt() {
        val choices = carPlaySizeChoices(context, 300)
        assertEquals(
            listOf("Large · 250 mm", "Medium · 300 mm", "Small · 350 mm", "Custom…"),
            choices.map { it.label },
        )
        assertTrue(choices.last().custom)
        assertEquals(300, choices.last().widthMillimeters)
        assertFalse(choices.first().custom)
    }

    @Test
    fun aLengthSetInTheCarPlayMenuRowShowsThatLength() {
        val choices = carPlaySizeChoices(context, 275)
        assertEquals("Custom · 275 mm", choices.last().label)
        assertTrue(choices.last().custom)
        assertEquals(275, choices.last().widthMillimeters)
    }
}

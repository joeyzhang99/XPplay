package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlaySizeTest {
    @Test
    fun `presets map back to themselves`() {
        CarPlaySize.entries.forEach { assertEquals(it, CarPlaySize.exact(it.widthMillimeters)) }
    }

    @Test
    fun `only the three preset widths count as a preset`() {
        org.junit.Assert.assertNull(CarPlaySize.exact(200))
        org.junit.Assert.assertNull(CarPlaySize.exact(275))
        org.junit.Assert.assertNull(CarPlaySize.exact(400))
    }

    @Test
    fun `presets fit the reported width range`() {
        CarPlaySize.entries.forEach {
            assertEquals(it.widthMillimeters, AirPlayDisplaySettings.sanitizeWidthPhysicalMm(it.widthMillimeters))
        }
    }

    @Test
    fun `physical length moves in five millimetre steps`() {
        assertEquals(225, AirPlayDisplaySettings.sanitizeWidthPhysicalMm(225))
        assertEquals(230, AirPlayDisplaySettings.sanitizeWidthPhysicalMm(231))
        assertEquals(240, AirPlayDisplaySettings.sanitizeWidthPhysicalMm(240))
        assertEquals(25, AirPlayDisplaySettings.widthPhysicalMmProgress(225))
    }
}

package net.zeevox.nearow

import net.zeevox.nearow.utils.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConversionTests {
    @Test
    fun speedToSplitFormatTest() {
        assertEquals("2:05.0", UnitConverter.speedToSplitFormatted(4.0f))
        assertEquals("1:40.0", UnitConverter.speedToSplitFormatted(5.0f))
        assertEquals("1:02.5", UnitConverter.speedToSplitFormatted(8.0f))
    }

    @Test
    // checking with values from https://www.concept2.com/indoor-rowers/training/calculators/watts-calculator
    fun speedToWattsTest() {
        assertEquals(179.2, UnitConverter.speedToWatts(4.0f), 0.1)
        assertEquals(350.0, UnitConverter.speedToWatts(5.0f), 0.1)
        assertEquals(1433.6, UnitConverter.speedToWatts(8.0f), 0.1)
    }
}
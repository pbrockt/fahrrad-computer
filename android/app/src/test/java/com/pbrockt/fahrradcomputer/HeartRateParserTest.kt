package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.sensors.HeartRateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun uint8() = assertEquals(72, HeartRateParser.parse(byteArrayOf(0x00, 72)))

    @Test
    fun uint16LittleEndian() =
        assertEquals(180, HeartRateParser.parse(byteArrayOf(0x01, 0xB4.toByte(), 0x00)))

    @Test
    fun uint8AboveSignedRange() = assertEquals(180, HeartRateParser.parse(byteArrayOf(0x00, 180.toByte())))

    @Test
    fun withRrIntervalsAppended() =
        assertEquals(65, HeartRateParser.parse(byteArrayOf(0x10, 65, 0x20, 0x03)))

    @Test
    fun zeroMeansNoSkinContact() = assertNull(HeartRateParser.parse(byteArrayOf(0x00, 0)))

    @Test
    fun tooShort() {
        assertNull(HeartRateParser.parse(byteArrayOf()))
        assertNull(HeartRateParser.parse(byteArrayOf(0x01, 80)))
    }
}

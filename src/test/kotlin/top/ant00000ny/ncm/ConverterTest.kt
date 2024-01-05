package top.ant00000ny.ncm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ConverterTest {
    @Test
    fun testConvert() {
        val ncmFile = File(object {}.javaClass.classLoader.getResource("ずっと真夜中でいいのに。 - 脳裏上のクラッカー.ncm")!!.toURI())
        val outputFile = Converter().convertNcm(ncmFile)
        assertTrue { outputFile.exists() }
    }
}

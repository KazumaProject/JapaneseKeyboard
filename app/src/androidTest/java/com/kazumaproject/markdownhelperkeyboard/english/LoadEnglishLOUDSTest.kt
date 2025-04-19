package com.kazumaproject.markdownhelperkeyboard.english

import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.english.EnglishLOUDS
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ObjectInputStream

class LoadEnglishLOUDSTest {
    @Before
    fun setup() {

    }

    @Test
    fun testLoadEnglishLOUDS() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val objectInput = ObjectInputStream(
            BufferedInputStream(context.assets.open("english/english.dat"))
        )
        val result = EnglishLOUDS().readExternal(objectInput)
        println("Loaded object: ${result.costListSave.size}")
        objectInput.close()
    }

}
package com.kazumaproject.connection_id

import com.kazumaproject.byteArrayToShortList
import com.kazumaproject.toByteArrayFromListShort
import java.io.ObjectInput
import java.io.ObjectOutput
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ConnectionIdBuilder {
    fun build(
        out: ObjectOutput,
        value: List<Short>
    ){
        try {
            out.apply {
                writeObject(value.toByteArrayFromListShort().size)
                writeObject(value.toByteArrayFromListShort().deflate())
                flush()
                close()
            }
        }catch (e: Exception){
            println(e.message)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun read(objectInput: ObjectInput): List<Short>{
        var a: List<Short>
        return try {
            objectInput.apply {
                val time = measureTime {
                    val byteSize = readObject() as Int
                    a = (readObject() as ByteArray).inflate(byteSize).byteArrayToShortList()
                    close()
                }
                println("loading time connection ids: $time")
            }
            a
        }catch (e: Exception){
            println(e.message)
            emptyList()
        }
    }
}
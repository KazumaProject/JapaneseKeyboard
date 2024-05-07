package com.kazumaproject.connection_id

import com.kazumaproject.byteArrayToShortList
import com.kazumaproject.toByteArrayFromListShort
import java.io.ObjectInput
import java.io.ObjectOutput

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

    fun read(objectInput: ObjectInput): List<Short>{
        try {
            objectInput.apply {
                val byteSize = readObject() as Int
                val a = (readObject() as ByteArray).inflate(byteSize).byteArrayToShortList()
                close()
                return a
            }
        }catch (e: Exception){
            println(e.message)
            return emptyList()
        }
    }
}
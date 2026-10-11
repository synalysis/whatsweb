package com.synalysis.whatsweb.web

import java.io.InputStream
import java.io.OutputStream

internal object ByteStreams {
    class LimitExceeded : Exception()

    fun copy(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        if (maxBytes < 0) throw LimitExceeded()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            if (total + read > maxBytes) throw LimitExceeded()
            output.write(buffer, 0, read)
            total += read
        }
    }
}

package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.mfi.MfiAuthenticator
import java.io.Closeable
import java.io.IOException

/** Signals the normal, retryable state where the MFi address probe found no chip. */
internal class MfiCoprocessorNotFoundException(
    message: String = "No MFi authentication coprocessor responded to the device probe",
) : IOException(message)

/** An opened MFi coprocessor client plus the handle that releases its backing transport. */
class MfiSession(
    val client: MfiAuthenticator,
    private val closeable: Closeable?,
) : Closeable {
    override fun close() {
        closeable?.close()
    }
}

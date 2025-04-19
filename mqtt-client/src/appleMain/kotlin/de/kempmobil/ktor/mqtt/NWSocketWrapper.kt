package de.kempmobil.ktor.mqtt

import cocoapods.SocketWrapper.SocketWrapper
import com.ditchoom.buffer.ByteOrder
import com.ditchoom.buffer.DataBuffer
import com.ditchoom.buffer.ReadBuffer
import com.ditchoom.buffer.ReadBuffer.Companion.EMPTY_BUFFER
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal open class NWSocketWrapper {
    internal var socket: SocketWrapper? = null
    private val readMutex = Mutex()
    private val writeMutex = Mutex()

    fun isOpen(): Boolean = socket?.isOpen() ?: false

    suspend fun localPort(): Int = socket?.localPort()?.toInt() ?: -1

    suspend fun remotePort(): Int = socket?.remotePort()?.toInt() ?: -1

    suspend fun read(timeout: Duration): ReadBuffer {
        val socket = socket ?: return EMPTY_BUFFER
        return readMutex.withLock {
            withTimeout(timeout) {
                suspendCancellableCoroutine {
                    socket.readDataWithCompletion { data, errorString, _ ->
                        if (errorString != null) {
                            closeInternal()
                            it.resumeWithException(SocketClosedException(errorString))
                        } else if (data != null) {
                            if (data.length.toInt() == 0) {
                                it.resume(EMPTY_BUFFER)
                            } else {
                                val d = DataBuffer(data, ByteOrder.BIG_ENDIAN)
                                d.position(data.length.toInt())
                                it.resume(d)
                            }
                        } else {
                            it.resumeWithException(IllegalStateException("Failed to get a valid error message from reading on socket"))
                        }
                    }
                    it.invokeOnCancellation {
                        closeInternal()
                    }
                }
            }
        }
    }

    override suspend fun write(
        buffer: ReadBuffer,
        timeout: Duration,
    ): Int {
        val socket = socket ?: return -1
        val readBuffer = buffer as DataBuffer
        return writeMutex.withLock {
            withTimeout(timeout) {
                suspendCancellableCoroutine { continuation ->
                    socket.writeDataWithBuffer(readBuffer.data) { bytesWritten, errorString ->
                        if (errorString != null) {
                            continuation.resumeWithException(SocketException(errorString))
                        } else {
                            val writtenBytes = bytesWritten.toInt()
                            readBuffer.position(readBuffer.position() + writtenBytes)
                            continuation.resume(writtenBytes)
                        }
                    }
                    continuation.invokeOnCancellation {
                        closeInternal()
                    }
                }
            }
        }
    }

    private fun closeInternal() {
        val socket = socket ?: return
        if (!isOpen()) return
        socket.cancel()
        socket.forceCancel()
    }

    override suspend fun close() {
        closeInternal()
    }
}
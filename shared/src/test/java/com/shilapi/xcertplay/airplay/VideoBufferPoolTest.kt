package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBufferPoolTest {
    @Test
    fun releasedBufferIsReusedWithoutExposingBucketPadding() {
        val pool = VideoBufferPool(maxPerBucket = 1)
        val first = pool.acquire(20_001)
        val storage = first.bytes
        assertTrue(storage.size >= 20_001)
        first.close()

        val second = pool.acquire(19_000)
        assertSame(storage, second.bytes)
        assertEquals(19_000, second.length)
        second.close()
    }
}

package com.maldawr.mediagrabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectMediaPolicyTest {
    @Test
    fun acceptsSecureDirectUrl() {
        assertNull(DirectMediaPolicy.validationError("https://cdn.example.com/media/video.mp4"))
    }

    @Test
    fun rejectsCleartextHttp() {
        assertEquals(
            "https_required",
            DirectMediaPolicy.validationError("http://cdn.example.com/video.mp4")
        )
    }

    @Test
    fun rejectsYouTubeHosts() {
        assertEquals(
            "blocked_source",
            DirectMediaPolicy.validationError("https://www.youtube.com/watch?v=abc")
        )
        assertEquals(
            "blocked_source",
            DirectMediaPolicy.validationError("https://youtu.be/abc")
        )
    }
}

package com.appause.android.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitDiagnosticTest {

    @Test
    fun `report keeps unknown reason numeric`() {
        val report = ProcessExitDiagnostic(
            reason = 987,
            timestamp = 123456789L,
            importance = 125,
            descriptionHint = null
        ).toReportLine()

        assertTrue(report.contains("reason=987"))
        assertTrue(report.contains("timestamp=123456789"))
        assertTrue(report.contains("importance=125"))
    }

    @Test
    fun `description whitespace is normalized and text is bounded`() {
        assertEquals("SwipeUpClean stopped process", sanitizeProcessExitDescription("  SwipeUpClean\n\tstopped   process  "))

        val longHint = sanitizeProcessExitDescription("x".repeat(200))
        assertEquals(160, longHint?.length)
    }

    @Test
    fun `blank description is omitted`() {
        assertNull(sanitizeProcessExitDescription(" \n\t "))
    }
}

package org.lianye.service.capture

import org.junit.Assert.*
import org.junit.Test
import org.lianye.domain.model.ManualControl
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase

class ManualSessionFeedbackTest {
    @Test fun `temporary gap keeps finish available and restores floating route after recovery`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.temporaryUnmatched(true)
        assertEquals(ManualPhase.RECORDING, session.state.phase)
        assertEquals(1, session.state.acceptedFrames)
        assertEquals("RECOVERY", session.state.guide.name)
        session.temporaryUnmatched(false)
        assertEquals(ManualGuide.FULL, session.state.guide)
        session.accepted(2)
        session.temporaryUnmatched(true); session.temporaryUnmatched(false)
        assertEquals(ManualGuide.ROUTE, session.state.guide)
        assertNull(session.state.message)
        session.finish(); session.temporaryUnmatched(false)
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }

    @Test fun `only accepted new content reduces guidance and never removes route`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY)
        assertEquals(0, session.state.acceptedFrames)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        session.begin()
        session.accepted(1)
        assertEquals(ManualGuide.FULL, session.state.guide)
        session.accepted(1)
        assertEquals(ManualGuide.FULL, session.state.guide)
        session.accepted(2)
        assertEquals(ManualGuide.ROUTE, session.state.guide)
        session.accepted(4)
        assertEquals(ManualGuide.ROUTE, session.state.guide)
        assertTrue(session.learnedThisSession)
    }

    @Test fun `notification capture cannot mark unseen floating guidance as learned`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.NOTIFICATION); session.begin()
        session.accepted(1); session.accepted(2)
        assertFalse(session.learnedThisSession)
        val next = ManualSessionFeedback(session.learnedThisSession)
        next.ready(ManualControl.OVERLAY); next.begin(); next.accepted(1)
        assertEquals(ManualGuide.FULL, next.state.guide)
    }

    @Test fun `dismissal is confined to this session and never stops capture`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.dismissGuide(); session.accepted(2)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        assertEquals(ManualPhase.RECORDING, session.state.phase)
        val next = ManualSessionFeedback(session.learnedThisSession)
        next.ready(ManualControl.OVERLAY); next.begin(); next.accepted(1)
        assertEquals(ManualGuide.ROUTE, next.state.guide)
    }

    @Test fun `ending before first frame waits for original callback`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY); session.begin()
        assertFalse(session.finish())
        assertEquals(0, session.state.acceptedFrames)
        session.accepted(1)
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        assertTrue(session.finishRequested)
    }

    @Test fun `gap clears guidance and final progress cannot restart it`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.NOTIFICATION); session.begin(); session.accepted(1)
        session.gap(); session.accepted(2)
        assertEquals(ManualPhase.GAP, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        assertTrue(session.finish())
        assertEquals(ManualPhase.FINISHING, session.state.phase)
    }

    @Test fun `first frame failure has no successful count`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY); session.begin(); session.failedFirst()
        assertEquals(ManualPhase.FIRST_FAILED, session.state.phase)
        assertEquals(0, session.state.acceptedFrames)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        session.begin(); session.accepted(1)
        assertEquals(ManualGuide.FULL, session.state.guide)
    }

    @Test fun `queued gap cannot reopen controls after finish`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.finish(); session.gap()
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }

    @Test fun `notification occlusion keeps finish available and can recover`() {
        val session = ManualSessionFeedback(false)
        session.ready(ManualControl.NOTIFICATION); session.begin(); session.accepted(1)
        session.temporaryUnmatched(true)
        assertEquals(ManualPhase.RECORDING, session.state.phase)
        assertEquals(1, session.state.acceptedFrames)
        assertEquals("temporarily_unmatched", session.state.message)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        session.temporaryUnmatched(false)
        assertNull(session.state.message)
        session.accepted(2)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
        session.finish(); session.temporaryUnmatched(false)
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }

    @Test fun `learned users keep recovery guidance until alignment actually returns`() {
        val session = ManualSessionFeedback(true)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.temporaryUnmatched(true)
        assertEquals("RECOVERY", session.state.guide.name)
        session.temporaryUnmatched(true); session.accepted(2)
        assertEquals("RECOVERY", session.state.guide.name)
        assertEquals("temporarily_unmatched", session.state.message)
        session.temporaryUnmatched(false)
        assertEquals(ManualGuide.ROUTE, session.state.guide)
        assertNull(session.state.message)
        session.finish(); session.temporaryUnmatched(true)
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }

    @Test fun `reverse restores full forward instructions until new content is accepted`() {
        val session = ManualSessionFeedback(true)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.reversed()
        assertEquals(ManualGuide.FULL, session.state.guide)
        assertNull(session.state.message)
        session.accepted(1)
        assertEquals(ManualGuide.FULL, session.state.guide)
        session.accepted(2)
        assertEquals(ManualGuide.ROUTE, session.state.guide)
        session.finish(); session.reversed()
        assertEquals(ManualPhase.FINISHING, session.state.phase)
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }

    @Test fun `reverse during recovery is guidance rather than another error`() {
        val session = ManualSessionFeedback(true)
        session.ready(ManualControl.OVERLAY); session.begin(); session.accepted(1)
        session.temporaryUnmatched(true); session.temporaryUnmatched(false); session.reversed()
        assertEquals(ManualGuide.FULL, session.state.guide)
        assertNull(session.state.message)
        assertEquals(1, session.state.acceptedFrames)
        session.control(ManualControl.NOTIFICATION); session.reversed()
        assertEquals(ManualGuide.HIDDEN, session.state.guide)
    }
}

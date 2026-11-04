package com.tutorbridge.app

import com.tutorbridge.app.data.model.NamedIdDto
import com.tutorbridge.app.data.model.newRequestDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAreaTest {

    private val areas = listOf(NamedIdDto("a1", "Library"), NamedIdDto("a2", "Main Hall"))

    @Test
    fun `a new request starts on the default area`() {
        val draft = newRequestDraft("a2", areas)
        assertEquals("a2", draft.areaId)
        assertEquals("Main Hall", draft.preferredVenue)
    }

    @Test
    fun `no default, or one that no longer exists, leaves the area to be chosen`() {
        assertEquals("", newRequestDraft("", areas).areaId)
        assertEquals("", newRequestDraft("gone", areas).areaId)
    }
}

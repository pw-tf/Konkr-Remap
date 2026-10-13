package com.pwtf.konkrremap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturesTest {
    private var now = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val out = mutableListOf<String>()
    private val g = Gestures(
        post = { d, b -> val t = (now + d) to b; timers += t; { timers.remove(t) } },
        emit = { c, down, rep -> out += "${c.joinToString("+")}${if (down) "↓" else "↑"}${if (rep > 0) rep else ""}" },
    )

    private fun advance(ms: Long) {
        now += ms
        timers.filter { it.first <= now }.forEach { timers.remove(it); it.second() }
    }

    private val a = 96
    private val plain = Mapping(listOf(1))
    private val rich = Mapping(tap = listOf(1), hold = listOf(2), double = listOf(3))

    @Test fun unmappedPassesThrough() {
        assertFalse(g.onKey(a, true, 0, null))
        assertFalse(g.onKey(a, false, 0, null))
        assertEquals(emptyList<String>(), out)
    }

    @Test fun plainRemapMirrorsHoldAndRepeat() {
        assertTrue(g.onKey(a, true, 0, plain))
        g.onKey(a, true, 1, plain)
        g.onKey(a, false, 0, plain)
        assertEquals(listOf("1↓", "1↓1", "1↑"), out)
    }

    @Test fun tapWaitsForDoubleWindow() {
        g.onKey(a, true, 0, rich); advance(100); g.onKey(a, false, 0, rich)
        assertEquals(emptyList<String>(), out)
        advance(Gestures.DOUBLE_MS)
        assertEquals(listOf("1↓", "1↑"), out)
    }

    @Test fun hold() {
        g.onKey(a, true, 0, rich); advance(Gestures.HOLD_MS)
        assertEquals(listOf("2↓"), out)
        g.onKey(a, false, 0, rich)
        assertEquals(listOf("2↓", "2↑"), out)
    }

    @Test fun doublePress() {
        g.onKey(a, true, 0, rich); g.onKey(a, false, 0, rich); advance(100)
        g.onKey(a, true, 0, rich); g.onKey(a, false, 0, rich); advance(1000)
        assertEquals(listOf("3↓", "3↑"), out)
    }

    @Test fun holdWithoutHoldActionHoldsTap() {
        val m = Mapping(tap = listOf(1), double = listOf(3))
        g.onKey(a, true, 0, m); advance(Gestures.HOLD_MS); g.onKey(a, false, 0, m)
        assertEquals(listOf("1↓", "1↑"), out)
    }

    @Test fun versions() {
        assertTrue(newer("1.10.0", "1.9.2"))
        assertTrue(newer("0.1.0", "0.0.0"))
        assertFalse(newer("1.0", "1.0.0"))
        assertFalse(newer("0.9.9", "1.0.0"))
    }
}

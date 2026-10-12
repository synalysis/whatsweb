package com.synalysis.whatsweb

import com.synalysis.whatsweb.prefs.CompanionAccount
import com.synalysis.whatsweb.prefs.LinkRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRecordsTest {
    @Test
    fun missingListIsTheOriginalAccount() {
        assertEquals(listOf(CompanionAccount(LinkRecords.MAIN, "")), LinkRecords.decode(null))
        assertEquals(listOf(CompanionAccount(LinkRecords.MAIN, "")), LinkRecords.decode("not json"))
    }

    @Test
    fun roundTripKeepsNames() {
        val accounts = listOf(
            CompanionAccount(LinkRecords.MAIN, "Home", "15551234567"),
            CompanionAccount("a1b2c3d4e5f6", "Work"),
        )
        assertEquals(accounts, LinkRecords.decode(LinkRecords.encode(accounts)))
    }

    @Test
    fun oldLinesWithoutANumberStillLoad() {
        assertEquals(
            listOf(CompanionAccount(LinkRecords.MAIN, "Home")),
            LinkRecords.decode("main\tHome"),
        )
        assertEquals(
            listOf(CompanionAccount(LinkRecords.MAIN, "Home")),
            LinkRecords.decode("main\tHome\t123"),
        )
        assertNull(LinkRecords.cleanPhone("123"))
        assertEquals("15551234567", LinkRecords.cleanPhone("15551234567"))
        assertNull(LinkRecords.cleanPhone("1555abc4567"))
        assertEquals("525551234567", LinkRecords.cleanPhone("5215551234567"))
        assertEquals(
            listOf(CompanionAccount(LinkRecords.MAIN, "Home", "525551234567")),
            LinkRecords.decode("main\tHome\t5215551234567"),
        )
    }

    @Test
    fun dropsBadIdsAndDuplicates() {
        val raw = """
            ../etc	Bad
            main	Home
            main	Again
            work	  Work   account  
            not-an-id	Skipped
        """.trimIndent()
        assertEquals(
            listOf(
                CompanionAccount(LinkRecords.MAIN, "Home"),
                CompanionAccount("work", "Work account"),
            ),
            LinkRecords.decode(raw),
        )
    }

    @Test
    fun blankNameIsRejected() {
        assertNull(LinkRecords.cleanName("   "))
        assertEquals("Ada", LinkRecords.cleanName(" Ada "))
        assertTrue(LinkRecords.isId(LinkRecords.MAIN))
        assertFalse(LinkRecords.isId("Work"))
        assertFalse(LinkRecords.isId("a/b"))
    }
}

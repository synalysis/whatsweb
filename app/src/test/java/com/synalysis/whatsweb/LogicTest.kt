package com.synalysis.whatsweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.synalysis.whatsweb.web.BlobTransfer
import com.synalysis.whatsweb.web.ByteStreams
import com.synalysis.whatsweb.web.DesktopIdentity
import com.synalysis.whatsweb.web.HostMessages
import com.synalysis.whatsweb.web.LinkRouter
import com.synalysis.whatsweb.web.TelemetryBlock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

class LinkRouterTest {
    @Test
    fun companionPageStays() {
        assertEquals(
            LinkRouter.Route.Stay,
            LinkRouter.route("https://web.whatsapp.com/"),
        )
    }

    @Test
    fun marketingSiteRecovers() {
        assertEquals(LinkRouter.Route.Recover, LinkRouter.route("https://www.whatsapp.com/"))
        assertEquals(LinkRouter.Route.Recover, LinkRouter.route("https://whatsapp.com/download"))
    }

    @Test
    fun waMeBecomesSendLink() {
        assertEquals(
            LinkRouter.Route.Translate("https://web.whatsapp.com/send?phone=15551212&text=Hi%20there"),
            LinkRouter.route("https://wa.me/15551212?text=Hi%20there"),
        )
    }

    @Test
    fun apiSendBecomesSendLink() {
        assertEquals(
            LinkRouter.Route.Translate("https://web.whatsapp.com/send?phone=15551212"),
            LinkRouter.route("https://api.whatsapp.com/send?phone=15551212"),
        )
    }

    @Test
    fun inviteBecomesAcceptLink() {
        assertEquals(
            LinkRouter.Route.Translate("https://web.whatsapp.com/accept?code=AbCdEfGhIj12"),
            LinkRouter.route("https://chat.whatsapp.com/AbCdEfGhIj12"),
        )
    }

    @Test
    fun otherSitesLeaveTheApp() {
        val route = LinkRouter.route("https://example.com/help")
        assertTrue(route is LinkRouter.Route.External)
    }

    @Test
    fun javascriptUrlsAreDropped() {
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("javascript:alert(1)"))
    }

    @Test
    fun httpCompanionIsUpgraded() {
        val route = LinkRouter.route("http://web.whatsapp.com/")
        assertEquals(LinkRouter.Route.Translate("https://web.whatsapp.com/"), route)
        assertEquals(
            LinkRouter.Route.Translate("https://web.whatsapp.com/"),
            LinkRouter.route("http://web.whatsapp.com/", external = true),
        )
    }

    @Test
    fun externalPageUrlsCannotEnterTheCompanion() {
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("data:text/html,hi", external = true))
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("blob:https://web.whatsapp.com/1", external = true))
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("https://example.com/help", external = true))
        assertEquals(LinkRouter.Route.Stay, LinkRouter.route("data:text/html,hi"))
        assertEquals(
            LinkRouter.Route.Stay,
            LinkRouter.route("https://web.whatsapp.com/", external = true),
        )
    }

    @Test
    fun disguisedCompanionUrlsAreRejected() {
        assertFalse(LinkRouter.isCompanionPage("http://web.whatsapp.com/"))
        assertFalse(LinkRouter.isCompanionPage("https://evil@web.whatsapp.com/"))
        assertFalse(LinkRouter.isCompanionPage("https://web.whatsapp.com:444/"))
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("https://evil@web.whatsapp.com/"))
        assertEquals(LinkRouter.Route.Drop, LinkRouter.route("https://web.whatsapp.com:444/"))
        assertTrue(LinkRouter.isTrustedCompanionOrigin("https://web.whatsapp.com"))
    }
}

class DesktopIdentityTest {
    @Test
    fun versionIsFourNumbers() {
        assertEquals("131.0.6778.135", DesktopIdentity.chromeVersion("131.0.6778.135"))
        assertEquals(DesktopIdentity.FALLBACK_VERSION, DesktopIdentity.chromeVersion("not a version"))
        assertEquals("120.0.0.1", DesktopIdentity.chromeVersion("120.0.0.1 (extra)"))
    }

    @Test
    fun userAgentLooksLikeDesktopChrome() {
        val agent = DesktopIdentity.userAgent("131.0.6778.135")
        assertTrue(agent.contains("Chrome/131.0.6778.135"))
        assertFalse(agent.contains("Android"))
        assertFalse(agent.contains("Mobile"))
        assertFalse(agent.contains("wv"))
    }
}

class TitlesTest {
    @Test
    fun qrUsesLinkTitle() {
        assertEquals(
            "Link",
            Titles.bar("WhatsApp", "list", linked = false, chats = "Chats", link = "Link", conversation = "Conversation"),
        )
    }

    @Test
    fun chatNameIsKept() {
        assertEquals(
            "Ada",
            Titles.bar("Ada", "chat", linked = true, chats = "Chats", link = "Link", conversation = "Conversation"),
        )
    }

    @Test
    fun genericTitleFallsBack() {
        assertEquals(
            "Chats",
            Titles.bar("WhatsApp", "list", linked = true, chats = "Chats", link = "Link", conversation = "Conversation"),
        )
        assertEquals(
            "Conversation",
            Titles.bar(null, "chat", linked = true, chats = "Chats", link = "Link", conversation = "Conversation"),
        )
        assertEquals(
            "Chats",
            Titles.bar("(1) WhatsApp", "list", linked = true, chats = "Chats", link = "Link", conversation = "Conversation"),
        )
    }
}

class TelemetryBlockTest {
    @Test
    fun telemetryHostsAreBlocked() {
        assertTrue(TelemetryBlock.blocks("https://dit.whatsapp.net/deidentified_telemetry"))
        assertTrue(TelemetryBlock.blocks("https://crashlogs.whatsapp.net/wa_fls_upload_check"))
        assertTrue(TelemetryBlock.blocks("https://crashlogs.whatsapp.net/wa_clb_data"))
        assertTrue(TelemetryBlock.blocks("https://web.whatsapp.com/ajax/bz?__a=1"))
    }

    @Test
    fun chatTrafficIsLeftAlone() {
        assertFalse(TelemetryBlock.blocks("https://web.whatsapp.com/"))
        assertFalse(TelemetryBlock.blocks("https://web.whatsapp.com/graphql"))
        assertFalse(TelemetryBlock.blocks("https://web.whatsapp.com/ajax/bootloader-endpoint"))
        assertFalse(TelemetryBlock.blocks("https://static.whatsapp.net/rsrc.php/v4/ye/r/app.js"))
        assertFalse(TelemetryBlock.blocks("https://media-qro1-1.cdn.whatsapp.net/v/t61.24694-24/photo"))
        assertFalse(TelemetryBlock.blocks("wss://web.whatsapp.com/ws/chat"))
    }
}

class HostMessagesTest {
    @Test
    fun parsesCompanionMessages() {
        assertEquals(
            HostMessages.Message.Pane("chat"),
            HostMessages.parse("""{"type":"pane","pane":"chat"}"""),
        )
        assertEquals(
            HostMessages.Message.Cover(true),
            HostMessages.parse("""{"type":"cover","open":true}"""),
        )
        assertEquals(HostMessages.Message.Session(false), HostMessages.parse("""{"type":"session","linked":false}"""))
        assertEquals(HostMessages.Message.Self("15551234567"), HostMessages.parse("""{"type":"self","phone":"15551234567"}"""))
        assertEquals(HostMessages.Message.Self(""), HostMessages.parse("""{"type":"self","phone":""}"""))
        assertEquals(HostMessages.Message.Call(true), HostMessages.parse("""{"type":"call","active":true}"""))
        assertEquals(HostMessages.Message.Call(false), HostMessages.parse("""{"type":"call","active":false}"""))
        assertEquals(HostMessages.Message.Fail, HostMessages.parse("""{"type":"fail"}"""))
        assertEquals(
            HostMessages.Message.Chunk("abc", 1, 2, "image/png", "YQ=="),
            HostMessages.parse("""{"type":"chunk","id":"abc","index":1,"total":2,"mime":"image/png","part":"YQ=="}"""),
        )
    }

    @Test
    fun rejectsMalformedMessages() {
        assertNull(HostMessages.parse("""{"type":"pane","pane":"chat","pane":"list"}"""))
        assertNull(HostMessages.parse("""{"type":"session","linked":{"nested":true}}"""))
        assertNull(HostMessages.parse("""{"type":"self","phone":"12"}"""))
        assertNull(HostMessages.parse("""{"type":"self"}"""))
        assertNull(HostMessages.parse("{\"type\":\"pane\",\"pane\":\"line\nbreak\"}"))
        assertEquals(
            HostMessages.Message.Pane("a\"b"),
            HostMessages.parse("""{"type":"pane","pane":"a\"b"}"""),
        )
    }
}

class BlobTransferTest {
    @Test
    fun assemblesOrderedChunksAndRejectsExcess() {
        val root = File.createTempFile("blob", "").apply {
            delete()
            mkdirs()
        }
        try {
            val transfer = BlobTransfer(root, maxBytes = 8, maxConcurrent = 2, ttlMillis = 1_000)
            val first = piece("one", 0, "hi", total = 2)
            val second = piece("one", 1, "!", total = 2)
            assertTrue(transfer.offer(first, 0) is BlobTransfer.Offer.Pending)
            val ready = transfer.offer(second, 1)
            assertTrue(ready is BlobTransfer.Offer.Ready)
            assertEquals("hi!", (ready as BlobTransfer.Offer.Ready).file.readText())

            val blocked = BlobTransfer(root, maxBytes = 8, maxConcurrent = 1, ttlMillis = 1_000)
            assertTrue(blocked.offer(piece("a", 0, "1234", total = 2), 0) is BlobTransfer.Offer.Pending)
            assertTrue(blocked.offer(piece("b", 0, "1234", total = 2), 1) is BlobTransfer.Offer.Rejected)
            assertTrue(blocked.offer(piece("a", 1, "123456789", total = 2), 2) is BlobTransfer.Offer.Rejected)
            blocked.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun expiresIncompleteTransfers() {
        val root = File.createTempFile("blob-ttl", "").apply {
            delete()
            mkdirs()
        }
        try {
            val transfer = BlobTransfer(root, ttlMillis = 10)
            assertTrue(transfer.offer(piece("old", 0, "a", total = 2), 0) is BlobTransfer.Offer.Pending)
            assertTrue(transfer.offer(piece("new", 0, "b"), 11) is BlobTransfer.Offer.Ready)
            assertTrue(transfer.offer(piece("old", 1, "c", total = 2), 12) is BlobTransfer.Offer.Pending)
            transfer.close()
        } finally {
            root.deleteRecursively()
        }
    }

    private fun piece(id: String, index: Int, text: String, total: Int = index + 1): BlobTransfer.Piece {
        return BlobTransfer.Piece(
            id = id,
            index = index,
            total = total,
            mime = "text/plain",
            part = Base64.getEncoder().encodeToString(text.toByteArray()),
        )
    }
}

class ByteStreamsTest {
    @Test
    fun copyStopsAtTheLimit() {
        val output = ByteArrayOutputStream()
        assertEquals(4L, ByteStreams.copy(ByteArrayInputStream("abcd".toByteArray()), output, 4))
        assertEquals("abcd", output.toString())
        try {
            ByteStreams.copy(ByteArrayInputStream("abcde".toByteArray()), ByteArrayOutputStream(), 4)
            throw AssertionError("expected limit")
        } catch (_: ByteStreams.LimitExceeded) {
        }
    }
}

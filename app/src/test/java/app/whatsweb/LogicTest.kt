package app.whatsweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.whatsweb.web.DesktopIdentity
import app.whatsweb.web.LinkRouter
import app.whatsweb.web.TelemetryBlock

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

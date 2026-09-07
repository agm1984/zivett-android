package com.zivett.app

import com.zivett.app.core.models.BlockResponse
import com.zivett.app.core.models.Conversation
import com.zivett.app.core.models.ConversationParticipant
import com.zivett.app.core.models.ConversationResponse
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.Message
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.features.customer.messages.ConversationModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Blocking from a thread: the model mirrors the server's filtered view
/// and drops live messages from anyone the viewer has blocked. Lockstep
/// with the iOS ConversationModelTests.
class ConversationModelTest {
    private fun message(id: Int, from: Int, body: String) = Message(id = id, senderId = from, sender = "Ravi Sandhu", body = body)

    private fun conversation(messages: List<Message>, blocked: Boolean) =
        ConversationResponse(Conversation(id = 77, messages = messages, participants = listOf(ConversationParticipant(9, "Ravi Sandhu", blocked))))

    @Test fun loadsParticipantsAndSplitsThemByBlockState() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5/conversation" to conversation(listOf(message(1, 9, "Hi")), blocked = false)))
        val model = ConversationModel(5, client)
        model.load()

        assertEquals(listOf(9), model.blockable.map { it.id })
        assertTrue(model.blocked.isEmpty())
        assertEquals(1, model.state.value?.size)
    }

    @Test fun blockingReloadsTheFilteredThread() = runTest {
        val client = PreviewApiClient(mapOf(
            "api/customer/jobs/5/conversation" to conversation(listOf(message(1, 9, "Rude")), blocked = false),
            "api/customer/jobs/5/conversation/block" to BlockResponse(listOf(ConversationParticipant(9, "Ravi Sandhu", blocked = true))),
        ))
        val model = ConversationModel(5, client)
        model.load()

        client.responses["api/customer/jobs/5/conversation"] = conversation(emptyList(), blocked = true)
        model.block(model.blockable[0])

        assertEquals(listOf(9), model.blocked.map { it.id })
        assertTrue(model.blockable.isEmpty())
        assertEquals(0, model.state.value?.size)
        assertTrue(client.sent.contains("POST api/customer/jobs/5/conversation/block"))
        assertNull(model.error)
    }

    @Test fun unblockingUsesTheDeleteRouteAndRestoresTheThread() = runTest {
        val client = PreviewApiClient(mapOf(
            "api/customer/jobs/5/conversation" to conversation(emptyList(), blocked = true),
            "api/customer/jobs/5/conversation/block/9" to BlockResponse(listOf(ConversationParticipant(9, "Ravi Sandhu", blocked = false))),
        ))
        val model = ConversationModel(5, client)
        model.load()
        assertEquals(1, model.blocked.size)

        client.responses["api/customer/jobs/5/conversation"] = conversation(listOf(message(1, 9, "Back")), blocked = false)
        model.unblock(model.blocked[0])

        assertTrue(model.blocked.isEmpty())
        assertEquals(1, model.state.value?.size)
        assertTrue(client.sent.contains("DELETE api/customer/jobs/5/conversation/block/9"))
    }

    @Test fun liveMessagesFromABlockedSenderAreDropped() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5/conversation" to conversation(emptyList(), blocked = true)))
        val model = ConversationModel(5, client).also { it.currentUserId = 3 }
        model.load()

        model.receive(RealtimeEvents.messageSent, """{"id":40,"conversation_id":77,"sender_id":9,"sender":"Ravi Sandhu","body":"Still here","created_at":"2026-09-06T10:00:00.000000Z"}""")
        model.receive(RealtimeEvents.messageSent, """{"id":41,"conversation_id":77,"sender_id":12,"sender":"Dana Cole","body":"Hello","created_at":"2026-09-06T10:00:01.000000Z"}""")

        assertEquals(listOf(41), model.state.value?.map { it.id })
    }

    @Test fun aBlockRejectionSurfacesTheServerMessage() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5/conversation" to conversation(emptyList(), blocked = false)))
        client.errors["api/customer/jobs/5/conversation/block"] = ApiError.Server(422, "You can only block someone on the other side of this conversation.")
        val model = ConversationModel(5, client)
        model.load()

        model.block(model.blockable[0])

        assertTrue(model.blocked.isEmpty())
        assertEquals("You can only block someone on the other side of this conversation.", model.error)
    }

    @Test fun everyAreaTargetsItsOwnRoutes() {
        assertEquals("api/company/jobs/5/conversation/block", JobArea.companyThread.blockUser(5, 9).path)
        assertEquals("api/company/jobs/5/conversation/block/9", JobArea.companyThread.unblockUser(5, 9).path)
        assertEquals(ApiRequest.Method.DELETE, JobArea.companyThread.unblockUser(5, 9).method)
        assertEquals("api/company/jobs/5/report", JobArea.companyThread.report(5, "conduct", "x").path)
        assertEquals("api/business/requests/5/conversation/block", JobArea.business.blockUser(5, 9).path)
    }

    @Test fun decodesAConversationWithAndWithoutParticipants() {
        val response = JsonCoding.json.decodeFromString<ConversationResponse>(
            """{"conversation":{"id":7,"messages":[{"id":1,"sender_id":9,"sender":"Ravi Sandhu","body":"Hi","mine":false,"read":true,"created_at":"2026-09-06T10:00:00.000000Z"}],"participants":[{"id":9,"name":"Ravi Sandhu","blocked":true}]}}""",
        )
        assertEquals(listOf(ConversationParticipant(9, "Ravi Sandhu", blocked = true)), response.conversation.participants)

        // Older servers omit the list — the thread still decodes.
        val legacy = JsonCoding.json.decodeFromString<ConversationResponse>("""{"conversation":{"id":7,"messages":[]}}""")
        assertTrue(legacy.conversation.participants.isEmpty())
    }
}

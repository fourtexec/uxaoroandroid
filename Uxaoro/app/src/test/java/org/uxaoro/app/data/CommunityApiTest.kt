package org.uxaoro.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Exercises the real Retrofit interface, not a separate test-only API. */
class CommunityApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: UxaoroApi
    private val gson = Gson()
    private val community = """{"id":"club","name":"Club","username":"new_link","is_member":true,"my_role":"owner","is_closed":false,"members_count":2}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create(gson))
            .build().create(UxaoroApi::class.java)
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun reply(body: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body)) }
    private fun request(method: String, path: String): RecordedRequest {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        return request
    }
    private fun body(request: RecordedRequest): JsonObject = gson.fromJson(request.body.readUtf8(), JsonObject::class.java)

    @Test
    fun patchesDetailsUsernameAndAccessWithWebFieldNames() = runBlocking {
        reply(community)
        val result = api.patchCommunity("club", CommunityPatch(name = "Club", username = "new_link", description = "", isClosed = false, whoCanPost = "admins", whoCanInvite = "editors"))
        val sent = body(request("PATCH", "/communities/club"))
        assertEquals("new_link", sent["username"].asString)
        assertEquals("", sent["description"].asString)
        assertFalse(sent["is_closed"].asBoolean)
        assertEquals("admins", sent["who_can_post"].asString)
        assertEquals("editors", sent["who_can_invite"].asString)
        assertEquals("new_link", result.username)
        assertEquals("owner", result.myRole)
    }

    @Test
    fun uploadsEditedPhotosAsMultipartFileAndParsesNullableUrls() = runBlocking {
        val file = MultipartBody.Part.createFormData("file", "photo.jpg", "edited-photo".toRequestBody("image/jpeg".toMediaType()))
        reply("""{"avatar_url":"/uploads/avatars/new.jpg","cover_url":null}""")
        val avatar = api.uploadCommunityAvatar("club", file)
        val avatarRequest = request("POST", "/communities/club/avatar")
        assertTrue(avatarRequest.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val multipart = avatarRequest.body.readUtf8()
        assertTrue(multipart.contains("name=\"file\"; filename=\"photo.jpg\""))
        assertTrue(multipart.contains("Content-Type: image/jpeg"))
        assertTrue(multipart.contains("edited-photo"))
        assertEquals("/uploads/avatars/new.jpg", avatar.avatarUrl)
        assertNull(avatar.coverUrl)

        reply("""{"avatar_url":null,"cover_url":"/uploads/covers/new.jpg"}""")
        val cover = api.uploadCommunityCover("club", file)
        request("POST", "/communities/club/cover")
        assertEquals("/uploads/covers/new.jpg", cover.coverUrl)
        assertNull(cover.avatarUrl)
    }

    @Test
    fun invitationsUseResolvedUserIdsAndReturnTheUpdatedCommunity() = runBlocking {
        reply(community)
        val updated = api.inviteCommunityMember("club", MemberIn("new-member"))
        assertEquals("new-member", body(request("POST", "/communities/club/invite"))["user_id"].asString)
        assertEquals(2, updated.membersCount)
    }

    @Test
    fun rolesRemovalAndDeletionUseTheirExistingServerRoutes() = runBlocking {
        reply("""{"ok":true}""")
        api.setCommunityRole("club", "member", RoleInCommunity("moderator"))
        assertEquals("moderator", body(request("POST", "/communities/club/members/member/role"))["role"].asString)
        reply("""{"ok":true}""")
        api.kickCommunityMember("club", "member")
        request("DELETE", "/communities/club/members/member")
        reply("""{"ok":true}""")
        api.deleteCommunity("club")
        request("DELETE", "/communities/club")
        Unit
    }

    @Test
    fun serverRejectionsPropagateInsteadOfBeingTreatedAsSaved() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Community username taken"}"""))
        val failure = runCatching { api.patchCommunity("club", CommunityPatch(username = "taken")) }.exceptionOrNull()
        assertTrue(failure is HttpException)
        assertEquals(409, (failure as HttpException).code())
        assertEquals("taken", body(request("PATCH", "/communities/club"))["username"].asString)
    }
}

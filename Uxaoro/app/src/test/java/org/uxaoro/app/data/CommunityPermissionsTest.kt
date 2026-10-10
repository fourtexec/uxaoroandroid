package org.uxaoro.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityPermissionsTest {
    private val roles = listOf("member", "editor", "moderator", "admin", "owner")
    private fun person(role: String, id: String = "target") = UserPublic(id = id, communityRole = role)

    @Test
    fun managementIsOnlyForCommunityOwnersAndAdmins() {
        (roles + listOf(null, "guest")).forEach { role ->
            assertEquals(role == "owner" || role == "admin", CommunityPermissions.canManage(role))
        }
    }

    @Test
    fun postingAndInvitationPoliciesMatchTheServerRoleHierarchy() {
        val allowed = mapOf(
            "members" to roles,
            "editors" to listOf("editor", "moderator", "admin", "owner"),
            "admins" to listOf("admin", "owner"),
        )
        for ((policy, permitted) in allowed) {
            for (role in roles + listOf(null, "unknown")) {
                val community = CommunityOut(isMember = true, myRole = role, whoCanPost = policy, whoCanInvite = policy)
                assertEquals("Post: $policy / $role", role in permitted, CommunityPermissions.canPost(community))
                assertEquals("Invite: $policy / $role", role in permitted, CommunityPermissions.canInvite(community))
                assertFalse(CommunityPermissions.canPost(community.copy(isMember = false)))
                assertFalse(CommunityPermissions.canInvite(community.copy(isMember = false)))
            }
        }
    }

    @Test
    fun closedCommunitiesStillAllowAuthorizedMembersToPostAndInvite() {
        val community = CommunityOut(isClosed = true, isMember = true, myRole = "owner")
        assertTrue(CommunityPermissions.canPost(community))
        assertTrue(CommunityPermissions.canInvite(community))
        assertFalse(CommunityPermissions.canPost(community.copy(whoCanPost = "unknown")))
        assertFalse(CommunityPermissions.canInvite(community.copy(whoCanInvite = "unknown")))
    }

    @Test
    fun onlyOwnersCanGrantAdminAndNobodyCanGrantOwnership() {
        assertEquals(listOf("member", "editor", "moderator", "admin"), CommunityPermissions.assignableRoles("owner"))
        assertEquals(listOf("member", "editor", "moderator"), CommunityPermissions.assignableRoles("admin"))
        listOf(null, "member", "editor", "moderator").forEach {
            assertTrue(CommunityPermissions.assignableRoles(it).isEmpty())
        }
    }

    @Test
    fun roleChangesExcludeTheOwnerAndTheCurrentUser() {
        for (role in roles) {
            assertFalse(CommunityPermissions.canChangeRole(role, "me", person("owner")))
            assertFalse(CommunityPermissions.canChangeRole(role, "me", person("member", id = "me")))
            assertFalse(CommunityPermissions.canChangeRole(role, null, person("member")))
        }
        assertTrue(CommunityPermissions.canChangeRole("owner", "me", person("admin")))
        // The API allows demotion of another admin, but not granting admin.
        assertTrue(CommunityPermissions.canChangeRole("admin", "me", person("admin")))
        assertFalse(CommunityPermissions.canChangeRole("moderator", "me", person("member")))
    }

    @Test
    fun administratorsCannotRemoveEqualOrHigherRankingMembers() {
        for (target in roles) {
            assertEquals(target != "owner", CommunityPermissions.canRemoveMember("owner", "me", person(target)))
            assertEquals(target in listOf("member", "editor", "moderator"), CommunityPermissions.canRemoveMember("admin", "me", person(target)))
            assertFalse(CommunityPermissions.canRemoveMember("member", "me", person(target)))
        }
        assertFalse(CommunityPermissions.canRemoveMember("owner", "me", person("member", id = "me")))
    }

    @Test
    fun patchIncludesAllWebSettingsWithoutSendingUnsetFields() {
        val gson = Gson()
        val patch = CommunityPatch(name = "Club", username = "club_link", description = "", isClosed = false, whoCanPost = "editors", whoCanInvite = "members")
        val json = gson.toJsonTree(patch).asJsonObject
        assertEquals(setOf("name", "username", "description", "is_closed", "who_can_post", "who_can_invite"), json.keySet())
        assertFalse(json["is_closed"].asBoolean)
        assertEquals("", json["description"].asString)
        val usernameOnly = gson.fromJson(gson.toJson(CommunityPatch(username = "new_link")), JsonObject::class.java)
        assertEquals(setOf("username"), usernameOnly.keySet())
        assertEquals("new_link", usernameOnly["username"].asString)
    }
}

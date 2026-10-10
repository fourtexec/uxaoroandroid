package org.uxaoro.app.data

/** UI gates matching the community policies in backend/uxaoro/{logic,extra}.py. */
object CommunityPermissions {
    val accessPolicies = listOf("members", "editors", "admins")
    private val roles = listOf("member", "editor", "moderator", "admin", "owner")

    fun canManage(role: String?): Boolean = role == "owner" || role == "admin"

    fun canPost(community: CommunityOut): Boolean = community.isMember &&
        policyAllows(community.whoCanPost, community.myRole)

    fun canInvite(community: CommunityOut): Boolean = community.isMember &&
        policyAllows(community.whoCanInvite, community.myRole)

    private fun policyAllows(policy: String, role: String?): Boolean {
        if (role !in roles) return false
        return when (policy) {
            "members" -> true
            "editors" -> role != "member"
            "admins" -> canManage(role)
            else -> false
        }
    }

    fun assignableRoles(myRole: String?): List<String> = when (myRole) {
        "owner" -> roles.filterNot { it == "owner" }
        "admin" -> roles.filterNot { it == "owner" || it == "admin" }
        else -> emptyList()
    }

    fun canChangeRole(myRole: String?, myId: String?, member: UserPublic): Boolean =
        canManage(myRole) && myId != null && myId != member.id && member.communityRole != "owner"

    fun canRemoveMember(myRole: String?, myId: String?, member: UserPublic): Boolean =
        canChangeRole(myRole, myId, member) &&
            (myRole == "owner" || roles.indexOf(myRole) > roles.indexOf(member.communityRole ?: "member"))
}

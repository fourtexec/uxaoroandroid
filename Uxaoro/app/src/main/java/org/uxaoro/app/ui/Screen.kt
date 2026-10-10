package org.uxaoro.app.ui

sealed class Screen {
    data object Boot : Screen()
    data object Login : Screen()
    data object Register : Screen()
    data object PreSettings : Screen()
    data object DevSettings : Screen()
    data object Main : Screen()
    data class Post(val id: String) : Screen()
    data class CreatePost(
        val communityId: String? = null,
        val wallUserId: String? = null,
        val originalPostId: String? = null,
    ) : Screen()
    data class User(val id: String) : Screen()
    data class Community(val id: String) : Screen()
    data object CreateCommunity : Screen()
    data class Chat(val id: String) : Screen()
    data object NewChat : Screen()
    data object NewGroup : Screen()
    data class ChatInfo(val id: String) : Screen()
    data object Search : Screen()
    data object Notifications : Screen()
    data object Settings : Screen()
    data object EditProfile : Screen()
    data object Admin : Screen()
    data object Blocked : Screen()
    data object Wallet : Screen()
    data object Premium : Screen()
    data object News : Screen()
    data class Explore(val tag: String = "") : Screen()
    data object CreateStory : Screen()
    data class StoryViewer(val userId: String) : Screen()
    data class CommunityMembers(val id: String) : Screen()
    data class CommunitySettings(val id: String) : Screen()
    data object Devices : Screen()
    /** App updates: version info, update channel, manual install (upd.manifest.json). */
    data object Updates : Screen()
    /** Sign-in history for this account (Security activity). */
    data object SecurityActivity : Screen()
    data object DeviceTransferNew : Screen()       // new device — shows pairing code + SAS, waits for seal
    data class DeviceTransferApprove(val transferId: String, val pairingCode: String) : Screen() // existing device — confirms & approves
}

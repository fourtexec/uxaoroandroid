package org.uxaoro.app.data

import com.google.gson.annotations.SerializedName

data class ApiError(val detail: String? = null, val error: String? = null)

data class VersionOut(
    val name: String = "Uxaoro",
    val version: String = "0.1.0",
    val api: String = "v1",
    val motd: String = "",
)

data class TokenPair(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("token_type") val tokenType: String = "bearer",
    val user: UserMe = UserMe(),
    @SerializedName("requires_2fa") val requires2fa: Boolean = false,
    @SerializedName("requires_email") val requiresEmail: Boolean = false,
    @SerializedName("pending_token") val pendingToken: String = "",
    val email: String = "",
)

data class TotpLoginIn(
    @SerializedName("pending_token") val pendingToken: String,
    val code: String,
)

data class EmailVerifyIn(
    @SerializedName("pending_token") val pendingToken: String,
    val code: String,
)

data class EmailPendingIn(
    @SerializedName("pending_token") val pendingToken: String,
)

data class TotpSetupOut(
    val secret: String = "",
    @SerializedName("otpauth_url") val otpauthUrl: String = "",
    @SerializedName("totp_enabled") val totpEnabled: Boolean = false,
)

data class TotpCodeIn(val code: String)

data class TotpDisableIn(val password: String, val code: String)

data class CaptchaVerifyIn(val token: String, val state: String)

data class CaptchaVerifyOut(
    @SerializedName("captcha_pass") val captchaPass: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0,
)

data class LoginIn(
    val login: String,
    val password: String,
    @SerializedName("captcha_pass") val captchaPass: String = "",
)

data class RegisterIn(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("dob") val dob: String = "",
    @SerializedName("captcha_pass") val captchaPass: String = "",
)

data class RefreshIn(@SerializedName("refresh_token") val refreshToken: String)

data class OAuthUnlinkIn(val provider: String)

data class OAuthUnlinkOut(
    val ok: Boolean = true,
    val user: UserMe = UserMe(),
)

data class OAuthStartOut(
    val url: String = "",
    val state: String = "",
    @SerializedName("redirect_uri") val redirectUri: String = "",
)

data class UserPublic(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    val bio: String = "",
    val status: String = "",
    @SerializedName("status_emoji") val statusEmoji: String = "",
    val pronouns: String = "",
    val website: String = "",
    @SerializedName("pinned_post_id") val pinnedPostId: String = "",
    @SerializedName("profile_views_count") val profileViewsCount: Int = 0,
    val city: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("cover_url") val coverUrl: String = "",
    val role: String = "user",
    val banned: Boolean = false,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("has_pubkey") val hasPubkey: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("friends_count") val friendsCount: Int = 0,
    @SerializedName("is_friend") val isFriend: Boolean = false,
    @SerializedName("friend_status") val friendStatus: String? = null,
    @SerializedName("is_blocked") val isBlocked: Boolean = false,
    val online: Boolean = false,
    val email: String = "",
    @SerializedName("community_role") val communityRole: String? = null,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    @SerializedName("who_can_message") val whoCanMessage: String = "everyone",
    val premium: Boolean = false,
    val badges: List<BadgeOut> = emptyList(),
    val customization: PremiumCustomization = PremiumCustomization(),
)

data class UserMe(
    val id: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String = "",
    val bio: String = "",
    val status: String = "",
    @SerializedName("status_emoji") val statusEmoji: String = "",
    val pronouns: String = "",
    val website: String = "",
    @SerializedName("pinned_post_id") val pinnedPostId: String = "",
    @SerializedName("profile_views_count") val profileViewsCount: Int = 0,
    val city: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("cover_url") val coverUrl: String = "",
    val role: String = "user",
    val banned: Boolean = false,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("has_pubkey") val hasPubkey: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("friends_count") val friendsCount: Int = 0,
    val email: String = "",
    val pubkey: String = "",
    val online: Boolean = false,
    @SerializedName("who_can_message") val whoCanMessage: String = "everyone",
    @SerializedName("who_can_invite_community") val whoCanInviteCommunity: String = "everyone",
    @SerializedName("who_can_see_last_seen") val whoCanSeeLastSeen: String = "everyone",
    @SerializedName("who_can_see_online") val whoCanSeeOnline: String = "everyone",
    @SerializedName("who_can_friend") val whoCanFriend: String = "everyone",
    @SerializedName("who_can_call") val whoCanCall: String = "everyone",
    @SerializedName("who_can_see_stories") val whoCanSeeStories: String = "everyone",
    @SerializedName("who_can_add_group") val whoCanAddGroup: String = "everyone",
    @SerializedName("totp_enabled") val totpEnabled: Boolean = false,
    @SerializedName("google_linked") val googleLinked: Boolean = false,
    @SerializedName("github_linked") val githubLinked: Boolean = false,
    @SerializedName("has_password") val hasPassword: Boolean = true,
    @SerializedName("security_alerts") val securityAlerts: Boolean = true,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    val premium: Boolean = false,
    val badges: List<BadgeOut> = emptyList(),
    val customization: PremiumCustomization = PremiumCustomization(),
) {
    fun asPublic() = UserPublic(
        id = id,
        username = username,
        displayName = displayName,
        bio = bio,
        status = status,
        statusEmoji = statusEmoji,
        pronouns = pronouns,
        website = website,
        pinnedPostId = pinnedPostId,
        profileViewsCount = profileViewsCount,
        city = city,
        avatarUrl = avatarUrl,
        coverUrl = coverUrl,
        role = role,
        banned = banned,
        lastSeen = lastSeen,
        hasPubkey = hasPubkey,
        createdAt = createdAt,
        friendsCount = friendsCount,
        email = email,
        online = online,
        balanceUx = balanceUx,
        premium = premium,
        badges = badges,
        customization = customization,
    )

    val isStaff: Boolean get() = role == "admin" || role == "moderator" || role == "owner"
    val isAdmin: Boolean get() = role == "admin" || role == "owner"
}

/** Server-owned Premium status and allowed cosmetics; these are not local theme preferences. */
data class PremiumCustomization(
    val glow: String = "",
    @SerializedName("name_style") val nameStyle: String = "",
    val badge: String = "",
    val bubble: String = "",
    @SerializedName("profile_theme") val profileTheme: String = "",
    @SerializedName("name_color") val nameColor: String = "",
    @SerializedName("bio_color") val bioColor: String = "",
)

data class PremiumOptions(
    val glow: List<String> = emptyList(),
    @SerializedName("name_style") val nameStyle: List<String> = emptyList(),
    val badge: List<String> = emptyList(),
    val bubble: List<String> = emptyList(),
    @SerializedName("profile_theme") val profileTheme: List<String> = emptyList(),
)

data class PremiumPlan(val days: Int = 0, val ux: Double = 0.0)

data class PremiumStatus(
    val active: Boolean = false,
    @SerializedName("premium_until") val premiumUntil: String = "",
    @SerializedName("stories_limit") val storiesLimit: Int = 4,
    @SerializedName("communities_limit") val communitiesLimit: Int = 40,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    val prices: List<PremiumPlan> = emptyList(),
    val options: PremiumOptions = PremiumOptions(),
    val customization: PremiumCustomization = PremiumCustomization(),
)

data class PremiumBuyIn(val days: Int)

data class PremiumBuyOut(
    val days: Int = 0,
    val ux: Double = 0.0,
    @SerializedName("premium_until") val premiumUntil: String = "",
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
)

data class CustomizationPatch(val customization: Map<String, String>)
data class CustomizationOut(val ok: Boolean = true, val customization: PremiumCustomization = PremiumCustomization())

data class UserUpdate(
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val status: String? = null,
    val city: String? = null,
    @SerializedName("status_emoji") val statusEmoji: String? = null,
    val pronouns: String? = null,
    val website: String? = null,
    val pubkey: String? = null,
    @SerializedName("who_can_message") val whoCanMessage: String? = null,
    @SerializedName("who_can_invite_community") val whoCanInviteCommunity: String? = null,
    @SerializedName("who_can_see_last_seen") val whoCanSeeLastSeen: String? = null,
    @SerializedName("who_can_see_online") val whoCanSeeOnline: String? = null,
    @SerializedName("who_can_friend") val whoCanFriend: String? = null,
    @SerializedName("who_can_call") val whoCanCall: String? = null,
    @SerializedName("who_can_see_stories") val whoCanSeeStories: String? = null,
    @SerializedName("who_can_add_group") val whoCanAddGroup: String? = null,
    @SerializedName("security_alerts") val securityAlerts: Boolean? = null,
)

/** One recorded sign-in, as returned by GET /me/security/activity. */
data class LoginEvent(
    val id: String = "",
    val at: String = "",
    val method: String = "",
    val device: String = "",
    val platform: String = "",
    @SerializedName("ip_address") val ipAddress: String = "",
    @SerializedName("new_device") val newDevice: Boolean = false,
)

data class SecurityActivityOut(
    val items: List<LoginEvent> = emptyList(),
    @SerializedName("alerts_enabled") val alertsEnabled: Boolean = true,
)

data class PasswordChange(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String,
)

data class ReactionOut(
    val emoji: String = "",
    val count: Int = 0,
    val me: Boolean = false,
)

data class ReactIn(val emoji: String = "")

data class ReactOut(
    val ok: Boolean = true,
    @SerializedName("my_reaction") val myReaction: String? = null,
    val reactions: List<ReactionOut> = emptyList(),
)

data class RepostIn(val text: String = "")

data class PollOptionOut(
    val id: String = "",
    val text: String = "",
    val votes: Int = 0,
    val voted: Boolean = false,
    val pct: Double = 0.0,
)

data class PollOut(
    val id: String = "",
    val question: String = "",
    val multiple: Boolean = false,
    val anonymous: Boolean = false,
    @SerializedName("total_votes") val totalVotes: Int = 0,
    @SerializedName("total_voters") val totalVoters: Int = 0,
    val voted: Boolean = false,
    val options: List<PollOptionOut> = emptyList(),
)

data class PollVoteIn(
    @SerializedName("option_id") val optionId: String,
)

data class PollVoteOut(
    val ok: Boolean = true,
    val poll: PollOut? = null,
    val post: PostOut? = null,
)

data class PostTipIn(
    @SerializedName("amount_ux") val amountUx: Double,
    val note: String = "",
)

data class PostTipOut(
    val ok: Boolean = true,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    @SerializedName("tips_ux") val tipsUx: Double = 0.0,
    @SerializedName("tips_count") val tipsCount: Int = 0,
    val post: PostOut? = null,
)

data class PinPostOut(
    val ok: Boolean = true,
    val pinned: Boolean = false,
    @SerializedName("pinned_post_id") val pinnedPostId: String = "",
    val post: PostOut? = null,
)

data class PostOut(
    val id: String = "",
    val author: UserPublic? = null,
    @SerializedName("community_id") val communityId: String? = null,
    @SerializedName("community_name") val communityName: String? = null,
    @SerializedName("wall_user_id") val wallUserId: String? = null,
    val text: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("video_url") val videoUrl: String = "",
    @SerializedName("audio_url") val audioUrl: String = "",
    @SerializedName("media_type") val mediaType: String = "",
    @SerializedName("media_mime") val mediaMime: String = "",
    @SerializedName("original_post") val originalPost: PostOut? = null,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    val liked: Boolean = false,
    val reactions: List<ReactionOut> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String? = null,
    @SerializedName("reposts_count") val repostsCount: Int = 0,
    val saved: Boolean = false,
    val poll: PollOut? = null,
    @SerializedName("tips_ux") val tipsUx: Double = 0.0,
    @SerializedName("tips_count") val tipsCount: Int = 0,
    @SerializedName("views_count") val viewsCount: Int = 0,
    val hashtags: List<String> = emptyList(),
    @SerializedName("is_pinned") val isPinned: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("edited_at") val editedAt: String? = null,
)

data class CommentOut(
    val id: String = "",
    val author: UserPublic? = null,
    val text: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class CommentCreate(val text: String)

data class CommunityOut(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val slug: String = "",
    val description: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("cover_url") val coverUrl: String = "",
    @SerializedName("owner_id") val ownerId: String = "",
    @SerializedName("is_closed") val isClosed: Boolean = false,
    @SerializedName("members_count") val membersCount: Int = 0,
    @SerializedName("is_member") val isMember: Boolean = false,
    @SerializedName("my_role") val myRole: String? = null,
    @SerializedName("who_can_post") val whoCanPost: String = "members",
    @SerializedName("who_can_invite") val whoCanInvite: String = "admins",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class CommunityCreate(
    val name: String,
    val username: String = "",
    val description: String = "",
    @SerializedName("is_closed") val isClosed: Boolean = false,
)

data class ChatOut(
    val id: String = "",
    @SerializedName("is_group") val isGroup: Boolean = false,
    val title: String = "",
    val members: List<UserPublic> = emptyList(),
    @SerializedName("last_message") val lastMessage: MessageOut? = null,
    val unread: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("owner_id") val ownerId: String = "",
    @SerializedName("members_can_invite") val membersCanInvite: Boolean = true,
    @SerializedName("my_role") val myRole: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    @SerializedName("peer_last_read") val peerLastRead: String? = null,
    /**
     * `"saved"` for the personal Saved Messages chat — a private E2EE chat with
     * yourself that the server creates on first access (`GET /chats/saved`).
     * Mirrors the web client, where saved posts and Cloud Notes live now.
     */
    val kind: String = "",
)

data class InboxBadge(
    @SerializedName("chats_unread") val chatsUnread: Int = 0,
    val notifications: Int = 0,
)

data class ChatInviteOut(
    val token: String = "",
    val url: String = "",
    @SerializedName("chat_id") val chatId: String = "",
)

data class FlagIn(val pinned: Boolean? = null, val muted: Boolean? = null)

data class IceServerOut(
    val urls: List<String> = emptyList(),
    val username: String = "",
    val credential: String = "",
)

data class IceOut(
    @SerializedName("ice_servers") val iceServers: List<IceServerOut> = emptyList(),
)

data class ChatCreate(
    @SerializedName("user_ids") val userIds: List<String>,
    val title: String = "",
)

data class MessageOut(
    val id: String = "",
    @SerializedName("chat_id") val chatId: String = "",
    @SerializedName("sender_id") val senderId: String = "",
    @SerializedName("msg_type") val msgType: String = "text",
    val ciphertext: String = "",
    val nonce: String = "",
    @SerializedName("keys_json") val keysJson: String = "{}",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("media_url") val mediaUrl: String = "",
    @SerializedName("media_type") val mediaType: String = "",
    @SerializedName("media_mime") val mediaMime: String = "",
    @SerializedName("media_nonce") val mediaNonce: String = "",
    @SerializedName("sticker_id") val stickerId: String = "",
    val sticker: StickerOut? = null,
    val reactions: List<ReactionOut> = emptyList(),
    @SerializedName("my_reaction") val myReaction: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class MessageCreate(
    @SerializedName("msg_type") val msgType: String = "text",
    val ciphertext: String = "",
    val nonce: String = "",
    @SerializedName("keys_json") val keysJson: String = "{}",
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("media_url") val mediaUrl: String = "",
    @SerializedName("media_type") val mediaType: String = "",
    @SerializedName("media_mime") val mediaMime: String = "",
    @SerializedName("media_nonce") val mediaNonce: String = "",
    @SerializedName("sticker_id") val stickerId: String = "",
)

data class MediaUploadOut(
    val url: String = "",
    val kind: String = "",
    val mime: String = "",
    val size: Long = 0,
)

data class StickerOut(
    val id: String = "",
    @SerializedName("pack_id") val packId: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    val emoji: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class StickerPackOut(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    @SerializedName("owner_id") val ownerId: String = "",
    val stickers: List<StickerOut> = emptyList(),
    val count: Int = 0,
)

data class StickerFavListOut(
    val items: List<StickerOut> = emptyList(),
)

data class StickerFavIn(
    @SerializedName("sticker_id") val stickerId: String,
)

data class NotificationOut(
    val id: String = "",
    val ntype: String = "",
    val title: String = "",
    val body: String = "",
    val payload: String = "{}",
    val read: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class NotificationList(
    val unread: Int = 0,
    val items: List<NotificationOut> = emptyList(),
)

data class ReportIn(
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_id") val targetId: String,
    val reason: String,
    val details: String = "",
)

data class ReportOut(
    val id: String = "",
    @SerializedName("reporter_id") val reporterId: String = "",
    @SerializedName("target_type") val targetType: String = "",
    @SerializedName("target_id") val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resolved_by") val resolvedBy: String? = null,
)

data class RoleIn(val role: String)
data class BanIn(val banned: Boolean, val reason: String = "")


data class ServerSettingsOut(
    @SerializedName("registration_open") val registrationOpen: Boolean = true,
    val maintenance: Boolean = false,
    val motd: String = "",
)

data class StatsOut(
    val users: Int = 0,
    val posts: Int = 0,
    val communities: Int = 0,
    val messages: Int = 0,
    @SerializedName("reports_open") val reportsOpen: Int = 0,
    val online: Int = 0,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
)

data class UxTx(
    val id: String = "",
    @SerializedName("user_id") val userId: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    @SerializedName("ref_type") val refType: String = "",
    @SerializedName("ref_id") val refId: String = "",
    val details: String = "",
    @SerializedName("actor_id") val actorId: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class PageUxTx(
    val items: List<UxTx> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class DailyCheckinOut(
    val streak: Int = 0,
    @SerializedName("next_streak") val nextStreak: Int = 1,
    @SerializedName("claimed_today") val claimedToday: Boolean = false,
    @SerializedName("reward_ux") val rewardUx: Double = 5.0,
    @SerializedName("premium_multiplier") val premiumMultiplier: Int = 1,
    @SerializedName("total_claimed_ux") val totalClaimedUx: Double = 0.0,
    @SerializedName("last_claim_date") val lastClaimDate: String = "",
)

data class DailyClaimOut(
    val ok: Boolean = true,
    @SerializedName("reward_ux") val rewardUx: Double = 0.0,
    val streak: Int = 1,
    @SerializedName("claimed_today") val claimedToday: Boolean = true,
    @SerializedName("total_claimed_ux") val totalClaimedUx: Double = 0.0,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    val daily: DailyCheckinOut = DailyCheckinOut(),
)

data class UxWallet(
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
    val rate: Double = 0.1,
    val asset: String = "USDT",
    @SerializedName("cryptobot_enabled") val cryptobotEnabled: Boolean = false,
    @SerializedName("telegram_bot_username") val telegramBotUsername: String = "",
    @SerializedName("telegram_linked") val telegramLinked: Boolean = false,
    val daily: DailyCheckinOut = DailyCheckinOut(),
    val transactions: PageUxTx = PageUxTx(),
)

data class UxGiftIn(
    @SerializedName("user_id") val userId: String? = null,
    val username: String? = null,
    val amount: Double,
    val message: String = "",
)

data class UxBuyIn(val amount: Double)

data class UxGrantIn(val amount: Double, val reason: String = "")

data class UxBalanceOut(
    val ok: Boolean = true,
    @SerializedName("balance_ux") val balanceUx: Double = 0.0,
)


data class UxLinkOut(val code: String = "", @SerializedName("bot_username") val botUsername: String = "")

data class UxPurchaseOut(
    val id: String = "",
    @SerializedName("invoice_id") val invoiceId: String = "",
    @SerializedName("amount_ux") val amountUx: Double = 0.0,
    val currency: String = "",
    @SerializedName("pay_amount") val payAmount: Double = 0.0,
    @SerializedName("pay_url") val payUrl: String = "",
    @SerializedName("bot_invoice_url") val botInvoiceUrl: String = "",
    val status: String = "pending",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("paid_at") val paidAt: String? = null,
)


data class PagePosts(
    val items: List<PostOut> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class PageUsers(
    val items: List<UserPublic> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class PageCommunities(
    val items: List<CommunityOut> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class PageMessages(
    val items: List<MessageOut> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class SearchOut(
    val users: List<UserPublic> = emptyList(),
    val communities: List<CommunityOut> = emptyList(),
)

data class UrlOut(
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
)

data class LikeOut(val liked: Boolean = false, @SerializedName("likes_count") val likesCount: Int = 0)

data class KeyBackupIn(
    val version: Int = 1,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int = 600000,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
    @SerializedName("pubkey_fingerprint") val pubkeyFingerprint: String = "",
)

data class KeyBackupOut(
    val exists: Boolean = false,
    val version: Int = 0,
    val kdf: String = "",
    val iterations: Int = 0,
    val salt: String = "",
    val nonce: String = "",
    val ciphertext: String = "",
    @SerializedName("pubkey_fingerprint") val pubkeyFingerprint: String = "",
    @SerializedName("account_pubkey_fingerprint") val accountPubkeyFingerprint: String = "",
    @SerializedName("matches_account_key") val matchesAccountKey: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class PubkeyOut(
    @SerializedName("user_id") val userId: String = "",
    val pubkey: String = "",
)

data class OkOut(val ok: Boolean = true)

data class AuditItem(
    val id: String = "",
    @SerializedName("actor_id") val actorId: String = "",
    val action: String = "",
    @SerializedName("target_type") val targetType: String = "",
    @SerializedName("target_id") val targetId: String = "",
    val details: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
)

data class PageAudit(
    val items: List<AuditItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
)

data class WsEvent(
    val event: String = "",
    val data: com.google.gson.JsonElement? = null,
)

data class StoryOut(
    val id: String = "",
    @SerializedName("author_id") val authorId: String = "",
    val author: UserPublic? = null,
    @SerializedName("image_url") val imageUrl: String = "",
    @SerializedName("media_type") val mediaType: String = "image",
    val caption: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    val viewed: Boolean = false,
    val views: Int = 0,
)

data class StoryGroup(
    val user: UserPublic? = null,
    val unseen: Boolean = false,
    val stories: List<StoryOut> = emptyList(),
)

data class StoryFeed(val items: List<StoryGroup> = emptyList())

data class UsernameIn(val username: String)

data class RoleInCommunity(val role: String)

data class CommunityPatch(
    val name: String? = null,
    val username: String? = null,
    val description: String? = null,
    @SerializedName("is_closed") val isClosed: Boolean? = null,
    @SerializedName("who_can_post") val whoCanPost: String? = null,
    @SerializedName("who_can_invite") val whoCanInvite: String? = null,
)

data class ChatPatch(
    val title: String? = null,
    @SerializedName("members_can_invite") val membersCanInvite: Boolean? = null,
)

data class MemberIn(@SerializedName("user_id") val userId: String)


data class CachedMsg(
    val id: String = "",
    @SerializedName("chat_id") val chatId: String = "",
    @SerializedName("sender_id") val senderId: String = "",
    val text: String = "",
    val failed: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    val raw: MessageOut? = null,
)

data class BadgeOut(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    @SerializedName("image_webp") val imageWebp: String = "",
    val applied: Boolean = true,
)

data class BadgeToggleOut(
    val ok: Boolean = false,
    @SerializedName("badge_id") val badgeId: String = "",
    val applied: Boolean = true,
)

data class PromoOut(
    val code: String = "",
    @SerializedName("badge_id") val badgeId: String = "",
    @SerializedName("badge_name") val badgeName: String = "",
    @SerializedName("used_by") val usedBy: String = "",
    @SerializedName("used_at") val usedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class BadgeListOut(
    val items: List<BadgeOut> = emptyList(),
    val promocodes: List<PromoOut> = emptyList(),
)

data class BadgeCreateIn(
    val name: String,
    val description: String = "",
    @SerializedName("image_webp") val imageWebp: String = "/uploads/badges/default.webp",
)

data class PromoCreateIn(
    val code: String,
    @SerializedName("badge_id") val badgeId: String,
)

data class BadgeApplyIn(val code: String)

data class BadgeApplyOut(
    val ok: Boolean = false,
    @SerializedName("badge_id") val badgeId: String = "",
)

data class SaleItemsSpec(
    val premium: Boolean = false,
    @SerializedName("premium_days") val premiumDays: Int = 30,
    @SerializedName("price_ux") val priceUx: Double = 0.0,
    val badges: List<String> = emptyList(),
)

data class SaleOut(
    val id: String = "",
    val name: String = "",
    @SerializedName("price_ux") val priceUx: Double = 0.0,
    val items: com.google.gson.JsonElement? = null,
    @SerializedName("users_rule") val usersRule: String = "all",
    val users: List<String> = emptyList(),
    val once: Boolean = false,
    @SerializedName("starts_at") val startsAt: String? = null,
    @SerializedName("ends_at") val endsAt: String? = null,
    @SerializedName("active_now") val activeNow: Boolean = true,
    @SerializedName("applied_by_me") val appliedByMe: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class SaleListOut(
    val items: List<SaleOut> = emptyList(),
)

data class SaleCreateIn(
    val name: String,
    @SerializedName("price_ux") val priceUx: Double = 0.0,
    @SerializedName("premium_days") val premiumDays: Int = 0,
    val items: SaleItemsSpec = SaleItemsSpec(),
    @SerializedName("users_rule") val usersRule: String = "all",
    val users: List<String> = emptyList(),
    val once: Boolean = false,
    @SerializedName("starts_at") val startsAt: String = "",
    @SerializedName("ends_at") val endsAt: String = "",
)

data class SaleApplyIn(
    @SerializedName("sale_id") val saleId: String,
)

data class SessionItem(
    val id: String = "",
    val device: String = "Uxaoro Client",
    @SerializedName("user_agent") val userAgent: String = "",
    @SerializedName("ip_address") val ipAddress: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
    @SerializedName("is_latest") val isLatest: Boolean = false,
)

data class SessionListOut(
    val items: List<SessionItem> = emptyList(),
)

// ---------- E2EE Device linking ----------

data class DeviceOut(
    val id: String = "",
    val label: String = "",
    val platform: String = "",
    @SerializedName("is_current") val isCurrent: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
)

data class DeviceRegisterIn(
    val label: String = "",
    val platform: String = "android",
    @SerializedName("device_pubkey") val devicePubkey: String = "",
)

data class DeviceRenameIn(val label: String)

data class KeyRotationOut(
    @SerializedName("rotation_id") val rotationId: String = "",
    val status: String = "none",
    @SerializedName("claim_token") val claimToken: String = "",
    @SerializedName("old_pubkey") val oldPubkey: String = "",
    @SerializedName("new_pubkey") val newPubkey: String = "",
    val ciphertext: String = "",
    val nonce: String = "",
    @SerializedName("keys_json") val keysJson: String = "{}",
)

data class KeyRotationCompleteIn(
    @SerializedName("rotation_id") val rotationId: String,
    @SerializedName("claim_token") val claimToken: String,
    @SerializedName("new_pubkey") val newPubkey: String,
    val ciphertext: String,
    val nonce: String,
    @SerializedName("keys_json") val keysJson: String,
)

data class KeyRotationAckIn(@SerializedName("rotation_id") val rotationId: String)

data class TransferInitiateIn(
    val label: String = "New device",
    val platform: String = "",
    @SerializedName("ephemeral_pubkey") val ephemeralPubkey: String,
)

data class TransferApproveIn(
    @SerializedName("approving_device_id") val approvingDeviceId: String = "",
    @SerializedName("approver_ephemeral_pubkey") val approverEphemeralPubkey: String = "",
    @SerializedName("encrypted_blob") val encryptedBlob: String,
    @SerializedName("blob_nonce") val blobNonce: String,
)

data class TransferCompleteIn(
    @SerializedName("device_pubkey") val devicePubkey: String = "",
)

data class TransferOut(
    @SerializedName("transfer_id") val transferId: String = "",
    @SerializedName("pairing_code") val pairingCode: String = "",
    val status: String = "pending",
    @SerializedName("new_device_label") val newDeviceLabel: String = "",
    @SerializedName("new_device_platform") val newDevicePlatform: String = "",
    @SerializedName("new_ephemeral_pubkey") val newEphemeralPubkey: String = "",
    @SerializedName("approver_ephemeral_pubkey") val approverEphemeralPubkey: String = "",
    @SerializedName("encrypted_blob") val encryptedBlob: String = "",
    @SerializedName("blob_nonce") val blobNonce: String = "",
    @SerializedName("approved_by_device_id") val approvedByDeviceId: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
)



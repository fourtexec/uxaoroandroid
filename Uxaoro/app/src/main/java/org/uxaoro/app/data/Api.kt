package org.uxaoro.app.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.uxaoro.app.net.TlsGuard
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit

interface UxaoroApi {
    @GET("version")
    suspend fun version(): VersionOut

    @POST("auth/register")
    suspend fun register(@Body body: RegisterIn): TokenPair

    @POST("auth/register/verify")
    suspend fun registerVerify(@Body body: EmailVerifyIn): TokenPair

    @POST("auth/register/resend")
    suspend fun registerResend(@Body body: EmailPendingIn): OkOut

    /**
     * Exchange a raw Turnstile response for the server's opaque, IP-bound
     * CAPTCHA pass. The app calls this itself (rather than letting the
     * browser-hosted CAPTCHA page do it) so the pass is issued to the same
     * network path/IP that immediately consumes it at login/register.
     */
    @POST("auth/captcha/verify")
    suspend fun captchaVerify(@Body body: CaptchaVerifyIn): CaptchaVerifyOut

    @POST("auth/login")
    suspend fun login(@Body body: LoginIn): TokenPair

    @POST("auth/login/2fa")
    suspend fun login2fa(@Body body: TotpLoginIn): TokenPair

    @POST("auth/2fa/setup")
    suspend fun totpSetup(): TotpSetupOut

    @POST("auth/2fa/enable")
    suspend fun totpEnable(@Body body: TotpCodeIn): OkOut

    @POST("auth/2fa/disable")
    suspend fun totpDisable(@Body body: TotpDisableIn): OkOut

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshIn): TokenPair

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshIn): OkOut

    @GET("auth/me")
    suspend fun me(): UserMe

    @POST("auth/password")
    suspend fun changePassword(@Body body: PasswordChange): OkOut

    @POST("auth/oauth/unlink")
    suspend fun unlinkOAuth(@Body body: OAuthUnlinkIn): OAuthUnlinkOut

    @GET("auth/{provider}")
    suspend fun startOAuth(
        @Path("provider") provider: String,
        @Query("format") format: String,
        @Query("redirect_to") redirectTo: String,
        @Query("mode") mode: String,
    ): OAuthStartOut

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserPublic

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UserUpdate): UserMe

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UrlOut

    @Multipart
    @POST("users/me/cover")
    suspend fun uploadCover(@Part file: MultipartBody.Part): UrlOut

    @GET("users/{id}/pubkey")
    suspend fun pubkey(@Path("id") id: String): PubkeyOut

    @PUT("users/me/pubkey")
    suspend fun putPubkey(@Body body: Map<String, String>): OkOut

    /** Password-encrypted private-key backup: the server only ever sees the blob. */
    @GET("e2ee/backup")
    suspend fun keyBackup(@Query("meta") meta: Int? = null): KeyBackupOut

    @PUT("e2ee/backup")
    suspend fun putKeyBackup(@Body body: KeyBackupIn): OkOut

    @DELETE("e2ee/backup")
    suspend fun deleteKeyBackup(): OkOut

    @POST("users/{id}/block")
    suspend fun block(@Path("id") id: String): OkOut

    @DELETE("users/{id}/block")
    suspend fun unblock(@Path("id") id: String): OkOut

    @GET("users/me/blocked")
    suspend fun blocked(): List<UserPublic>

    @GET("users/{id}/posts")
    suspend fun userPosts(@Path("id") id: String, @Query("page") page: Int = 1): PagePosts

    @GET("friends")
    suspend fun friends(): List<UserPublic>

    @GET("friends/requests")
    suspend fun friendRequests(): List<UserPublic>

    @GET("friends/suggestions")
    suspend fun suggestions(): List<UserPublic>

    @POST("friends/request/{id}")
    suspend fun friendRequest(@Path("id") id: String): OkOut

    @POST("friends/accept/{id}")
    suspend fun friendAccept(@Path("id") id: String): OkOut

    @POST("friends/decline/{id}")
    suspend fun friendDecline(@Path("id") id: String): OkOut

    @DELETE("friends/{id}")
    suspend fun unfriend(@Path("id") id: String): OkOut

    @GET("feed")
    suspend fun feed(@Query("page") page: Int = 1): PagePosts

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: String): PostOut

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: String): OkOut

    @POST("posts/{id}/like")
    suspend fun like(@Path("id") id: String): LikeOut

    @GET("posts/{id}/comments")
    suspend fun comments(@Path("id") id: String): List<CommentOut>

    @POST("posts/{id}/comments")
    suspend fun addComment(@Path("id") id: String, @Body body: CommentCreate): CommentOut

    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part("text") text: RequestBody,
        @Part("community_id") communityId: RequestBody?,
        @Part("wall_user_id") wallUserId: RequestBody?,
        @Part("original_post_id") originalPostId: RequestBody?,
        @Part image: MultipartBody.Part?,
        @Part video: MultipartBody.Part? = null,
        @Part audio: MultipartBody.Part? = null,
        @Part("poll_question") pollQuestion: RequestBody? = null,
        @Part("poll_options") pollOptions: RequestBody? = null,
        @Part("poll_multiple") pollMultiple: RequestBody? = null,
    ): PostOut

    /** E2EE chat media: `file` carries ciphertext, enc="1". */
    @Multipart
    @POST("media/upload")
    suspend fun uploadMediaEnc(
        @Part file: MultipartBody.Part,
        @Part("subdir") subdir: RequestBody? = null,
        @Part("enc") enc: RequestBody = "1".toPlainBody(),
        @Part("kind") kind: RequestBody? = null,
        @Part("mime") mime: RequestBody? = null,
    ): MediaUploadOut

    /** Raw download of encrypted chat media blobs. */
    @Streaming
    @GET
    suspend fun fetchRaw(@Url url: String): ResponseBody

    @GET("stickers/packs")
    suspend fun stickerPacks(): List<StickerPackOut>

    @GET("stickers/favorites")
    suspend fun favoriteStickers(): StickerFavListOut

    @POST("stickers/favorites")
    suspend fun addFavoriteSticker(@Body body: StickerFavIn): OkOut

    @DELETE("stickers/favorites/{id}")
    suspend fun removeFavoriteSticker(@Path("id") id: String): OkOut

    @Multipart
    @POST("stickers/create")
    suspend fun createSticker(
        @Part file: MultipartBody.Part,
        @Part("emoji") emoji: RequestBody? = null,
        @Part("title") title: RequestBody? = null,
    ): StickerOut

    @GET("communities")
    suspend fun communities(
        @Query("q") q: String = "",
        @Query("mine") mine: Boolean = false,
        @Query("page") page: Int = 1,
    ): PageCommunities

    @POST("communities")
    suspend fun createCommunity(@Body body: CommunityCreate): CommunityOut

    @GET("communities/{id}")
    suspend fun getCommunity(@Path("id") id: String): CommunityOut

    @POST("communities/{id}/join")
    suspend fun joinCommunity(@Path("id") id: String): CommunityOut

    @POST("communities/{id}/leave")
    suspend fun leaveCommunity(@Path("id") id: String): OkOut

    @GET("communities/{id}/members")
    suspend fun communityMembers(@Path("id") id: String): List<UserPublic>

    @GET("communities/{id}/posts")
    suspend fun communityPosts(@Path("id") id: String, @Query("page") page: Int = 1): PagePosts

    @GET("chats")
    suspend fun chats(): List<ChatOut>

    /**
     * The user's Saved Messages: created on first access, then the same chat
     * forever. It is an ordinary E2EE chat with yourself, so sending a note (or
     * forwarding a message into it) needs no endpoint of its own.
     */
    @GET("chats/saved")
    suspend fun savedChat(): ChatOut

    @POST("chats")
    suspend fun createChat(@Body body: ChatCreate): ChatOut

    @GET("chats/{id}")
    suspend fun getChat(@Path("id") id: String): ChatOut

    @GET("chats/{id}/invite-link")
    suspend fun chatInviteLink(@Path("id") id: String): ChatInviteOut

    @POST("chats/{id}/invite-link/revoke")
    suspend fun revokeChatInviteLink(@Path("id") id: String): ChatInviteOut

    @GET("chats/{id}/messages")
    suspend fun messages(@Path("id") id: String, @Query("page") page: Int = 1): PageMessages

    @POST("chats/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: MessageCreate): MessageOut

    @GET("notifications")
    suspend fun notifications(): NotificationList

    @POST("notifications/read-all")
    suspend fun readAllNotifications(): OkOut

    @GET("search")
    suspend fun search(@Query("q") q: String): SearchOut

    @POST("reports")
    suspend fun report(@Body body: ReportIn): OkOut

    @GET("admin/stats")
    suspend fun adminStats(): StatsOut

    @GET("admin/users")
    suspend fun adminUsers(
        @Query("q") q: String = "",
        @Query("role") role: String = "",
        @Query("banned") banned: String = "",
        @Query("page") page: Int = 1,
    ): PageUsers

    @POST("admin/users/{id}/role")
    suspend fun setRole(@Path("id") id: String, @Body body: RoleIn): OkOut

    @POST("admin/users/{id}/ban")
    suspend fun ban(@Path("id") id: String, @Body body: BanIn): OkOut

    @GET("admin/posts")
    suspend fun adminPosts(@Query("q") q: String = "", @Query("page") page: Int = 1): PagePosts

    @DELETE("admin/posts/{id}")
    suspend fun adminDeletePost(@Path("id") id: String): OkOut

    @GET("admin/reports")
    suspend fun adminReports(@Query("status") status: String = "open"): List<ReportOut>

    @POST("admin/reports/{id}/resolve")
    suspend fun resolveReport(@Path("id") id: String, @Body body: Map<String, String>): OkOut

    @GET("admin/settings")
    suspend fun adminSettings(): ServerSettingsOut

    @POST("admin/settings")
    suspend fun setAdminSettings(@Body body: Map<String, @JvmSuppressWildcards Any>): ServerSettingsOut

    @GET("admin/audit")
    suspend fun audit(@Query("page") page: Int = 1): PageAudit

    // ---- Uxaoro Premium (server-priced in UX; never grant access locally) ----

    @GET("premium/status")
    suspend fun premiumStatus(): PremiumStatus

    @POST("premium/buy")
    suspend fun premiumBuy(@Body body: PremiumBuyIn): PremiumBuyOut

    @PATCH("users/me/customization")
    suspend fun setCustomization(@Body body: CustomizationPatch): CustomizationOut

    // ---- UX digital currency ----

    @GET("ux/wallet")
    suspend fun uxWallet(): UxWallet

    @POST("ux/gift")
    suspend fun uxGift(@Body body: UxGiftIn): UxBalanceOut

    @POST("ux/link/code")
    suspend fun uxLinkCode(): UxLinkOut

    @POST("ux/buy")
    suspend fun uxBuy(@Body body: UxBuyIn): UxPurchaseOut

    @GET("ux/purchases/{id}/status")
    suspend fun uxPurchaseStatus(@Path("id") id: String): UxPurchaseOut

    @POST("admin/users/{id}/ux")
    suspend fun adminGrantUx(@Path("id") id: String, @Body body: UxGrantIn): UxBalanceOut

    @POST("friends/request-by-username")
    suspend fun friendRequestByUsername(@Body body: UsernameIn): OkOut

    @GET("stories")
    suspend fun stories(): StoryFeed

    @Multipart
    @POST("stories")
    suspend fun createStory(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
    ): StoryOut

    @POST("stories/{id}/view")
    suspend fun viewStory(@Path("id") id: String): OkOut

    @DELETE("stories/{id}")
    suspend fun deleteStory(@Path("id") id: String): OkOut

    @PATCH("communities/{id}")
    suspend fun patchCommunity(@Path("id") id: String, @Body body: CommunityPatch): CommunityOut

    @Multipart
    @POST("communities/{id}/avatar")
    suspend fun uploadCommunityAvatar(@Path("id") id: String, @Part file: MultipartBody.Part): UrlOut

    @Multipart
    @POST("communities/{id}/cover")
    suspend fun uploadCommunityCover(@Path("id") id: String, @Part file: MultipartBody.Part): UrlOut

    @POST("communities/{id}/invite")
    suspend fun inviteCommunityMember(@Path("id") id: String, @Body body: MemberIn): CommunityOut

    @DELETE("communities/{id}")
    suspend fun deleteCommunity(@Path("id") id: String): OkOut

    @POST("communities/{id}/members/{userId}/role")
    suspend fun setCommunityRole(@Path("id") id: String, @Path("userId") userId: String, @Body body: RoleInCommunity): OkOut

    @DELETE("communities/{id}/members/{userId}")
    suspend fun kickCommunityMember(@Path("id") id: String, @Path("userId") userId: String): OkOut

    @PATCH("chats/{id}")
    suspend fun patchChat(@Path("id") id: String, @Body body: ChatPatch): ChatOut

    @POST("chats/{id}/members")
    suspend fun addChatMember(@Path("id") id: String, @Body body: MemberIn): ChatOut

    @DELETE("chats/{id}/members/{userId}")
    suspend fun removeChatMember(@Path("id") id: String, @Path("userId") userId: String): OkOut

    @POST("chats/{id}/members/{userId}/role")
    suspend fun setChatRole(@Path("id") id: String, @Path("userId") userId: String, @Body body: RoleInCommunity): OkOut

    @GET("calls/ice")
    suspend fun ice(): IceOut

    @GET("lookup/users/{username}")
    suspend fun lookupUser(@Path("username") username: String): UserPublic

    @POST("chats/{id}/pin")
    suspend fun pinChat(@Path("id") id: String, @Body body: FlagIn = FlagIn()): ChatOut

    @POST("chats/{id}/mute")
    suspend fun muteChat(@Path("id") id: String, @Body body: FlagIn = FlagIn()): ChatOut

    @DELETE("chats/{id}/messages/{mid}")
    suspend fun deleteMessage(@Path("id") id: String, @Path("mid") mid: String): OkOut

    @GET("inbox/badge")
    suspend fun inboxBadge(): InboxBadge

    @POST("posts/{id}/react")
    suspend fun reactPost(@Path("id") id: String, @Body body: ReactIn): ReactOut

    @POST("posts/{id}/repost")
    suspend fun repost(@Path("id") id: String, @Body body: RepostIn = RepostIn()): PostOut

    @POST("chats/{id}/messages/{mid}/react")
    suspend fun reactMessage(
        @Path("id") id: String,
        @Path("mid") mid: String,
        @Body body: ReactIn,
    ): ReactOut

    // ---- Badges & Sales ----

    @GET("badges")
    suspend fun listBadges(): BadgeListOut

    @POST("badges")
    suspend fun createBadge(@Body body: BadgeCreateIn): BadgeOut

    @DELETE("badges/{id}")
    suspend fun deleteBadge(@Path("id") id: String): OkOut

    @POST("badges/promocode")
    suspend fun createPromocode(@Body body: PromoCreateIn): OkOut

    @POST("badges/apply")
    suspend fun applyBadge(@Body body: BadgeApplyIn): BadgeApplyOut

    @GET("users/me/badges")
    suspend fun myBadges(): BadgeListOut

    @POST("users/me/badges/{id}/toggle")
    suspend fun toggleMyBadge(@Path("id") id: String): BadgeToggleOut

    @GET("users/me/sales")
    suspend fun mySales(): SaleListOut

    @GET("sales")
    suspend fun listSales(): SaleListOut

    @POST("sales")
    suspend fun createSale(@Body body: SaleCreateIn): OkOut

    @DELETE("sales/{id}")
    suspend fun deleteSale(@Path("id") id: String): OkOut

    @POST("sales/apply")
    suspend fun applySale(@Body body: SaleApplyIn): OkOut

    // ---- Polls, Tipping, Pinning, Explore, Notes, Daily Bonus, Visitors, Sessions ----

    @POST("posts/{id}/poll/vote")
    suspend fun votePoll(@Path("id") id: String, @Body body: PollVoteIn): PollVoteOut

    @POST("posts/{id}/tip")
    suspend fun tipPost(@Path("id") id: String, @Body body: PostTipIn): PostTipOut

    @POST("posts/{id}/pin")
    suspend fun pinPost(@Path("id") id: String): PinPostOut

    @GET("posts/hashtag/{tag}")
    suspend fun postsByHashtag(@Path("tag") tag: String, @Query("page") page: Int = 1): PagePosts

    @POST("ux/daily/claim")
    suspend fun uxDailyClaim(): DailyClaimOut

    @GET("auth/sessions")
    suspend fun listSessions(): SessionListOut

    /** Sign-ins for this account (device, IP, method, new-device flag). */
    @GET("me/security/activity")
    suspend fun securityActivity(@Query("limit") limit: Int = 50): SecurityActivityOut

    @DELETE("auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): OkOut

    @POST("auth/sessions/revoke-others")
    suspend fun revokeOtherSessions(@Body body: RefreshIn): OkOut

    // ---- E2EE devices & key transfer ----

    @GET("devices")
    suspend fun listDevices(): List<DeviceOut>

    @POST("devices/register")
    suspend fun registerDevice(@Body body: DeviceRegisterIn): DeviceOut

    @PATCH("devices/{id}")
    suspend fun renameDevice(@Path("id") id: String, @Body body: DeviceRenameIn): DeviceOut

    @DELETE("devices/{id}")
    suspend fun revokeDevice(@Path("id") id: String): OkOut

    @GET("devices/key-rotation")
    suspend fun keyRotationStatus(): KeyRotationOut

    @POST("devices/key-rotation/claim")
    suspend fun claimKeyRotation(): KeyRotationOut

    @POST("devices/key-rotation/complete")
    suspend fun completeKeyRotation(@Body body: KeyRotationCompleteIn): OkOut

    @POST("devices/key-rotation/ack")
    suspend fun acknowledgeKeyRotation(@Body body: KeyRotationAckIn): OkOut

    @POST("devices/transfer/initiate")
    suspend fun transferInitiate(@Body body: TransferInitiateIn): TransferOut

    @GET("devices/transfer/{id}")
    suspend fun transferGet(@Path("id") id: String): TransferOut

    @POST("devices/transfer/{id}/approve")
    suspend fun transferApprove(@Path("id") id: String, @Body body: TransferApproveIn): TransferOut

    @POST("devices/transfer/{id}/deny")
    suspend fun transferDeny(@Path("id") id: String): OkOut

    @POST("devices/transfer/{id}/complete")
    suspend fun transferComplete(@Path("id") id: String, @Body body: TransferCompleteIn): com.google.gson.JsonObject

    @GET("devices/transfer/{id}/poll")
    suspend fun transferPoll(@Path("id") id: String): TransferOut
}

/** Plain-text form field helper for multipart requests. */
fun String.toPlainBody(): RequestBody = toRequestBody("text/plain".toMediaType())

object Api {
    val gson: Gson = GsonBuilder().create()
    @Volatile private var client: UxaoroApi? = null
    @Volatile var http: OkHttpClient? = null
        private set

    fun service(): UxaoroApi = client ?: rebuild()

    fun rebuild(): UxaoroApi {
        val auth = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val open = path.contains("/auth/register") ||
                path.contains("/auth/login") ||
                path.endsWith("/auth/refresh")
            val t = Prefs.accessToken
            val b = chain.request().newBuilder()
                .header("X-Device-Id", Prefs.deviceId)
            if (!open && t.isNotBlank()) b.header("Authorization", "Bearer $t")
            chain.proceed(b.build())
        }
        val log = HttpLoggingInterceptor().apply {
            level = if (org.uxaoro.app.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        // TLS guard: HTTPS sessions that are being intercepted by an
        // SSL-proxying proxy are reset before the request is written.
        // See org.uxaoro.app.net.TlsGuard.
        // AppProxy: routes only Uxaoro traffic through the configured proxy
        // (unless "Don't use proxy when VPN is on" is enabled and VPN is active).
        val ok = TlsGuard.harden(
            org.uxaoro.app.net.AppProxy.configure(
                OkHttpClient.Builder()
                    .addInterceptor(auth)
                    .addInterceptor(log)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(20, TimeUnit.SECONDS),
            ),
        ).build()
        http = ok
        val base = Prefs.apiBase.let { if (it.endsWith("/")) it else "$it/" }
        val r = Retrofit.Builder()
            .baseUrl(base)
            .client(ok)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val api = r.create(UxaoroApi::class.java)
        client = api
        return api
    }

    fun mediaUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("content://") || path.startsWith("file://") || path.startsWith("blob:") || path.startsWith("data:")) return path
        return Prefs.apiBase.trimEnd('/') + if (path.startsWith("/")) path else "/$path"
    }

    fun wsUrl(): String {
        val b = Prefs.apiBase
        val ws = when {
            b.startsWith("https://") -> "wss://" + b.removePrefix("https://")
            b.startsWith("http://") -> "ws://" + b.removePrefix("http://")
            else -> "wss://$b"
        }
        // No token in the URL: WsClient authenticates with a first-frame
        // `hello` message right after the socket opens.
        return ws.trimEnd('/') + "/ws"
    }

    /**
     * Standalone client for media requests (Coil images, video posters) so they
     * use the same guarded TLS stack and Uxaoro app proxy as the API/websocket client.
     */
    fun mediaClient(): OkHttpClient = TlsGuard.harden(
        org.uxaoro.app.net.AppProxy.configure(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS),
        ),
    ).build()

    fun errorMessage(t: Throwable): String {
        // The TLS guard has the most meaningful wording for its own failures.
        TlsGuard.userMessage(t)?.let { return it }
        if (t is HttpException) {
            val raw = t.response()?.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(raw, ApiError::class.java) }.getOrNull()
            return parsed?.detail ?: parsed?.error ?: t.message() ?: "HTTP ${t.code()}"
        }
        return t.message ?: t.toString()
    }
}

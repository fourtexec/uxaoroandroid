package org.uxaoro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.uxaoro.app.ui.admin.AdminScreen
import org.uxaoro.app.ui.wallet.WalletScreen
import org.uxaoro.app.ui.premium.PremiumScreen
import org.uxaoro.app.ui.auth.DevSettingsScreen
import org.uxaoro.app.ui.auth.LoginScreen
import org.uxaoro.app.ui.auth.PreSettingsScreen
import org.uxaoro.app.ui.auth.RegisterScreen
import org.uxaoro.app.ui.call.CallOverlay
import org.uxaoro.app.ui.chat.ChatInfoScreen
import org.uxaoro.app.ui.chat.ChatThreadScreen
import org.uxaoro.app.ui.chat.ChatsScreen
import org.uxaoro.app.ui.chat.NewChatScreen
import org.uxaoro.app.ui.chat.NewGroupScreen
import org.uxaoro.app.ui.feed.CreatePostScreen
import org.uxaoro.app.ui.feed.FeedScreen
import org.uxaoro.app.ui.feed.PostDetailScreen
import org.uxaoro.app.ui.motion.ScreenFade
import org.uxaoro.app.ui.motion.pulse
import org.uxaoro.app.ui.motion.screenTransform
import org.uxaoro.app.ui.motion.tabIconScale
import org.uxaoro.app.ui.motion.tabTransform
import org.uxaoro.app.ui.components.GlassToast
import org.uxaoro.app.ui.news.MoreScreen
import org.uxaoro.app.ui.news.NewsPopupOnEnterLayer
import org.uxaoro.app.ui.news.NewsScreen
import org.uxaoro.app.ui.news.ExploreScreen
import org.uxaoro.app.ui.settings.BlockedScreen
import org.uxaoro.app.ui.settings.AppLockScreen
import org.uxaoro.app.ui.settings.SecurityActivityScreen
import org.uxaoro.app.ui.upd.UpdatesScreen
import org.uxaoro.app.ui.upd.UpdateGate
import org.uxaoro.app.ui.settings.DevicesScreen
import org.uxaoro.app.ui.settings.DeviceTransferNewScreen
import org.uxaoro.app.ui.settings.InAppSettingsScreen
import org.uxaoro.app.ui.social.CommunitiesScreen
import org.uxaoro.app.ui.social.CommunityDetailScreen
import org.uxaoro.app.ui.social.CommunityMembersScreen
import org.uxaoro.app.ui.social.CommunitySettingsScreen
import org.uxaoro.app.ui.social.CreateCommunityScreen
import org.uxaoro.app.ui.social.CreateStoryScreen
import org.uxaoro.app.ui.social.EditProfileScreen
import org.uxaoro.app.ui.social.FriendsScreen
import org.uxaoro.app.ui.social.NotificationsScreen
import org.uxaoro.app.ui.social.ProfileScreen
import org.uxaoro.app.ui.social.SearchScreen
import org.uxaoro.app.ui.social.StoryViewerScreen
import org.uxaoro.app.ui.social.UserScreen
import org.uxaoro.app.ui.theme.LocalHazeState
import org.uxaoro.app.ui.theme.LocalMainChrome
import org.uxaoro.app.ui.theme.MeshBackground
import org.uxaoro.app.ui.theme.UxaoroTheme
import org.uxaoro.app.ui.theme.glassBlurBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun AppRoot(vm: AppVm) {
    UxaoroTheme {
        // Route the system/gesture Back into the app's own navigation instead of
        // letting Android finish the Activity (which looked like a minimize).
        // When there is nothing left to go back to, fall through to the system
        // default so the app can finally be dismissed from the home tab.
        BackHandler(enabled = vm.canGoBack() || vm.call != null) {
            if (vm.call != null) vm.endCall() else vm.back()
        }
        val snack = remember { SnackbarHostState() }
        val toastHaze = remember { HazeState() }
        LaunchedEffect(vm.snack) {
            vm.snack?.let {
                snack.showSnackbar(it)
                vm.snack = null
            }
        }
        // Tell the user when the TLS guard had to reset a connection: this is
        // the visible half of "an SSL-proxying proxy was cut off".
        LaunchedEffect(Unit) {
            org.uxaoro.app.net.TlsGuard.status.collect { blocked ->
                blocked?.let { snack.showSnackbar("Reset connection — ${it.reason}") }
            }
        }
        // Route chrome owns isolated Haze states. This root source is only for
        // the snackbar, so transitions cannot leak into other chrome.
        CompositionLocalProvider(LocalHazeState provides null) {
            Box(Modifier.fillMaxSize()) {
                // The launch screen is deliberately still: a plain backdrop and logo only.
                // The animated mesh begins only after boot has completed.
                if (vm.screen is Screen.Boot) {
                    // Match the white Uxaoro mark on its plain black brand field.
                    Box(Modifier.fillMaxSize().background(Color.Black))
                } else {
                    MeshBackground()
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    // Rounded frosted toast instead of Material's flat snackbar.
                    snackbarHost = {
                        // This root Scaffold cannot account for MainShell's custom
                        // tab bar (it is drawn inside the screen body). Lift toasts
                        // above both the bar and the feed FAB instead of painting
                        // over the navigation labels.
                        val toastLift = if (vm.screen is Screen.Main) 110.dp else 0.dp
                        SnackbarHost(
                            snack,
                            Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .padding(bottom = toastLift),
                        ) { data ->
                            CompositionLocalProvider(
                                LocalHazeState provides toastHaze,
                            ) {
                                GlassToast(data.visuals.message)
                            }
                        }
                    },
                ) { _ ->
                    Box(Modifier.fillMaxSize().haze(state = toastHaze)) {
                        ScreenFade(
                            targetState = vm.screen,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (initialState is Screen.Boot || targetState is Screen.Boot) {
                                    // Do not animate into or out of the launch logo.
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    screenTransform(vm.navigatingForward)
                                }
                            },
                        ) { s ->
                            when (s) {
                                Screen.Boot -> Boot(vm)
                                Screen.Login -> LoginScreen(vm)
                                Screen.Register -> RegisterScreen(vm)
                                Screen.PreSettings -> PreSettingsScreen(vm)
                                Screen.DevSettings -> DevSettingsScreen(vm)
                                Screen.Main -> MainShell(vm)
                                is Screen.Post -> PostDetailScreen(vm, s.id)
                                is Screen.CreatePost -> CreatePostScreen(vm, s.communityId, s.wallUserId, s.originalPostId)
                                is Screen.User -> UserScreen(vm, s.id)
                                is Screen.Community -> CommunityDetailScreen(vm, s.id)
                                Screen.CreateCommunity -> CreateCommunityScreen(vm)
                                is Screen.Chat -> ChatThreadScreen(vm, s.id)
                                Screen.NewChat -> NewChatScreen(vm)
                                Screen.NewGroup -> NewGroupScreen(vm)
                                is Screen.ChatInfo -> ChatInfoScreen(vm, s.id)
                                Screen.Search -> SearchScreen(vm)
                                Screen.Notifications -> NotificationsScreen(vm)
                                Screen.Settings -> InAppSettingsScreen(vm)
                                Screen.EditProfile -> EditProfileScreen(vm)
                                Screen.Admin -> {
                                    if (vm.me?.isStaff == true) AdminScreen(vm) else MainShell(vm)
                                }
                                Screen.Blocked -> BlockedScreen(vm)
                                Screen.Wallet -> WalletScreen(vm)
                                Screen.Premium -> PremiumScreen(vm)
                                Screen.News -> NewsScreen(vm)
                                is Screen.Explore -> ExploreScreen(vm, s.tag)
                                Screen.CreateStory -> CreateStoryScreen(vm)
                                is Screen.StoryViewer -> StoryViewerScreen(vm, s.userId)
                                is Screen.CommunityMembers -> CommunityMembersScreen(vm, s.id)
                                is Screen.CommunitySettings -> CommunitySettingsScreen(vm, s.id)
                                Screen.Devices -> DevicesScreen(vm)
                                Screen.Updates -> UpdatesScreen(vm)
                                Screen.SecurityActivity -> SecurityActivityScreen(vm)
                                Screen.DeviceTransferNew -> DeviceTransferNewScreen(vm)
                                is Screen.DeviceTransferApprove -> DeviceTransferNewScreen(vm)
                            }
                        }
                        vm.call?.let { CallOverlay(vm, it) }
                        // Normal update: dismissible banner. Required update: modal prompt.
                        UpdateGate()
                        // The app lock is the outermost layer: while it is on, none of
                        // the screens above are visible or reachable.
                        if (vm.appLocked) AppLockScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun Boot(vm: AppVm) {
    LaunchedEffect(Unit) { vm.boot() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            org.uxaoro.app.ui.components.UxaoroLogo(
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uxaoro", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(8.dp))
                org.uxaoro.app.ui.components.BetaTag()
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppVm) {
    val tabs = listOf("Feed", "Circles", "Wire", "People", "You", "More")
    val icons = listOf(
        Icons.Default.DynamicFeed,
        Icons.Default.Groups,
        Icons.Default.Chat,
        Icons.Default.People,
        Icons.Default.Person,
        Icons.Default.MoreHoriz,
    )
    // MainShell shares a source with its active-tab navigation bar; standalone
    // routes create their own state in BlurScaffold.
    val haze = remember { HazeState() }
    CompositionLocalProvider(
        LocalHazeState provides haze,
        LocalMainChrome provides true,
    ) {
    Box(Modifier.fillMaxSize()) {
        ScreenFade(
            targetState = vm.tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { tabTransform(initialState, targetState) },
        ) { tab ->
            when (tab) {
                0 -> FeedScreen(vm)
                1 -> CommunitiesScreen(vm)
                2 -> ChatsScreen(vm)
                3 -> FriendsScreen(vm)
                4 -> ProfileScreen(vm)
                else -> MoreScreen(vm)
            }
        }
        if (vm.screen is Screen.Main || (vm.screen is Screen.Admin && vm.me?.isStaff != true)) {
            GlassNav(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                tabs = tabs,
                icons = icons,
                selected = vm.tab,
                chatBadge = vm.chatUnread,
                onSelect = {
                    vm.selectTab(it)
                },
            )
        }
        NewsPopupOnEnterLayer()
    }
    }
}

@Composable
private fun GlassNav(
    modifier: Modifier,
    tabs: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    chatBadge: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .glassBlurBar(RoundedCornerShape(14.dp), elevation = 14.dp)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val on = selected == i
            val pill by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(220),
                label = "tabPill",
            )
            val ink by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                tween(220),
                label = "tabInk",
            )
            val scale = tabIconScale(on)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(11.dp))
                    .background(pill)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            ) {
                BadgedBox(badge = {
                    if (t == "Wire" && chatBadge > 0) {
                        Badge(modifier = Modifier.pulse(0.82f, 1.22f, 760)) { Text(if (chatBadge > 99) "99+" else "$chatBadge") }
                    }
                }) {
                    Icon(
                        icons[i],
                        contentDescription = t,
                        tint = ink,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    t,
                    fontSize = 9.sp,
                    maxLines = 1,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = ink,
                )
                // Active-tab dot: a small accent marker under the label.
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (on) ink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    MeshBackground(modifier)
}

package org.uxaoro.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.liquidGlass
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.AuditItem
import org.uxaoro.app.data.BadgeApplyIn
import org.uxaoro.app.data.BadgeCreateIn
import org.uxaoro.app.data.BadgeOut
import org.uxaoro.app.data.BanIn
import org.uxaoro.app.data.PostOut
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.PromoCreateIn
import org.uxaoro.app.data.PromoOut
import org.uxaoro.app.data.ReportOut
import org.uxaoro.app.data.RoleIn
import org.uxaoro.app.data.SaleApplyIn
import org.uxaoro.app.data.SaleCreateIn
import org.uxaoro.app.data.SaleItemsSpec
import org.uxaoro.app.data.SaleOut
import org.uxaoro.app.data.ServerSettingsOut
import org.uxaoro.app.data.StatsOut
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.data.UxGrantIn
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.ui.components.inkButtonColors
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.ui.premium.formatSalePerks

private fun fmtUxAdmin(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

private val saleDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun saleDateLabel(value: LocalDateTime?): String =
    value?.format(saleDateFormatter) ?: "Not set"

private fun saleIso(value: LocalDateTime?): String =
    value?.atZone(ZoneId.systemDefault())?.toInstant()?.toString() ?: ""

private fun saleIsoLabel(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        saleDateFormatter.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}

@Composable
private fun SaleDateTimePicker(
    label: String,
    value: LocalDateTime?,
    onValueChange: (LocalDateTime?) -> Unit,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val initial = value ?: LocalDateTime.now().withSecond(0).withNano(0)
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        android.app.TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onValueChange(LocalDateTime.of(year, month + 1, day, hour, minute))
                            },
                            initial.hour,
                            initial.minute,
                            true,
                        ).show()
                    },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("$label: ${saleDateLabel(value)}")
        }
        if (value != null) {
            TextButton(onClick = { onValueChange(null) }) { Text("Clear") }
        }
    }
}


@Composable
fun AdminScreen(vm: AppVm) {
    val admin = vm.me?.isAdmin == true
    val tabs = if (admin) {
        listOf("Overview", "Users", "Posts", "Badges", "Sales", "Reports", "UX", "Server", "Audit")
    } else {
        listOf("Overview", "Users", "Posts", "Badges", "Sales", "Reports")
    }
    var tab by remember { mutableIntStateOf(0) }
    BlurScaffold(topBar = { DATopBar("Control panel", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                when (vm.me?.role) {
                    "owner" -> "Owner"
                    "admin" -> "Administrator"
                    else -> "Moderator"
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, modifier = Modifier.height(36.dp)) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.height(36.dp), text = { Text(t, fontSize = 12.sp) })
                }
            }
            when (tabs[tab]) {
                "Overview" -> OverviewTab()
                "Users" -> UsersTab(admin)
                "Posts" -> PostsTab()
                "Badges" -> BadgesTab(vm)
                "Sales" -> SalesTab(vm)
                "Reports" -> ReportsTab()
                "UX" -> UxTab()
                "Server" -> ServerTab()
                "Audit" -> AuditTab()
            }
        }
    }
}

@Composable
private fun UxTab() {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    var loadRev by remember { mutableIntStateOf(0) }
    LaunchedEffect(q, loadRev) {
        items = runCatching { Api.service().adminUsers(q = q).items }.getOrDefault(emptyList())
        balances = items.associate { it.id to it.balanceUx }
    }
    Column {
        OutlinedTextField(
            q, { q = it }, modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Search users") },
            colors = inkFieldColors(),
        )
        TextButton(onClick = { loadRev++ }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Search") }
        LazyColumn {
            items(items, key = { it.id }) { u ->
                var amt by remember(u.id) { mutableStateOf("10") }
                var reason by remember(u.id) { mutableStateOf("") }
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    UserRow(u, subtitle = "Balance: " + fmtUxAdmin(balances[u.id] ?: 0.0) + " UX", onClick = {})
                    OutlinedTextField(
                        amt, { amt = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        placeholder = { Text("Amount (negative to deduct)") },
                        singleLine = true,
                        colors = inkFieldColors(),
                    )
                    OutlinedTextField(
                        reason, { reason = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        placeholder = { Text("Reason (optional)") },
                        singleLine = true,
                        colors = inkFieldColors(),
                    )
                    Row {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    Api.service().adminGrantUx(u.id, UxGrantIn(amt.toDoubleOrNull() ?: 0.0, reason.trim()))
                                }.onSuccess {
                                    balances = balances + (u.id to it.balanceUx)
                                }.onFailure { }
                            }
                        }) { Text("Grant / deduct") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab() {
    var s by remember { mutableStateOf<StatsOut?>(null) }
    LaunchedEffect(Unit) { s = runCatching { Api.service().adminStats() }.getOrNull() }
    val st = s
    if (st == null) EmptyBox("Loading stats…")
    else Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        StatCard("Users", st.users.toString())
        StatCard("Online now", st.online.toString())
        StatCard("Posts", st.posts.toString())
        StatCard("Communities", st.communities.toString())
        StatCard("Messages (ciphertext)", st.messages.toString())
        StatCard("Open reports", st.reportsOpen.toString())
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun UsersTab(admin: Boolean) {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch { items = runCatching { Api.service().adminUsers(q = q).items }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { load() }
    Column {
        OutlinedTextField(
            q, { q = it }, modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Search users") },
            colors = inkFieldColors(),
        )
        TextButton(onClick = { load() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Search") }
        LazyColumn {
            items(items, key = { it.id }) { u ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    UserRow(u, subtitle = "${u.role}${if (u.banned) " · BANNED" else ""}", onClick = {})
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (u.banned) {
                            TextButton(onClick = {
                                scope.launch { Api.service().ban(u.id, BanIn(false)); load() }
                            }) { Text("Unban") }
                        } else {
                            TextButton(onClick = {
                                scope.launch { Api.service().ban(u.id, BanIn(true, "Banned by staff")); load() }
                            }) { Text("Ban") }
                        }
                        if (admin) {
                            if (u.role != "moderator") {
                                TextButton(onClick = {
                                    scope.launch { Api.service().setRole(u.id, RoleIn("moderator")); load() }
                                }) { Text("Make mod") }
                            } else {
                                TextButton(onClick = {
                                    scope.launch { Api.service().setRole(u.id, RoleIn("user")); load() }
                                }) { Text("Remove mod") }
                            }
                            if (u.role != "admin") {
                                TextButton(onClick = {
                                    scope.launch { Api.service().setRole(u.id, RoleIn("admin")); load() }
                                }) { Text("Make admin") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostsTab() {
    var items by remember { mutableStateOf<List<PostOut>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch { items = runCatching { Api.service().adminPosts().items }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { load() }
    LazyColumn {
        items(items, key = { it.id }) { p ->
            Column(Modifier.padding(16.dp)) {
                Text(p.author?.displayName ?: "?", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(p.text.take(280), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    scope.launch { Api.service().adminDeletePost(p.id); load() }
                }) { Text("Delete post") }
            }
        }
    }
}

@Composable
private fun ReportsTab() {
    var items by remember { mutableStateOf<List<ReportOut>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch { items = runCatching { Api.service().adminReports("open") }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { load() }
    if (items.isEmpty()) EmptyBox("No open reports")
    else LazyColumn {
        items(items, key = { it.id }) { r ->
            Column(Modifier.padding(16.dp)) {
                Text("${r.targetType} · ${r.reason}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(r.details, color = MaterialTheme.colorScheme.onBackground)
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            Api.service().resolveReport(r.id, mapOf("status" to "resolved"))
                            load()
                        }
                    }) { Text("Resolve") }
                    TextButton(onClick = {
                        scope.launch {
                            Api.service().resolveReport(r.id, mapOf("status" to "dismissed"))
                            load()
                        }
                    }) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun ServerTab() {
    var s by remember { mutableStateOf<ServerSettingsOut?>(null) }
    var motd by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val got = runCatching { Api.service().adminSettings() }.getOrNull()
        s = got
        motd = got?.motd.orEmpty()
    }
    val cur = s ?: return
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Registration open", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = cur.registrationOpen, onCheckedChange = { v ->
                scope.launch {
                    s = Api.service().setAdminSettings(mapOf("registration_open" to v))
                }
            })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Maintenance mode", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = cur.maintenance, onCheckedChange = { v ->
                scope.launch {
                    s = Api.service().setAdminSettings(mapOf("maintenance" to v))
                }
            })
        }
        Spacer(Modifier.height(8.dp))
        PrimaryField(motd, { motd = it }, "Message of the day")
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Save MOTD", icon = "check-circle") {
            scope.launch { s = Api.service().setAdminSettings(mapOf("motd" to motd)) }
        }
    }
}

@Composable
private fun AuditTab() {
    var items by remember { mutableStateOf<List<AuditItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        items = runCatching { Api.service().audit().items }.getOrDefault(emptyList())
    }
    LazyColumn {
        items(items, key = { it.id }) { a ->
            Column(Modifier.padding(16.dp)) {
                Text(a.action, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("${a.targetType} ${a.targetId}  ${a.details}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BadgesTab(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var badges by remember { mutableStateOf<List<BadgeOut>>(emptyList()) }
    var promos by remember { mutableStateOf<List<PromoOut>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var imageWebp by remember { mutableStateOf("/uploads/badges/default.webp") }
    var selectedBadgeId by remember { mutableStateOf("") }
    var promoCode by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            runCatching { Api.service().listBadges() }.onSuccess {
                badges = it.items
                promos = it.promocodes
                if (selectedBadgeId.isBlank() && it.items.isNotEmpty()) {
                    selectedBadgeId = it.items.first().id
                }
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("Create Badge", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            PrimaryField(name, { name = it }, "Badge name (e.g. Early Adopter)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(desc, { desc = it }, "Description")
            Spacer(Modifier.height(8.dp))
            PrimaryField(imageWebp, { imageWebp = it }, ".webp image path")
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Create badge", icon = "medal-star") {
                if (name.isBlank()) {
                    vm.toast("Enter a badge name")
                    return@PrimaryButton
                }
                scope.launch {
                    runCatching {
                        Api.service().createBadge(
                            BadgeCreateIn(
                                name = name.trim(),
                                description = desc.trim(),
                                imageWebp = imageWebp.trim().ifBlank { "/uploads/badges/default.webp" },
                            )
                        )
                    }.onSuccess {
                        name = ""
                        desc = ""
                        vm.toast("Badge created!")
                        reload()
                    }.onFailure { vm.toast(Api.errorMessage(it)) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("Create Badge Promocode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (badges.isEmpty()) {
                Text("Create a badge first to generate promocodes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                Text("Select badge:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    badges.forEach { b ->
                        val sel = b.id == selectedBadgeId
                        Text(
                            "✦ ${b.name}",
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clickable { selectedBadgeId = b.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        PrimaryField(promoCode, { promoCode = it }, "Promocode (e.g. UX-FOUNDER)")
                    }
                    OutlinedButton(onClick = {
                        val rand = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                        promoCode = "UX-$rand"
                    }) {
                        Text("Random")
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Create promocode", icon = "ticket") {
                    if (selectedBadgeId.isBlank() || promoCode.isBlank()) {
                        vm.toast("Select a badge and enter a promocode")
                        return@PrimaryButton
                    }
                    scope.launch {
                        runCatching {
                            Api.service().createPromocode(PromoCreateIn(code = promoCode.trim(), badgeId = selectedBadgeId))
                        }.onSuccess {
                            promoCode = ""
                            vm.toast("Promocode created!")
                            reload()
                        }.onFailure { vm.toast(Api.errorMessage(it)) }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("All Badges (${badges.size})", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (badges.isEmpty()) {
                Text("No badges created yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                badges.forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("✦ ${b.name}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "${b.description.ifBlank { "No description" }} · ID: ${b.id.take(8)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { Api.service().deleteBadge(b.id) }
                                    .onSuccess {
                                        vm.toast("Badge deleted")
                                        reload()
                                    }
                                    .onFailure { vm.toast(Api.errorMessage(it)) }
                            }
                        }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("Promocodes (${promos.size})", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (promos.isEmpty()) {
                Text("No promocodes created yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                promos.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.code} → ${p.badgeName.ifBlank { p.badgeId.take(8) }}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                if (p.usedBy.isNotBlank()) "Used by ${p.usedBy.take(8)}" else "Available",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.usedBy.isBlank()) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching { Api.service().applyBadge(BadgeApplyIn(p.code)) }
                                            .onSuccess {
                                                runCatching { Api.service().me() }.onSuccess { u ->
                                                    vm.me = u
                                                    Prefs.user = u
                                                }
                                                vm.toast("Badge applied to your profile!")
                                                reload()
                                            }
                                            .onFailure { vm.toast(Api.errorMessage(it)) }
                                    }
                                },
                                colors = inkButtonColors(),
                            ) {
                                Text("Apply to me")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesTab(vm: AppVm) {
    val scope = rememberCoroutineScope()
    var sales by remember { mutableStateOf<List<SaleOut>>(emptyList()) }
    var badges by remember { mutableStateOf<List<BadgeOut>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var priceUxText by remember { mutableStateOf("0") }
    var includePremium by remember { mutableStateOf(true) }
    var premiumDaysText by remember { mutableStateOf("30") }
    var selectedBadges by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usersRule by remember { mutableStateOf("all") }
    var usersCsv by remember { mutableStateOf("") }
    var oneTime by remember { mutableStateOf(false) }
    var startsAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var endsAt by remember { mutableStateOf<LocalDateTime?>(null) }

    fun reload() {
        scope.launch {
            runCatching { Api.service().listSales().items }.onSuccess { sales = it }
            runCatching { Api.service().listBadges().items }.onSuccess { badges = it }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("Create Sale / Special Offer", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            PrimaryField(name, { name = it }, "Sale name (e.g. Launch Bundle)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(priceUxText, { priceUxText = it }, "Price in UX (0 = Free)")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Include Uxaoro Premium", Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = includePremium, onCheckedChange = { includePremium = it })
            }
            if (includePremium) {
                Spacer(Modifier.height(6.dp))
                PrimaryField(premiumDaysText, { premiumDaysText = it }, "Premium duration in days (any length, e.g. 7, 30, 90, 365)")
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Bundle badges with this sale:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    badges.forEach { b ->
                        val sel = selectedBadges.contains(b.id)
                        Text(
                            "✦ ${b.name}",
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clickable {
                                    selectedBadges = if (sel) selectedBadges - b.id else selectedBadges + b.id
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Target audience:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("all" to "All users", "list" to "Only listed", "except" to "All except").forEach { (rule, label) ->
                    val sel = usersRule == rule
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable { usersRule = rule }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (usersRule != "all") {
                Spacer(Modifier.height(8.dp))
                PrimaryField(usersCsv, { usersCsv = it }, "Usernames or IDs (comma-separated)")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("One-time activation", color = MaterialTheme.colorScheme.onBackground)
                    Text("Each user can activate this sale only once", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = oneTime, onCheckedChange = { oneTime = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Activation time limit (optional)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SaleDateTimePicker("Starts", startsAt) { startsAt = it }
            SaleDateTimePicker("Ends", endsAt) { endsAt = it }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Create sale", icon = "tag-price") {
                if (name.isBlank()) {
                    vm.toast("Enter a sale name")
                    return@PrimaryButton
                }
                if (startsAt != null && endsAt != null && !startsAt!!.isBefore(endsAt)) {
                    vm.toast("End time must be after start time")
                    return@PrimaryButton
                }
                val parsedPrice = priceUxText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val parsedDays = if (includePremium) (premiumDaysText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 30) else 0
                val users = usersCsv.split(",").map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }
                scope.launch {
                    runCatching {
                        Api.service().createSale(
                            SaleCreateIn(
                                name = name.trim(),
                                priceUx = parsedPrice,
                                premiumDays = parsedDays,
                                items = SaleItemsSpec(
                                    premium = parsedDays > 0,
                                    premiumDays = parsedDays,
                                    priceUx = parsedPrice,
                                    badges = selectedBadges.toList(),
                                ),
                                usersRule = usersRule,
                                users = users,
                                once = oneTime,
                                startsAt = saleIso(startsAt),
                                endsAt = saleIso(endsAt),
                            )
                        )
                    }.onSuccess {
                        name = ""
                        priceUxText = "0"
                        usersCsv = ""
                        selectedBadges = emptySet()
                        oneTime = false
                        startsAt = null
                        endsAt = null
                        vm.toast("Sale created!")
                        reload()
                    }.onFailure { vm.toast(Api.errorMessage(it)) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text("Active Sales (${sales.size})", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (sales.isEmpty()) {
                Text("No active sales yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                sales.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("✦ ${s.name}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "${formatSalePerks(s)} · Rule: ${s.usersRule} · ID: ${s.id.take(8)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (s.once) {
                                Text(
                                    if (s.appliedByMe) "One-time · activated by you" else "One-time activation",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            val startLabel = saleIsoLabel(s.startsAt)
                            val endLabel = saleIsoLabel(s.endsAt)
                            if (startLabel != null || endLabel != null) {
                                Text(
                                    buildString {
                                        append(if (s.activeNow) "Active" else "Inactive")
                                        if (startLabel != null) append(" from $startLabel")
                                        if (endLabel != null) append(" until $endLabel")
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                enabled = !(s.once && s.appliedByMe),
                                onClick = {
                                    scope.launch {
                                        runCatching { Api.service().applySale(SaleApplyIn(s.id)) }
                                            .onSuccess {
                                                runCatching { Api.service().me() }.onSuccess { u ->
                                                    vm.me = u
                                                    Prefs.user = u
                                                }
                                                vm.toast("Sale applied!")
                                                reload()
                                            }
                                            .onFailure { vm.toast(Api.errorMessage(it)) }
                                    }
                                },
                                colors = inkButtonColors(),
                            ) {
                                Text(if (s.once && s.appliedByMe) "Activated" else "Apply")
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.service().deleteSale(s.id) }
                                        .onSuccess {
                                            vm.toast("Sale deleted")
                                            reload()
                                        }
                                        .onFailure { vm.toast(Api.errorMessage(it)) }
                                }
                            }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}


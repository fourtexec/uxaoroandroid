package org.uxaoro.app.ui.wallet

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.UxBuyIn
import org.uxaoro.app.data.UxGiftIn
import org.uxaoro.app.data.UxTx
import org.uxaoro.app.data.UxWallet
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.localId
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.motion.ConfettiRain
import androidx.compose.foundation.layout.Box
import org.uxaoro.app.ui.motion.zoomPop

private fun fmtUx(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

private fun txLabel(t: UxTx): String = when (t.reason) {
    "gift_in" -> "Gift received"
    "gift_out" -> "Gift sent"
    "purchase" -> "Purchased UX"
    "premium_buy" -> "Premium subscription"
    "admin" -> "Admin grant"
    "admin_deduct" -> "Admin deduction"
    "watch" -> "Watch reward"
    "join" -> "Join reward"
    else -> t.reason.replaceFirstChar { it.uppercase() }
}

@Composable
fun WalletScreen(vm: AppVm) {
    var wallet by remember { mutableStateOf<UxWallet?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var buyAmount by remember { mutableStateOf("10") }
    var giftUser by remember { mutableStateOf("") }
    var giftAmount by remember { mutableStateOf("5") }
    var giftMessage by remember { mutableStateOf("") }
    var linkCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    fun load() {
        scope.launch {
            runCatching { Api.service().uxWallet() }
                .onSuccess { wallet = it }
                .onFailure { err = Api.errorMessage(it) }
        }
    }
    LaunchedEffect(Unit) { load() }

    val w = wallet
    BlurScaffold(topBar = { DATopBar("UX Wallet", onBack = { vm.back() }) }) { pad ->
        if (err != null) {
            Column(Modifier.padding(pad).fillMaxSize()) { ErrorText(err) }
        } else if (w == null) {
            Column(Modifier.padding(pad).fillMaxSize()) { }
        } else {
            Box(Modifier.padding(pad)) {
                ConfettiRain(celebrate, Modifier.fillMaxSize())
                LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssetSvgIcon("calendar-mark", contentDescription = "Daily UX", size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Daily UX Check-In & Streak", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Streak: ${w.daily.streak} day${if (w.daily.streak == 1) "" else "s"} · Total claimed: ${fmtUx(w.daily.totalClaimedUx)} UX" +
                                        (if (w.daily.premiumMultiplier > 1) " · ✦ 2x Premium Bonus" else ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                PrimaryButton(
                                    if (w.daily.claimedToday) "Claimed Today" else "Claim +${fmtUx(w.daily.rewardUx)} UX Daily Bonus",
                                    enabled = !busy && !w.daily.claimedToday,
                                    icon = if (w.daily.claimedToday) "check-circle" else "gift",
                                ) {
                                    val before = wallet
                                    scope.localFirst(
                                        vm,
                                        "Claim daily UX",
                                        send = {
                                            val res = Api.service().uxDailyClaim()
                                            celebrate += 1
                                            vm.toast("+${fmtUx(res.rewardUx)} UX! Day ${res.streak} streak")
                                            load()
                                        },
                                        apply = {
                                            // "Claim +N UX" answers the tap immediately.
                                            wallet = before?.copy(daily = before.daily.copy(claimedToday = true))
                                        },
                                        undo = {
                                            wallet = before
                                            vm.toast("Not claimed — try again.")
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().zoomPop(),
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    "${fmtUx(w.balanceUx)} UX",
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "1 UX = ${fmtUx(w.rate)} ${w.asset} · buy in Telegram, gift it in Uxaoro",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { vm.go(Screen.Premium) }) {
                            Text("✦ ${if (vm.me?.premium == true) "Manage Premium" else "Get Uxaoro Premium"} — pay with UX")
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Buy UX", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        if (w.cryptobotEnabled) {
                            PrimaryField(buyAmount, { buyAmount = it }, "Amount of UX")
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Buy via CryptoBot", enabled = !busy, icon = "wallet") {
                                val amt = buyAmount.toDoubleOrNull() ?: 0.0
                                if (amt <= 0) {
                                    vm.toast("Enter a positive amount")
                                } else {
                                    busy = true
                                    scope.launch {
                                        runCatching { Api.service().uxBuy(UxBuyIn(amt)) }
                                            .onSuccess { p ->
                                                val url = p.botInvoiceUrl.ifBlank { p.payUrl }
                                                if (url.isNotBlank()) {
                                                    runCatching {
                                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                                    }
                                                }
                                                repeat(40) {
                                                    delay(3000)
                                                    val st = runCatching { Api.service().uxPurchaseStatus(p.id) }.getOrNull()
                                                    if (st?.status == "paid") {
                                                        celebrate += 1
                                                        vm.toast("${fmtUx(st.amountUx)} UX credited")
                                                        load()
                                                        return@launch
                                                    }
                                                }
                                                vm.toast("Invoice created — pay it in Telegram")
                                            }
                                            .onFailure { vm.toast(Api.errorMessage(it)) }
                                        busy = false
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Purchases run in the Telegram bot" +
                                    (if (w.telegramBotUsername.isNotBlank()) " (@${w.telegramBotUsername})" else "") + ".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (w.telegramBotUsername.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            if (!w.telegramLinked) {
                                if (linkCode.isBlank()) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { Api.service().uxLinkCode() }
                                                .onSuccess { linkCode = it.code }
                                                .onFailure { vm.toast(Api.errorMessage(it)) }
                                        }
                                    }) { Text("Link Telegram account") }
                                } else {
                                    Text(
                                        "Send /start $linkCode to @${w.telegramBotUsername}",
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    TextButton(onClick = {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/${w.telegramBotUsername}".toUri()))
                                        }
                                    }) { Text("Open @${w.telegramBotUsername}") }
                                }
                            } else {
                                Text("Telegram linked ✓", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Send a gift", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        PrimaryField(giftUser, { giftUser = it }, "Username to gift")
                        Spacer(Modifier.height(8.dp))
                        PrimaryField(giftAmount, { giftAmount = it }, "Amount of UX")
                        Spacer(Modifier.height(8.dp))
                        PrimaryField(giftMessage, { giftMessage = it }, "Message (optional)")
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton("Send gift", enabled = !busy, icon = "send-square") {
                            val amt = giftAmount.toDoubleOrNull() ?: 0.0
                            val uname = giftUser.trim().removePrefix("@")
                            if (uname.isBlank() || amt <= 0) {
                                vm.toast("Enter a username and a positive amount")
                            } else {
                                val before = wallet
                                val pendingTx = UxTx(
                                    id = localId("gift"),
                                    amount = -amt,
                                    reason = "gift_out",
                                    details = giftMessage.trim(),
                                )
                                scope.localFirst(
                                    vm,
                                    "Send gift",
                                    send = {
                                        val res = Api.service().uxGift(UxGiftIn(username = uname, amount = amt, message = giftMessage.trim()))
                                        celebrate += 1
                                        vm.toast("Gift sent — new balance ${fmtUx(res.balanceUx)} UX")
                                        giftUser = ""
                                        giftMessage = ""
                                        load()
                                    },
                                    apply = {
                                        // The UX leaves the balance (and lands in the
                                        // history) before the transfer is confirmed.
                                        wallet = before?.copy(
                                            balanceUx = (before.balanceUx - amt).coerceAtLeast(0.0),
                                            transactions = before.transactions.copy(items = listOf(pendingTx) + before.transactions.items),
                                        )
                                    },
                                    undo = {
                                        wallet = before
                                        vm.toast("Not sent — your UX is still in the balance.")
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("History", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(w.transactions.items, key = { it.id }) { t ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(txLabel(t), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                (if (t.amount >= 0) "+" else "") + fmtUx(t.amount) + " UX",
                                fontWeight = FontWeight.SemiBold,
                                color = if (t.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (t.details.isNotBlank()) {
                            Text(t.details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}


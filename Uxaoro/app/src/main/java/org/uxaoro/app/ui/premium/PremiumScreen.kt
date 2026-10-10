package org.uxaoro.app.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.CustomizationPatch
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.PremiumBuyIn
import org.uxaoro.app.data.PremiumPlan
import org.uxaoro.app.data.PremiumStatus
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.formatTime
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.GlassPanel
import java.util.Locale
import org.uxaoro.app.ui.components.AssetSvgIcon

private fun ux(amount: Double): String =
    String.format(Locale.US, "%.4f", amount).trimEnd('0').trimEnd('.')

@Composable
fun PremiumScreen(vm: AppVm) {
    var status by remember { mutableStateOf<PremiumStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var planToConfirm by remember { mutableStateOf<PremiumPlan?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(clearError: Boolean = true) {
        scope.launch {
            loading = status == null
            runCatching { Api.service().premiumStatus() }
                .onSuccess { fresh ->
                    status = fresh
                    vm.me = vm.me?.copy(
                        premium = fresh.active,
                        customization = fresh.customization,
                        balanceUx = fresh.balanceUx,
                    )
                    Prefs.user = vm.me
                    if (clearError) error = null
                }
                .onFailure { if (clearError || error == null) error = Api.errorMessage(it) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun save(key: String, value: String) {
        if (busy || status?.active != true) return
        scope.launch {
            busy = true
            error = null
            try {
                val result = Api.service().setCustomization(CustomizationPatch(mapOf(key to value)))
                status = status?.copy(customization = result.customization)
                // The PATCH response is authoritative; don't leave the visible
                // profile stale if the follow-up /auth/me request is offline.
                vm.me = vm.me?.copy(customization = result.customization, premium = true)
                Prefs.user = vm.me
                runCatching { Api.service().me() }.onSuccess { vm.me = it; Prefs.user = it }
            } catch (t: Throwable) {
                error = Api.errorMessage(t)
                // The subscription may have expired since status was loaded.
                if (error?.contains("Premium required", ignoreCase = true) == true) {
                    status = null
                    reload()
                }
            } finally {
                busy = false
            }
        }
    }

    BlurScaffold(topBar = {
        DATopBar("✦ Uxaoro Premium", onBack = { vm.back() }, actions = {
            TextButton(onClick = { reload() }, enabled = !busy) {
              AssetSvgIcon("refresh", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
              Spacer(Modifier.width(6.dp))
              Text("Refresh")
            }
        })
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading && status == null) {
                Box(Modifier.fillMaxWidth().height(160.dp)) { LoadingBox() }
            }
            ErrorText(error)
            val st = status
            if (st == null && !loading) {
                Button(onClick = { reload() }) { Text("Retry") }
            } else if (st != null) {
                GlassPanel(Modifier.fillMaxWidth(), intense = true) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (st.active) "Premium active ✓" else "Make it yours",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (st.active) "Valid until ${formatTime(st.premiumUntil)}. Buy a plan to extend it."
                            else "Buy Premium with UX from your wallet. No card is charged in this screen.",
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text("${st.storiesLimit} stories per day  ·  Own up to ${st.communitiesLimit} communities", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Profile themes, colorful names, glow, badges and chat bubbles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Balance: ${ux(st.balanceUx)} UX", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.go(Screen.Wallet) }) { Text("Get UX") }
                        }
                    }
                }
                Text("Plans", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (st.prices.isEmpty()) {
                    Text("No plans available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                st.prices.forEach { plan ->
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${plan.days} days", fontWeight = FontWeight.SemiBold)
                                Text("${ux(plan.ux)} UX", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { planToConfirm = plan },
                                enabled = !busy && st.balanceUx >= plan.ux && plan.days > 0,
                            ) { Text(if (st.active) "Extend" else "Buy") }
                        }
                    }
                }
                if (st.prices.any { st.balanceUx < it.ux }) {
                    Text("Need more UX? Add it in the wallet, then return here to purchase Premium.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                if (st.active) {
                    Text("Customize", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val c = st.customization
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Your choices are saved to your account and appear on other devices.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            ColorSetting("Name color", "name_color", c.nameColor, busy, ::save)
                            ColorSetting("Bio color", "bio_color", c.bioColor, busy, ::save)
                            OptionSetting("Name glow", "glow", st.options.glow, c.glow, busy, ::save)
                            OptionSetting("Name style", "name_style", st.options.nameStyle, c.nameStyle, busy, ::save)
                            OptionSetting("Profile badge", "badge", st.options.badge, c.badge, busy, ::save)
                            OptionSetting("Chat bubble", "bubble", st.options.bubble, c.bubble, busy, ::save)
                            OptionSetting("Profile theme", "profile_theme", st.options.profileTheme, c.profileTheme, busy, ::save)
                        }
                    }
                } else {
                    Text("Customization unlocks with Premium. Saved styles stop displaying after expiry until you renew.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BadgesAndSalesSection(vm, onChanged = {
                    scope.launch {
                        runCatching { status = Api.service().premiumStatus() }
                    }
                })
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    planToConfirm?.let { plan ->
        AlertDialog(
            onDismissRequest = { if (!busy) planToConfirm = null },
            title = { Text(if (status?.active == true) "Extend Premium?" else "Buy Premium?") },
            text = { Text("Spend ${ux(plan.ux)} UX for ${plan.days} days? This purchase is charged immediately to your UX balance.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    planToConfirm = null
                    if (busy) return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val receipt = Api.service().premiumBuy(PremiumBuyIn(plan.days))
                            val updated = runCatching { Api.service().premiumStatus() }.getOrNull()
                            status = updated ?: status?.copy(active = true, premiumUntil = receipt.premiumUntil, balanceUx = receipt.balanceUx)
                            runCatching { Api.service().me() }.onSuccess { vm.me = it; Prefs.user = it }
                                .onFailure {
                                    vm.me = vm.me?.copy(premium = true, balanceUx = receipt.balanceUx)
                                    Prefs.user = vm.me
                                }
                            if (updated == null) error = "Purchase succeeded. Refresh to update your limits."
                            vm.toast("Premium extended by ${plan.days} days")
                        } catch (t: Throwable) {
                            error = Api.errorMessage(t)
                            reload(clearError = false) // Refresh balance without hiding the purchase error.
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Spend ${ux(plan.ux)} UX") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { planToConfirm = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionSetting(
    label: String,
    key: String,
    values: List<String>,
    current: String,
    busy: Boolean,
    onSave: (String, String) -> Unit,
) {
    val normalized = current.takeUnless { it == "normal" || it == "default" } ?: ""
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = normalized.isEmpty(),
                onClick = { onSave(key, "") },
                enabled = !busy,
                label = { Text(if (key == "glow" || key == "badge") "None" else "Default") },
            )
            values.filterNot { it.isBlank() || it == "normal" || it == "default" }.distinct().forEach { v ->
                FilterChip(
                    selected = normalized == v,
                    onClick = { onSave(key, v) },
                    enabled = !busy,
                    label = { Text(v.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

private val palette = listOf("#FF6B6B", "#FFD166", "#5DDFF4", "#4CE0A1", "#AF87FF", "#FF8EBD", "#FFFFFF", "#222222")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSetting(
    label: String,
    key: String,
    current: String,
    busy: Boolean,
    onSave: (String, String) -> Unit,
) {
    var hex by remember(current) { mutableStateOf(current) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            palette.forEach { value ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(safePremiumColor(value) ?: Color.Gray)
                        .border(if (hex.equals(value, true)) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable(enabled = !busy) { hex = value },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hex,
                onValueChange = { if (it.length <= 7) hex = it },
                label = { Text("Hex #RRGGBB") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = hex.isNotBlank() && !validPremiumHex(hex),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(key, hex.uppercase(Locale.US)) }, enabled = !busy && validPremiumHex(hex) && !hex.equals(current, true)) { Text("Save") }
        }
        if (current.isNotBlank()) {
            TextButton(onClick = { onSave(key, "") }, enabled = !busy) { Text("Reset to default") }
        }
    }
}

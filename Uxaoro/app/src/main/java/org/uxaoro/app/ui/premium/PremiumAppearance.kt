package org.uxaoro.app.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.BadgeApplyIn
import org.uxaoro.app.data.BadgeOut
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.PremiumCustomization
import org.uxaoro.app.data.SaleApplyIn
import org.uxaoro.app.data.SaleOut
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.components.inkButtonColors
import org.uxaoro.app.ui.components.inkTextButtonColors
import org.uxaoro.app.ui.theme.liquidGlass

/** Only server-authorized, currently Premium users display paid cosmetics. */
fun visibleCustomization(user: UserPublic): PremiumCustomization =
    if (user.premium) user.customization else PremiumCustomization()

private val hexColor = Regex("^#[a-fA-F0-9]{6}$")

fun safePremiumColor(value: String): Color? =
    if (hexColor.matches(value)) Color(0xFF000000.toInt() or value.drop(1).toInt(16)) else null

fun validPremiumHex(value: String): Boolean = hexColor.matches(value)

private fun glowColor(glow: String): Color? = when (glow) {
    "coral" -> Color(0xFFFF6B6B)
    "gold" -> Color(0xFFFFD166)
    "emerald" -> Color(0xFF4CE0A1)
    "violet" -> Color(0xFFAF87FF)
    "cyan" -> Color(0xFF5DDFF4)
    "rose" -> Color(0xFFFF8EBD)
    else -> null
}

/** Match the web's named profile backgrounds; user supplied values never become brushes. */
fun premiumProfileBrush(theme: String): Brush? = when (theme) {
    "midnight" -> Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF141B38)))
    "sunset" -> Brush.verticalGradient(listOf(Color(0xFF3A1C45), Color(0xFF83442C)))
    "forest" -> Brush.verticalGradient(listOf(Color(0xFF12301F), Color(0xFF254A2C)))
    "ocean" -> Brush.verticalGradient(listOf(Color(0xFF0D2B45), Color(0xFF1A5E70)))
    "mono" -> Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF1C1C1C)))
    else -> null
}

@Composable
fun PremiumName(
    user: UserPublic,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    defaultColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val c = visibleCustomization(user)
    val gradient = c.nameStyle == "gradient"
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = if (c.nameStyle == "bold") FontWeight.ExtraBold else FontWeight.SemiBold,
        fontStyle = if (c.nameStyle == "italic") FontStyle.Italic else FontStyle.Normal,
        color = if (gradient) Color.Unspecified else safePremiumColor(c.nameColor) ?: defaultColor,
        shadow = glowColor(c.glow)?.let { Shadow(it, Offset.Zero, 12f) },
    )
    Text(
        user.displayName.ifBlank { user.username },
        modifier = modifier,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PremiumBadges(user: UserPublic, compact: Boolean = false) {
    val badge = if (user.premium) {
        visibleCustomization(user).badge.takeIf {
            it in setOf("premium", "vip", "legend", "creator", "gamer")
        }
    } else null
    val earnedBadges = user.badges.filter { it.applied }
    if (!user.premium && earnedBadges.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (user.premium) {
            BadgePill(if (compact) "✦" else "✦ Premium")
            if (badge != null && !compact) BadgePill(badge.replaceFirstChar { it.uppercase() })
        }
        val shown = if (compact) earnedBadges.take(2) else earnedBadges
        shown.forEach { b ->
            BadgeIconOnly(b)
        }
    }
}

@Composable
fun BadgeIconOnly(badge: BadgeOut) {
    val hasImg = badge.imageWebp.isNotBlank() && badge.imageWebp != "/uploads/badges/default.webp"
    if (hasImg) {
        AsyncImage(
            model = Api.mediaUrl(badge.imageWebp),
            contentDescription = badge.name,
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        BadgePill("✦")
    }
}

@Composable
fun BadgePill(label: String, onRemove: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "✕",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRemove() },
            )
        }
    }
}

fun formatSalePerks(sale: SaleOut): String {
    val el = sale.items
    val parts = mutableListOf<String>()
    runCatching {
        if (el != null && el.isJsonObject) {
            val obj = el.asJsonObject
            val pDays = runCatching { obj.get("premium_days")?.asInt }.getOrNull()
                ?: if (runCatching { obj.get("premium")?.asBoolean }.getOrNull() == true) 30 else 0
            if (pDays > 0) parts.add("${pDays}d Premium")
            val bArr = obj.get("badges")
            if (bArr != null && bArr.isJsonArray && bArr.asJsonArray.size() > 0) {
                parts.add("${bArr.asJsonArray.size()} badge(s)")
            }
        }
    }
    val price = sale.priceUx
    parts.add(if (price > 0.0) "${if (price % 1.0 == 0.0) price.toInt() else price} UX" else "Free")
    return if (parts.isEmpty()) "Special offer" else parts.joinToString(" · ")
}

@Composable
fun BadgesAndSalesSection(
    vm: AppVm,
    onChanged: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var myBadges by remember { mutableStateOf<List<BadgeOut>>(vm.me?.badges ?: emptyList()) }
    var mySales by remember { mutableStateOf<List<SaleOut>>(emptyList()) }
    var promoCode by remember { mutableStateOf("") }

    fun reloadAll() {
        scope.launch {
            runCatching { Api.service().myBadges().items }.onSuccess { myBadges = it }
            runCatching { Api.service().mySales().items }.onSuccess { mySales = it }
            runCatching { Api.service().me() }.onSuccess {
                vm.me = it
                Prefs.user = it
                if (myBadges.isEmpty() && it.badges.isNotEmpty()) myBadges = it.badges
            }
            onChanged?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    val appliedCount = myBadges.count { it.applied }

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "My Badges ($appliedCount/5 applied)",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (vm.me?.isStaff == true) {
                TextButton(onClick = { vm.go(Screen.Admin) }, colors = inkTextButtonColors()) {
                    Text("Control Panel")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (myBadges.isEmpty()) {
            Text(
                "No badges yet. Redeem a promocode or apply a sale below.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                myBadges.forEach { b ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (b.applied) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                scope.launch {
                                    runCatching { Api.service().toggleMyBadge(b.id) }
                                        .onSuccess { res ->
                                            vm.toast(if (res.applied) "Badge applied!" else "Badge unapplied")
                                            reloadAll()
                                        }
                                        .onFailure { vm.toast(Api.errorMessage(it)) }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        BadgeIconOnly(b)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            b.name,
                            color = if (b.applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (b.applied) "Unapply" else "Apply",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryField(
            value = promoCode,
            label = "Enter badge promocode",
            onValue = { promoCode = it },
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Apply badge", icon = "medal-star") {
            val code = promoCode.trim()
            if (code.isBlank()) {
                vm.toast("Enter a promocode")
                return@PrimaryButton
            }
            scope.launch {
                runCatching { Api.service().applyBadge(BadgeApplyIn(code)) }
                    .onSuccess {
                        promoCode = ""
                        vm.toast("Badge applied!")
                        reloadAll()
                    }
                    .onFailure { vm.toast(Api.errorMessage(it)) }
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "My Sales & Offers (${mySales.size})",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (vm.me?.isStaff == true) {
                TextButton(onClick = { vm.go(Screen.Admin) }, colors = inkTextButtonColors()) {
                    Text("Control Panel")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (mySales.isEmpty()) {
            Text(
                "No active sales right now.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mySales.forEach { s ->
                    val priceLabel = if (s.priceUx > 0.0) {
                        val pStr = if (s.priceUx % 1.0 == 0.0) s.priceUx.toInt().toString() else s.priceUx.toString()
                        "Buy ($pStr UX)"
                    } else {
                        "Apply sale"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "✦ ${s.name}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                formatSalePerks(s),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { Api.service().applySale(SaleApplyIn(s.id)) }
                                        .onSuccess {
                                            vm.toast("Sale applied!")
                                            reloadAll()
                                        }
                                        .onFailure { vm.toast(Api.errorMessage(it)) }
                                }
                            },
                            colors = inkButtonColors(),
                        ) {
                            Text(priceLabel)
                        }
                    }
                }
            }
        }
    }
}


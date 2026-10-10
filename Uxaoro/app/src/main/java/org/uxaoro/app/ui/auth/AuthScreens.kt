package org.uxaoro.app.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.uxaoro.app.ui.components.inkTextButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.uxaoro.app.R
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.CaptchaVerifyIn
import org.uxaoro.app.data.LoginIn
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.RegisterIn
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.AmbientGlow
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.NumericCodePad
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.components.ThemeAccentPickers
import org.uxaoro.app.ui.motion.fadeUp
import org.uxaoro.app.ui.motion.TypewriterText

@Composable
fun LoginScreen(vm: AppVm) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val needTotp = pending.isNotBlank()
    val awaitingCaptcha = vm.captchaPurpose == AppVm.CAPTCHA_LOGIN

    suspend fun submitLogin(captchaPass: String, captchaResult: AppVm.CaptchaResult? = null) {
        loading = true
        err = null
        try {
            // Exchange and consume the CAPTCHA in this screen's coroutine. The
            // browser callback is an Android-only round trip; keep it separate
            // from LaunchedEffect so clearing its state cannot cancel the HTTP
            // verification request.
            val pass = captchaResult?.let { exchangeCaptchaToken(it) } ?: captchaPass
            val pair = if (needTotp) {
                Api.service().login2fa(org.uxaoro.app.data.TotpLoginIn(pending, totp))
            } else {
                Api.service().login(LoginIn(login.trim(), password, pass))
            }
            if (pair.requires2fa && pair.pendingToken.isNotBlank()) {
                pending = pair.pendingToken
            } else {
                Prefs.saveSession(pair)
                // The typed password is only available here: hand it to the
                // view model so it can unlock (or refresh) the E2EE key
                // backup before a fresh keypair is generated.
                vm.onLoggedIn(password)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            err = Api.errorMessage(t)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(vm.captchaResult, needTotp) {
        val result = vm.captchaResult
        if (!needTotp && result?.purpose == AppVm.CAPTCHA_LOGIN) {
            // Clearing this key from inside the effect would cancel the effect
            // and its suspended network call. Launch independently, then clear
            // the one-shot result after login has finished.
            scope.launch {
                try {
                    submitLogin("", result)
                } finally {
                    vm.clearCaptchaResult()
                }
            }
        }
    }

    AuthScaffold(vm, title = if (needTotp) "Authenticator code" else "Welcome back") {
        if (!needTotp) {
            PrimaryField(login, { login = it }, "Username or email")
            Spacer(Modifier.height(10.dp))
            PrimaryField(password, { password = it }, "Password", visual = PasswordVisualTransformation())
            if (awaitingCaptcha) {
                Text(
                    "Complete the Cloudflare CAPTCHA in your browser, then return here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            Text(
                "Enter the 6-digit code from your authenticator app.",
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            NumericCodePad(
                code = totp,
                onCodeChange = { totp = it },
                modifier = Modifier.fadeUp(),
            )
        }
        ErrorText(err)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            if (needTotp) "Verify" else if (awaitingCaptcha) "Open CAPTCHA again" else "Log in",
            enabled = !loading && if (needTotp) totp.length == 6 else login.isNotBlank() && password.length >= 6,
            icon = if (needTotp) "check-circle" else if (awaitingCaptcha) "shield-check" else "login",
        ) {
            if (needTotp) {
                scope.launch { submitLogin("") }
            } else {
                err = null
                openCaptcha(context, vm, AppVm.CAPTCHA_LOGIN)?.let { err = it }
            }
        }
        if (needTotp) {
            TextButton(onClick = { pending = ""; totp = ""; err = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to password")
            }
        } else {
            Spacer(Modifier.height(10.dp))
            SocialOAuthRow(context = context, onError = { err = it })
            TextButton(
                onClick = {
                    vm.cancelCaptcha(AppVm.CAPTCHA_LOGIN)
                    vm.go(Screen.Register)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Create an account")
            }
        }
    }
}

private fun parseAgeYears(dob: String): Int? {
    val clean = dob.trim()
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(clean)) return null
    return runCatching {
        val birth = java.time.LocalDate.parse(clean)
        val today = java.time.LocalDate.now()
        java.time.Period.between(birth, today).years
    }.getOrNull()
}

@Composable
fun RegisterScreen(vm: AppVm) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    var sentTo by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val needEmail = pending.isNotBlank()
    val awaitingCaptcha = vm.captchaPurpose == AppVm.CAPTCHA_REGISTER
    val ageYears = parseAgeYears(dob)

    suspend fun submitRegistration(captchaPass: String, captchaResult: AppVm.CaptchaResult? = null) {
        loading = true
        err = null
        try {
            // Keep verification outside the keyed LaunchedEffect: clearing or
            // changing its key would cancel a suspended CAPTCHA request.
            val pass = captchaResult?.let { exchangeCaptchaToken(it) } ?: captchaPass
            val pair = if (needEmail) {
                Api.service().registerVerify(org.uxaoro.app.data.EmailVerifyIn(pending, code))
            } else {
                val d = dob.trim()
                if (d.isBlank()) {
                    err = "Date of birth is required (YYYY-MM-DD)"
                    return
                }
                val age = parseAgeYears(d)
                if (age == null) {
                    err = "Enter date of birth as YYYY-MM-DD"
                    return
                }
                if (age < 13) {
                    err = "You must be at least 13 years old to register"
                    return
                }
                val u = username.trim()
                Api.service().register(RegisterIn(u, email.trim(), password, u, d, pass))
            }
            if (pair.requiresEmail && pair.pendingToken.isNotBlank()) {
                pending = pair.pendingToken
                sentTo = pair.email.ifBlank { email.trim() }
            } else {
                Prefs.saveSession(pair)
                // A brand new account gets its key backup right away, so
                // the very first device is not the only one that can read
                // its own messages.
                vm.onLoggedIn(password)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            err = Api.errorMessage(t)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(vm.captchaResult, needEmail) {
        val result = vm.captchaResult
        if (!needEmail && result?.purpose == AppVm.CAPTCHA_REGISTER) {
            // Keep the response until the standalone job completes; resetting
            // the effect key before POST /auth/captcha/verify returned used to
            // cancel this request on Android.
            scope.launch {
                try {
                    submitRegistration("", result)
                } finally {
                    vm.clearCaptchaResult()
                }
            }
        }
    }

    AuthScaffold(vm, title = if (needEmail) "Check your email" else "Join the night market") {
        if (!needEmail) {
            PrimaryField(username, { username = it }, "Username")
            Spacer(Modifier.height(10.dp))
            PrimaryField(email, { email = it }, "Email")
            Spacer(Modifier.height(10.dp))
            PrimaryField(password, { password = it }, "Password (min 6)", visual = PasswordVisualTransformation())
            Spacer(Modifier.height(10.dp))
            PrimaryField(passwordConfirm, { passwordConfirm = it }, "Confirm password", visual = PasswordVisualTransformation())
            Spacer(Modifier.height(10.dp))
            PrimaryField(dob, { dob = it }, "Date of birth (YYYY-MM-DD, required 13+)")
            if (dob.isNotBlank() && ageYears == null) {
                Text(
                    "Enter date of birth in YYYY-MM-DD format",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else if (ageYears != null && ageYears < 13) {
                Text(
                    "You must be at least 13 years old to register",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (passwordConfirm.isNotEmpty() && passwordConfirm != password) {
                Text(
                    "Passwords do not match",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (awaitingCaptcha) {
                Text(
                    "Complete the Cloudflare CAPTCHA in your browser, then return here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            Text(
                "We sent a 6-digit code to ${sentTo.ifBlank { "your email" }}. Enter it to finish signing up.",
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            // This step is intentionally an in-app keypad: no system numeric
            // keyboard appears while confirming the email code.
            NumericCodePad(
                code = code,
                onCodeChange = { code = it },
                modifier = Modifier.fadeUp(),
            )
        }
        ErrorText(err)
        Spacer(Modifier.height(16.dp))
        if (!needEmail) {
            LegalConsent(buttonLabel = "Sign up")
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(
            if (needEmail) "Verify email" else if (awaitingCaptcha) "Open CAPTCHA again" else "Sign up",
            icon = if (needEmail) "check-read" else if (awaitingCaptcha) "shield-check" else "user-plus",
            enabled = !loading && if (needEmail) {
                code.length == 6
            } else {
                username.length >= 3 && email.contains("@") && password.length >= 6 && password == passwordConfirm && dob.isNotBlank() && (ageYears != null && ageYears >= 13)
            },
        ) {
            if (needEmail) {
                scope.launch { submitRegistration("") }
            } else {
                val age = parseAgeYears(dob)
                if (dob.isBlank()) {
                    err = "Date of birth is required (YYYY-MM-DD)"
                    return@PrimaryButton
                }
                if (age == null) {
                    err = "Enter date of birth as YYYY-MM-DD"
                    return@PrimaryButton
                }
                if (age < 13) {
                    err = "You must be at least 13 years old to register"
                    return@PrimaryButton
                }
                err = null
                openCaptcha(context, vm, AppVm.CAPTCHA_REGISTER)?.let { err = it }
            }
        }
        if (needEmail) {
            TextButton(
                onClick = {
                    scope.launch {
                        loading = true
                        err = null
                        try {
                            Api.service().registerResend(org.uxaoro.app.data.EmailPendingIn(pending))
                            err = "New code sent"
                        } catch (t: Throwable) {
                            err = Api.errorMessage(t)
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Resend code") }
            TextButton(
                onClick = { pending = ""; code = ""; sentTo = ""; err = null },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Back") }
        } else {
            Spacer(Modifier.height(10.dp))
            SocialOAuthRow(context = context, onError = { err = it })
            TextButton(
                onClick = {
                    vm.cancelCaptcha(AppVm.CAPTCHA_REGISTER)
                    vm.go(Screen.Login)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Already have an account")
            }
        }
    }
}

@Composable
private fun SocialOAuthRow(context: Context, onError: (String) -> Unit) {
    fun launchOAuth(provider: String) {
        runCatching {
            val base = Prefs.apiBase.trimEnd('/')
            val url = "$base/auth/$provider?redirect_to=uxaoro%3A%2F%2Foauth"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            onError("Could not open browser for $provider sign-in")
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "or continue with",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = { launchOAuth("google") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google",
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Google", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { launchOAuth("github") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("GitHub", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun openCaptcha(context: Context, vm: AppVm, purpose: String): String? {
    return try {
        val url = vm.beginCaptcha(purpose)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        null
    } catch (_: Throwable) {
        vm.cancelCaptcha(purpose)
        "Couldn't open a browser for the CAPTCHA. Install or enable a browser and try again."
    }
}

/**
 * Returns a CAPTCHA pass ready for login/register.
 *
 * The browser-hosted CAPTCHA page hands the raw Turnstile response back over
 * the fixed deep link (`turnstile_token`) and the app exchanges it here, from
 * its own network stack. The server binds the issued pass to the caller's IP,
 * and the browser and the app are two different network stacks on Android
 * (different IPv4/IPv6 choices, the app-only proxy, per-app VPN routing, or
 * different Cloudflare edge IPs), so exchanging in the browser regularly
 * produced passes the app's own login request could not consume. Exchanging
 * here keeps issue + consume on one network path. Legacy backends that still
 * deep-link an exchanged `captcha_pass` are used as-is.
 */
private suspend fun exchangeCaptchaToken(result: AppVm.CaptchaResult): String {
    if (result.turnstileToken.isBlank()) return result.captchaPass
    return Api.service()
        .captchaVerify(CaptchaVerifyIn(result.turnstileToken, result.state))
        .captchaPass
}

private const val TERMS_OF_USE_URL = "https://uxaoro.fourtexec.site/tou.txt"
private const val PRIVACY_POLICY_URL = "https://uxaoro.fourtexec.site/pp.txt"

/**
 * "By tapping Sign up, you accept the Terms of Use and Privacy Policy." —
 * both links open in the browser.
 */
@Composable
private fun LegalConsent(buttonLabel: String) {
    val uriHandler = LocalUriHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val link = SpanStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
    )
    val text = buildAnnotatedString {
        append("By tapping \u201C$buttonLabel\u201D, you accept the ")
        pushStringAnnotation(tag = "url", annotation = TERMS_OF_USE_URL)
        withStyle(link) { append("Terms of Use") }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "url", annotation = PRIVACY_POLICY_URL)
        withStyle(link) { append("Privacy Policy") }
        pop()
        append(".")
    }
    ClickableText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(
            color = muted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        ),
        onClick = { offset ->
            text.getStringAnnotations(tag = "url", start = offset, end = offset)
                .firstOrNull()
                ?.let { runCatching { uriHandler.openUri(it.item) } }
        },
    )
}

@Composable
private fun AuthScaffold(vm: AppVm, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        org.uxaoro.app.ui.AmbientGlow()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    org.uxaoro.app.ui.components.UxaoroLogo(
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Uxaoro",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(8.dp))
                    org.uxaoro.app.ui.components.BetaTag()
                }
                IconButton(onClick = { vm.openSettingsFromAuth() }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fadeUp()) {
                Text(
                    "Uxaoro",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(10.dp))
                org.uxaoro.app.ui.components.BetaTag()
            }
            TypewriterText(
                title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier.fadeUp(50),
                charDelayMillis = 38,
            )
            Spacer(Modifier.height(24.dp))
            // Solid card: animated background details must not show through
            // rounded form surfaces.
            val cardShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .fadeUp(90, fromY = 22f)
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp), content = content)
            }
        }
    }
}

private fun Modifier.fillMaxWidthPad() = this.then(Modifier.fillMaxSize().height(48.dp))

@Composable
fun PreSettingsScreen(vm: AppVm) {
    SettingsBody(vm, fromAuth = true)
}

@Composable
fun DevSettingsScreen(vm: AppVm) {
    var host by remember { mutableStateOf(Prefs.apiBase) }
    var saved by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        TextButton(onClick = { vm.back() }, colors = inkTextButtonColors()) { Text("← Back") }
        Text("Developer settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Advanced options. Handle with care.", color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        org.uxaoro.app.ui.theme.GlassPanel(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), intense = true) {
            Column(Modifier.padding(16.dp)) {
                PrimaryField(host, { host = it }, "API host")
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Save API host", icon = "check-circle") {
                    val v = host.trim().trimEnd('/')
                    if (v.startsWith("http://") || v.startsWith("https://")) {
                        Prefs.apiBase = v
                        saved = "Saved. Using $v"
                    } else {
                        saved = "Host must start with http:// or https://"
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        Prefs.resetApi()
                        host = Prefs.apiBase
                        saved = "Reset to default"
                    },
                    colors = inkTextButtonColors(),
                ) { Text("Reset to ${Prefs.DEFAULT_API}") }
                if (saved != null) Text(saved!!, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(16.dp))
        org.uxaoro.app.ui.theme.GlassPanel(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), intense = true) {
            Column(Modifier.padding(16.dp)) {
                ThemeAccentPickers { vm.themeBump() }
            }
        }
        Spacer(Modifier.height(20.dp))
        VersionTap(onDev = {})
        Text("Client v${Prefs.APP_VERSION.removePrefix("v")}  ·  default ${Prefs.DEFAULT_API}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SettingsBody(vm: AppVm, fromAuth: Boolean) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        TextButton(onClick = { vm.back() }, colors = inkTextButtonColors()) { Text("← Back") }
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        ThemeAccentPickers { vm.themeBump() }
        Spacer(Modifier.height(18.dp))
        Text(
            "PROXY (UXAORO ONLY)",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        org.uxaoro.app.ui.theme.GlassPanel(shape = RoundedCornerShape(16.dp), intense = true) {
            Column(Modifier.padding(16.dp)) {
                org.uxaoro.app.ui.settings.ProxySettingsSection(
                    onToast = { msg -> android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show() },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        VersionTap(onDev = { vm.openDev(if (fromAuth) Screen.PreSettings else Screen.Settings) })
    }
}

@Composable
fun VersionTap(onDev: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    Column {
        Text(
            "Uxaoro v${Prefs.APP_VERSION.removePrefix("v")}",
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - last > 1600) taps = 0
                    last = now
                    taps++
                    if (taps >= 10) {
                        taps = 0
                        onDev()
                    }
                },
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

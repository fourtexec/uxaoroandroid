# Uxaoro Android

Open the **Uxaoro** folder (this directory’s `Uxaoro/` subfolder) in Android Studio.

- Language: Kotlin + Jetpack Compose
- minSdk 26, targetSdk 34, JDK 17
- Default API: `https://api.fourtexec.site`

Login/signup gear → theme, accent, version. Tap version 10× for developer settings (API host).

## Local-first (optimistic) actions

Writes answer the tap before the network does. `ui/LocalFirst.kt` runs one
mutation: `apply` the change to the in-memory state, `send` the request, and on
failure `undo` it and report (`Snackbar` + `Log.e` under `uxaoro`). A trailing
`{ ... }` after a call inside a single lambda parses as a trailing lambda
argument, so `apply` and `undo` are **separate, named parameters**; keep that
shape at every call site — dropping the `undo = ` label is a compile error, and
`tools/check_android_kotlin.py` fails the pre-flight on it.

Rows that exist before their server id does are held by `AppVm`: `pendingIds`
("sending…" markers), `draftPosts`, `draftStories` and `pendingCommunity`. A
draft leaves its list when the server row arrives (the feed removes a draft once
the fetched page carries its id) or when the write fails, in which case the
composer reopens with the text still in it.

Covered: publishing posts/comments/stories, like / reaction / repost / save to
Saved Messages / poll vote / pin / delete, chat send / react / delete / pin /
mute / rename / members, friend request / accept / decline / unfriend, block,
community create / join / leave / details / photos / access / invites / roles / kick, profile & privacy saves, gift UX,
daily claim, badge toggle, customization, sessions and device revoke.

Not covered on purpose: sign-in, password / 2FA, key-backup writes, payment
redirects, promocode redemptions and the admin screens.

## Community management

Open a community → **Manage** (owner/admin), or **Members → Manage**. Android
uses the same endpoints and permissions as the web control panel:

- **Details:** name and description; only the owner can change the unique
  username/public link.
- **Look:** avatar and cover photo, both through the built-in photo editor.
- **Access:** open/closed community, and who can post or invite (members,
  editors including moderators, or admins including the owner).
- **Members & roles:** invite by username (subject to the target's privacy),
  explicitly choose member/editor/moderator/admin, and confirm removals.
  Admins cannot grant admin or remove equal/higher-ranking members; owner and
  self controls are protected. Invitations are also available to other members
  when the community's invite policy allows them.
- **Danger zone:** owner-only deletion, with a warning that all posts are
  removed. Success returns to Circles and removes stale community routes/cache.

Changes preview immediately and roll back on failure, with the server's error
shown inline. Failed saves keep the draft text. Saves are serialized and Back
waits for an in-flight mutation, so a second edit cannot overwrite its rollback.
Closed-community guests see an invitation hint instead of hidden posts/members,
and the post button follows the posting policy. These protections complement
server authorization; the app never treats an offline cached role as authority
for opening the management form.

Regression coverage: `./gradlew testDebugUnitTest` in `android/Uxaoro` checks
role-policy gates, serialization and the real Retrofit endpoints (MockWebServer).
`python backend/tests/test_community_settings.py` exercises the server contracts
against temporary database/upload directories. Android CI runs the unit tests
before assembling the APKs.

## Saved Messages

A private E2EE chat with yourself — the same one the web client uses — is where
"saving" lives now. It is created on first access (`GET /chats/saved`, a chat row
with `kind = "saved"`), it leads the chat list, it shows no presence, and it
cannot be left or deleted. The old **Saved posts** screen and the **Cloud Notes**
screen are gone with their routes; the server answers those endpoints with
`410 Gone`.

Saving a **post**: `⋯` → *Forward to Saved Messages*. Saving a **message**:
long-press the bubble → *Save to Saved Messages* (text only — an attachment's
ciphertext is bound to the key of the chat it was sent in, so a copy could never
be decrypted).

There is no save endpoint. `ui/SavedMessages.kt` encrypts one payload to this
account's own published key and posts it as an ordinary message, with the
reference inside the ciphertext:

    { text, media_url, media_type, forward: { post_id, author, username, created_at } }

That is the shape `forwardPostToSaved()` in `backend/web/app.js` writes, so a
copy made on a phone opens in a browser and vice versa; the two parsers tolerate
each other's extra keys. The server only ever stores ciphertext.

The chat *is* the record, so the local "this is saved" markers
(`AppVm.savedMessages` / `savedPosts`, add-only) are rebuilt from it:
`syncSavedMarkers()` with the chat list and `syncSavedRefs()` when the chat is
open. That is what puts the bookmark back on a bubble or a post after a relogin
on a fresh device.

## Pre-flight: catch Kotlin breakage without a build

Gradle is the real check, but it is far too slow to run while editing.
`tools/check_android_kotlin.py` compiles the app sources with `kotlinc` and **no
classpath at all**, which is enough to report everything a missing dependency
cannot explain:

* anything that does not parse — a stray `private set` left at class level, an
  unbalanced brace, a broken top-level declaration;
* writes to a `val`, or to a property whose setter is `private`;
* references to a name the project declares but that is not in scope at the
  call site (e.g. reading `securityAlerts` off a class that does not have it).

Two further checks run **before** the compiler and need no classpath at all,
because a missing dependency cannot explain them away:

* a block immediately after a call to a function that has no lambda parameter
  (`vm.markPending(lid)` followed by `{ ... }`) — Kotlin parses it as a trailing
  lambda argument. That is how a missing `undo =` label once put a rollback body
  inside `markPending()` and failed CI;
* `x = Api.service().foo()` where `x`'s declared state type cannot be what
  `foo()` returns — read straight out of `data/Api.kt` and the declaring file.
  It is the only way to see `inferred type is OkOut but ChatOut? was expected`
  without a classpath, because `remember { mutableStateOf<T>(...) }` has no type
  when the androidx delegate does not resolve.

```bash
python3 tools/check_android_kotlin.py              # $KOTLINC, /tmp/kt/kotlinc or PATH
python3 tools/check_android_kotlin.py --no-kotlinc # source checks only, no compiler
python3 tools/check_android_kotlin.py --heap 2g    # bigger heap for slow machines
python3 tools/check_android_kotlin.py --self-test  # exercise the checks on fixtures
```

The `androidx.*` / `android.*` / `retrofit2.*` errors caused by the missing
classpath are filtered out automatically (the run prints how many it ignored, so
nothing is hidden silently). Type errors that involve Compose-typed values — a
`Boolean?` passed where a `Boolean` is expected, say — are **not** visible
without the real classpath, so CI still owns those — the API-assignment check
above covers the ones that can be decided from the sources alone. `--self-test`
runs every source check over known-bad and known-good fixtures, so the guards
themselves cannot go quietly blind. Getting `kotlinc`:
`backend/tests/android_e2ee_harness.sh` installs one into `/tmp/kt`.

## APK updates and signing

Android only installs an APK over an existing app when the package ID and signing certificate match, and the new `versionCode` is not lower. The release workflow previously fell back to the runner's debug key, which is not a stable release signature. The build now keeps debug APKs separate (`org.uxaoro.app.debug`) and only makes a release APK when a stable signing keystore is configured. CI increments `versionCode` on each workflow run.

Create one release keystore on a trusted computer and keep an offline backup:

```bash
keytool -genkeypair -v -keystore uxaoro-release.jks -storetype JKS \
  -alias uxaoro-release -keyalg RSA -keysize 2048 -validity 10000
```

Add these four **GitHub Actions repository secrets** under **Settings → Secrets and variables → Actions**:

- `UXAORO_KEYSTORE_BASE64` — base64 of the complete `.jks` file (`base64 -w 0 uxaoro-release.jks` on Linux; on macOS use `base64 < uxaoro-release.jks | tr -d '\n'`).
- `UXAORO_KEYSTORE_PASSWORD`
- `UXAORO_KEY_ALIAS` — `uxaoro-release` from the command above.
- `UXAORO_KEY_PASSWORD`

Never commit the keystore or put its passwords in the repository. Until those secrets are configured, Actions publishes only a **debug** APK. After adding the secrets, run **Actions → Android → Run workflow** on `main`; install `Uxaoro-latest.apk` (or the versioned APK for a tag) for normal use, not the `-debug.apk` asset.

**One-time migration warning:** if the APK currently on the phone was installed from the old CI release and you do not have the key that signed it, Android cannot update it in place with a new key. Before uninstalling, pair another trusted device in **Settings → E2EE & Devices → Linked devices** and verify that it received the private key. After uninstalling the old app and installing the first stable-signed release, pair the phone back to that trusted device to restore the key. Without this transfer, encrypted chat history may become unreadable. Use the same release keystore for every future release. If you recover the old signing keystore, use it instead and no reinstall should be needed.

## Uxaoro Premium

From **You → Get/Manage Premium**, **Settings → Uxaoro Premium**, or the **UX Wallet**, open the Premium screen. Plans, current balance, subscription expiry, and account limits come from `GET /premium/status`. Confirm a plan to spend UX via `POST /premium/buy`; renewals extend an active subscription. If you need UX first, open the existing UX Wallet (Telegram/CryptoBot invoice flow).

While active, the screen edits the seven server-side options: name color, bio color, name glow, name style, profile badge, chat bubble, and profile theme. Presets and arbitrary `#RRGGBB` values are supported; each setting can be reset. These settings follow your account to other devices and appear on public profiles, member lists, posts, and sender-specific chat bubbles. They stop rendering when the server reports Premium expired. The story and community composers show the applicable server-provided limits (4/40 stories per day and 40/4000 owned communities, respectively); the backend enforces the quotas.

The Premium implementation does **not** add or change Developer Account/OAuth screens or the existing hidden developer settings.

Manual checks on a device: open Premium with 0 UX (plans are disabled and Wallet is accessible); buy UX in Wallet; purchase a plan, renew it, edit and reset all options; check another account's profile and a chat with that account; verify that cosmetics disappear after expiry. Purchases and customization require a running backend with the Premium routes.

## App updates (upd.manifest.json)

The Android client updates itself from **`https://fourtexec.site/downloads/uxaoro/upd/upd.manifest.json`**:

1. The manifest is fetched on start / resume (at most once every 6 hours, switchable in **More → App updates → Check automatically**) and on demand.
2. The published `version_code` is compared with the installed one; the published `apk.package` must match `org.uxaoro.app`.
3. The APK is streamed into `cacheDir/updates/`, its **size and SHA-256 are verified** against the manifest, and only then is it handed to the system installer (`ACTION_VIEW` + `application/vnd.android.package-archive` via `FileProvider`). Android asks the user to confirm; on Android 8+ the app requests the *install unknown apps* permission for its own package (`REQUEST_INSTALL_PACKAGES`).
4. If the file is already downloaded, the prompt switches to **Install** — no second download.

### Normal vs required

* `"severity": "normal"` — the prompt can be postponed (*Later*, this session) or skipped (*Skip this version*, remembered). The publisher can promote it later.
* `"severity": "required"` — a blocking dialog: no back press, no tap outside, no "Later". The app is usable again only after the APK is installed (debug builds get an *Ignore (debug)* escape hatch because a release APK cannot replace a debug build).
* A normal update is promoted to required automatically when the device is below `min_supported_version_code` / `min_supported_version`, when `mandatory_from` has passed, or with `"required": true`.

Debug builds never auto-check (`BuildConfig.DEBUG` guards it) — verify the flow on a release build, since Android only replaces an app whose package id and signing certificate match and whose `versionCode` is not lower (see “APK updates and signing” above).

Code: `app/src/main/java/org/uxaoro/app/upd/Updater.kt` (engine),
`…/upd/UpdateManifest.kt` (manifest parser, `severity` → normal/required,
version floors, `mandatory_from`), `…/upd/ApkInstaller.kt` (downloader →
installer hand-off), UI in `…/ui/upd/UpdateScreens.kt` (`UpdateGate` +
**More → App updates**). Publisher and examples: `tools/update-server/`.

## Security settings

Settings → **Sign-in security**:

| Setting | What it does |
|---|---|
| Alert me about new sign-ins | First sign-in from an unrecognised device → notification (and email when the server has mail configured). Server-side (`security_alerts`) |
| Security activity | Every recorded sign-in: device, IP, method, time, and a **NEW DEVICE** badge on the ones that raised an alert |
| App lock | 4-digit PIN asked on a cold start and after the chosen idle timeout. Stored as a salted PBKDF2-SHA256 hash in `SharedPreferences` — never sent anywhere. *Lock now* is there for a manual lock |
| Block screenshots | `FLAG_SECURE`: no screenshots, no screen recording, no recents preview |

The lock is drawn as the outermost layer in `AppRoot`, so a locked app shows
nothing of the UI behind it — not even the update prompt. `MainActivity.onStop`
stamps the background time and `onStart` re-locks when the timeout (or a fresh
process) says so.

## Key backup (password-encrypted)

Settings → **Key backup** keeps a copy of the account's E2EE private key on the
server, sealed with the account password, so a new phone restores it by simply
signing in:

| Action | What happens |
|---|---|
| Back up now | `E2EE.sealBackup` derives `PBKDF2-HMAC-SHA256(password, salt, 600 000, 32)` locally, encrypts the PKCS#8 PEM with AES-256-GCM and uploads the envelope to `PUT /e2ee/backup` |
| Restore | `GET /e2ee/backup` → `E2EE.restoreBackup` writes the key files, then the restored public key is republished to `PUT /users/me/pubkey` |
| Delete | Removes the server copy; local keys stay |

Sign-in runs the same restore step **before** a keypair is generated (otherwise a
fresh install would publish a brand new key and make older messages unreadable),
and refreshes the envelope afterwards when the blob already belongs to this
device. The password is used only on the phone: it is never hashed server-side
and never uploaded. An envelope is bound to the account id through the GCM
additional data, so a blob cannot be swapped between accounts, and the settings
card warns when the stored backup was made from a different key than the account
currently publishes.

## Last seen & presence

Profiles, the chat list and the chat header show **online** / **last seen …**,
updated live from the `presence` event over the websocket (`AppVm.presenceOverrides`).
The two privacy switches ("Who can see when I am online", "Who can see last visit
time") are in Settings → Privacy; `who_can_see_online = nobody` stops the server
from sending anything at all.

## Glass surfaces (menus and toasts)

Every `⋯` menu and every toast is a rounded, frosted-style pane over the app
rather than an uncustomizable Material container. Material3 1.2's
`DropdownMenu` paints an opaque `surfaceContainer` and exposes neither a shape
nor a container colour, and
`SnackbarHost` ships a solid, barely-rounded bar — so
`ui/components/GlassMenu.kt` carries its own `GlassMenu` (a raw `Popup` +
`Modifier.glassSurface`) with `GlassMenuItem` rows (optional leading glyph,
`danger` in the error colour) and `GlassToast`, which `AppRoot` puts in the
`SnackbarHost` slot instead of the default bar.

`glassSurface` uses a stable tinted fill, soft highlight and rim for floating
surfaces. It deliberately does **not** apply Haze inside popup/dialog windows:
on some Android/OEM combinations that cross-window effect paints a black sheet
over the menu children. Haze remains enabled for in-window headers and the tab
bar. `AppRoot` lifts snackbars above `MainShell`'s custom bottom tabs (and the
feed FAB), while `BlurScaffold` reserves the Android navigation-bar inset for
scroll content. Each standalone `BlurScaffold` owns its own `HazeState`; only
`MainShell` shares one between the active tab and bottom navigation. This keeps
outgoing/incoming route snapshots from leaking into unrelated cards during
animated navigation. Content cards remain solid surfaces, not haze children.
`GlassMenuPositionProvider` puts the popup under the anchor's
right edge, flipped above when there is no room, with a 14 dp inset so the drop
shadow is not clipped — a popup window is exactly content-sized.

## Highlights

- **TLS guard**: HTTPS connections intercepted by an SSL-proxying proxy
  (mitmproxy, Charles, Fiddler, Burp, MDM/AV inspection) are **reset** before any
  request is written — API, websocket and media. See
  `app/src/main/java/org/uxaoro/app/net/TlsGuard.kt`; switch and live status in
  **Settings → Security**.
- **Video player**: aspect-ratio correct, poster frame, buffering spinner,
  auto-hiding glass controls (scrub, time, mute, fullscreen), double-tap ±10s,
  fullscreen dialog that keeps position, autoplaying looping stories. See
  `app/src/main/java/org/uxaoro/app/ui/media/VideoPlayer.kt`.

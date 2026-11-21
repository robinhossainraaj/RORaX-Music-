# RORaX Music --- Complete Android App Product & Engineering Specification

**App name:** RORaX Music\
**Android application ID / package name:** `com.rorax.music`\
**Product type:** Offline-first local music player with cinematic
synchronized lyrics and optional AI-assisted features\
**Primary build environment:** Google AI Studio\
**Target deliverable:** A working Android APK backed by a complete,
maintainable Android project\
**Status:** Product specification for implementation

------------------------------------------------------------------------

## 1. Mission and product identity

RORaX Music must not feel like a generic music player. Its main
experience is listening to locally stored music while enjoying
beautiful, readable lyrics that follow the song in real time.

The product combines:

1.  A polished local music player.
2.  Cinematic line-by-line synchronized lyrics.
3.  One-tap bulk lyric downloads for offline use.
4.  A music library that is easy to browse.
5.  Optional AI-powered music and lyric assistance that does not
    interfere with ordinary listening.
6.  Reliable background playback and Android media controls.

**Core promise:** Download lyrics while online, then enjoy local songs
and their cached synchronized lyrics offline.

The main player must remain useful without an AI account, paid AI
service, or internet connection.

## 2. Visual identity and copyright-safe design

### 2.1 Design direction

Use a premium dark music-app aesthetic inspired by the broad usability
principles of modern streaming apps, but create an original RORaX Music
identity. Spotify may be a general visual reference only; do not copy
its exact screens, layout, icons, illustrations, branded green
treatment, assets, or proprietary font.

Design principles:

-   Dark, immersive, AMOLED-friendly foundation.
-   Original RORaX accent palette; use a distinctive accent such as
    electric lime, teal, or warm amber sparingly. Do not reproduce
    Spotify's exact green-and-black branding.
-   Clean, confident, geometric sans-serif typography with a modern
    music-app feel. Use a properly licensed Android/system font or a
    freely licensed font such as Inter if included legally. Do not
    extract or bundle Spotify's font.
-   Strong text hierarchy, large song/section titles, generous spacing,
    restrained gradients, and subtle motion.
-   Album art may provide contextual color, but text and controls must
    remain legible.
-   Use original icons or properly licensed Material icons.
-   Avoid excessive glass effects, glowing borders, random decorative
    shapes, and generic AI-generated visual clutter.
-   Motion should be smooth and purposeful, with
    reduced-motion/accessibility considerations.
-   Support dark mode as the main experience and ensure readable
    contrast.

### 2.2 Branding rules

-   App name must be written exactly as **RORaX Music**.
-   Package/application ID must be exactly `com.rorax.music`, unless the
    existing Android project already has a conflicting identity that
    requires explicit user approval before changing it.
-   Do not use Spotify's logo, brand assets, exact font files, or
    confusingly similar branding.
-   Create an original app icon and splash screen that match RORaX
    Music's identity.
-   Do not invent a final logo without presenting the design as original
    branding.

------------------------------------------------------------------------

## 3. Required app screens

The user specified five main screens. Keep these as the primary product
structure:

1.  Splash screen
2.  Home screen
3.  Settings screen
4.  Download screen
5.  Music Player screen

Supporting pages, sheets, and dialogs may be added only where needed for
complete functionality, such as Search, Queue, Playlist detail,
permission explanation, lyric match review, and AI assistant. They must
fit the same design system and must not turn the main navigation into an
overcrowded menu.

### 3.1 Splash screen

**Purpose:** Provide a polished, brief entry into the app.

Requirements: - Show original RORaX Music icon/wordmark. - Use the app's
dark base color and a restrained accent. - Keep the screen simple and
professional. - Avoid a fake loading animation that always waits for a
fixed long time. - Initialize only the essentials needed to enter the
app. - If first-run permissions are needed, request them contextually
after the splash rather than stacking confusing permission dialogs on
the splash. - Navigate to Home when initialization is ready. - On later
launches, avoid unnecessary delays.

States: - Normal launch. - Initial library/database initialization. -
Recoverable initialization error with a useful message and retry option.

### 3.2 Home screen

**Purpose:** Make it fast to find and play local music.

Suggested structure: - Header with RORaX Music identity and a Settings
entry. - Search entry. - A prominent Continue Listening or Recently
Played section when data exists. - Recently Added. - Favorites or user
playlists when available. - Browse categories such as Songs, Albums,
Artists, and Playlists. - A compact persistent mini-player at the bottom
when a song is active. - Main navigation to Home, Downloads/Lyrics, and
Settings, designed to be original and uncluttered.

Rules: - The Home screen must reflect real library data, not hardcoded
demo songs. - Empty state should explain how to grant music access or
add songs. - Tapping a song starts playback. - Tapping an album or
artist opens a relevant detail view or filtered song list. - Search
should find local songs by title, artist, and album. - Recently played
and favorites must persist across app restarts. - Do not display fake
recommendations or fake AI-generated playlists. - Do not require network
access to show the local library.

### 3.3 Settings screen

**Purpose:** Configure playback, library, lyrics, appearance, and app
behavior.

Required settings groups:

**Appearance** - Dark theme as the primary theme. - Optional system
theme/light theme only if implemented consistently. - Accent color
selection only if it can be implemented cleanly. - Lyrics text size. -
Lyrics animation preference or reduced motion.

**Playback** - Shuffle and repeat behavior controls where appropriate. -
Resume last playback/session option if supported. - Audio focus and
headphone unplug behavior, following Android conventions. - Playback
speed if supported by the existing player engine.

**Lyrics** - Preferred lyric source: LRCLIB online lookup and local LRC
import. - Automatic lyric lookup toggle. - Automatically download
missing lyrics while charging/Wi-Fi only if such behavior is explicitly
implemented and clearly explained. - Default behavior must not silently
consume excessive mobile data. - Cached lyrics management and refresh
controls. - Clearly explain that synchronized lyrics require timestamps.

**Library** - Rescan/refresh local music library. - Show media
permission status. - Explain how to grant access if permission is
denied. - Do not promise access to folders Android does not expose to
the app.

**Storage** - Show approximate storage used by cached lyrics. - Clear
lyric cache with a confirmation dialog. - Clearing lyric cache must not
delete the user's audio files or playlists.

**AI features** - Enable/disable optional AI assistance. - Explain which
features need internet and which data, if any, would be sent to an
external service. - Do not upload audio files without explicit informed
user consent. - The core player must not break when AI is disabled or
unavailable.

**About** - App name and version. - Open-source licenses/third-party
notices as applicable. - Privacy policy and support links only if real
links are supplied/configured. - Do not create fake support URLs.

### 3.4 Download screen --- Lyrics Manager

**Purpose:** Download and manage lyrics for local songs so they can be
used offline.

This is a lyrics-download manager, not a music-download store. The audio
tracks already come from local storage.

Required features: - Main **Download All Lyrics** action. - Summary
counts: total local songs, already synchronized, queued, downloaded,
plain lyrics only, not found, needs review, and failed. - Progress
display based on real work completed. - Song list with status and retry
controls. - Filter tabs or chips such as All, Missing, Downloaded, Needs
Review, and Failed. - Cancel/pause controls where supported. - Preserve
completed results if the queue is cancelled or the app closes. - Retry
failed items without restarting every successful lookup. - Refresh lyric
data deliberately rather than repeating API calls unnecessarily. -
Manually import an `.lrc` file for a song. - Manually select a match
when multiple online results are plausible. - Provide an honest message
when no lyrics are available. - Never show fake progress or mark a song
downloaded until the result has been saved successfully.

**Bulk-download workflow:** 1. Scan the currently accessible local music
library. 2. Identify songs with no cached lyrics or only
plain/unsynchronized lyrics. 3. Skip valid cached synchronized lyrics by
default. 4. Search LRCLIB using available title, artist, album and
duration metadata. 5. Prefer a correct match with timestamped
`syncedLyrics`. 6. Validate result metadata and avoid incorrect
remixes/live versions where identifiable. 7. Save each successful result
to persistent local storage immediately. 8. Continue after individual
failures. 9. Respect current LRCLIB documentation and rate limits; use
controlled concurrency and sensible request delays. 10. On completion,
report real totals for synchronized results, plain lyrics, not found,
ambiguous matches, and failures.

The queue should survive process death as far as Android's
background-execution rules permit. Use WorkManager or an appropriate
existing mechanism if background work is necessary, and preserve state
so work can safely resume. Do not promise that Android will run
background work continuously without restrictions.

### 3.5 Music Player screen --- primary differentiator

**Purpose:** Let the listener enjoy the song and read synchronized
lyrics without distraction.

Design: - Cinematic but restrained dark interface. - Album artwork or a
subtle artwork-derived background near the top. - Song title and artist
clearly visible. - Large, readable lyric area as the visual focus. -
Current lyric line in high contrast. - Previous and upcoming lines
dimmed but still readable. - Smooth line-by-line transitions driven by
playback timestamps. - Compact, easy-to-reach playback controls. -
Progress/seek bar and elapsed/total time. - Shuffle/repeat, queue,
favorite, and lyric status controls where they fit. - Do not put
translation panels or an AI chat interface on the main player by
default. - Optional word-by-word karaoke mode may be added later, but
the initial main mode is cinematic line-by-line lyrics. - A small AI
action may be available in a secondary menu, not as the main focus.

Required playback behavior: - Play/pause, next, previous, seek, shuffle,
repeat, queue, and favorites. - Continue audio when navigating to other
screens. - Provide a mini-player on Home while playback is active. -
Integrate with Android notification, lock-screen and headset/media
controls. - Use the existing project player if suitable; otherwise use
AndroidX Media3 ExoPlayer with a proper playback service. - Handle audio
focus, interruptions, headphone unplug, track changes, and process
recreation appropriately. - The lyric display must follow the real
player position, not an unrelated timer. - Seeking backward/forward must
immediately select the appropriate lyric. - If lyrics are unavailable,
keep all playback controls working and show a clean no-lyrics state. -
If only plain lyrics exist, label them as unsynchronized rather than
pretending they follow the song.

------------------------------------------------------------------------

## 4. Navigation and interaction map

Recommended primary navigation: - **Home** - **Lyrics / Downloads** -
**Settings**

The Music Player is opened by selecting a song or tapping the
mini-player. It is not necessary to reserve a permanent
bottom-navigation tab for it if that makes navigation cluttered.

Typical flows:

**First launch** Splash → Home → Explain/request audio permission → Scan
library → Home with real songs.

**Play a song** Home/library → tap song → Music Player → lyrics loaded
from local cache if available.

**Download lyrics** Home → Lyrics/Downloads → Download All Lyrics →
progress → completion summary → cached lyrics available offline.

**Manual lyrics** Music Player or Download screen → Import LRC / choose
lyric result → validate → save locally → return to player.

**Settings** Any main screen → Settings → adjust preference → changes
persist and apply without restarting unless technically necessary.

**Offline playback** Cached local song + cached lyrics → play in
airplane mode → lyrics synchronize from timestamps without network
requests.

------------------------------------------------------------------------

## 5. Technical architecture

Use the existing project architecture after inspecting it. The preferred
stack, if compatible with the repository, is:

-   Kotlin.
-   Jetpack Compose for UI.
-   AndroidX Media3 ExoPlayer for playback.
-   MediaSession/service integration for background and system controls.
-   Room for persistent lyric metadata and cache if the project needs a
    database.
-   DataStore or the existing preference system for settings.
-   Kotlin coroutines and Flow for asynchronous work/state where
    compatible.
-   WorkManager for durable background queue work when appropriate.
-   A maintained HTTP client already present in the project, or a
    compatible one selected after inspection.
-   Unit and integration tests for the core logic.

Do not blindly upgrade Gradle, Kotlin, Compose, Media3, or the Android
Gradle Plugin. Check versions and compatibility first.

Suggested conceptual modules/layers: - UI/screens and navigation. -
Playback controller/service. - Local media library repository. - Lyrics
provider/client. - Lyrics matching/ranking. - LRC parser and timestamp
model. - Persistent lyric repository/cache. - Download queue/worker. -
Settings/preferences. - Optional AI integration.

The exact package and directory structure should follow the existing
project conventions while using `com.rorax.music` as the required
application identity for a new project.

------------------------------------------------------------------------

## 6. LRCLIB integration requirements

Official documentation: https://lrclib.net/docs

Read the current documentation before coding. Use only documented
endpoints, parameters, response fields, identification requirements, and
request limits. Do not invent API behavior.

Lookup: - Use title and artist first, with album and duration when
available. - Prefer exact metadata matches and timestamped lyrics. -
Preserve version indicators such as live, acoustic, remix, instrumental,
radio edit and remaster. - Do not choose a clearly mismatched result
simply because the title is similar. - If multiple candidates are
plausible, mark as Needs Review and allow the user to choose. - Do not
fabricate missing lyrics. - Handle no result as a normal state. - Handle
offline state, timeouts, HTTP errors, malformed responses and rate
limiting gracefully. - Cache results and avoid repeated requests for the
same song. - Do not expose private credentials in the APK; do not assume
an API key is required.

LRCLIB does not guarantee coverage for every track. The UI must clearly
distinguish missing lyrics from failed network requests.

------------------------------------------------------------------------

## 7. LRC timestamps and lyric synchronization

Parse common LRC timestamp formats, including centisecond and
millisecond-style timestamps supported by the input. Handle metadata
tags, multiple timestamps on one line where supported, Unicode, line
endings, empty lines and malformed entries safely.

For a player position `t`, choose the latest valid lyric timestamp at or
before `t`. Use the real Media3/player position and update at an
appropriate interval or on relevant player events. Avoid a separate
timer that drifts from audio playback.

Must correctly handle: - Play/pause/resume. - Seek forward and
backward. - Track changes. - Queue changes. - Playback speed changes. -
End of track. - Missing or invalid timestamps. - Equal timestamps. -
Empty lyric lines.

Do not generate fake timestamps by evenly dividing the track duration
across lyrics. Plain lyrics must remain marked unsynchronized unless an
actual alignment process generates and validates timestamps.

------------------------------------------------------------------------

## 8. AI feature strategy

AI must add useful capabilities without making the player dependent on
cloud AI.

Potential optional features: - Assist in ranking ambiguous lyric matches
using metadata. - Help explain a selected lyric when the user explicitly
requests it. - Assist with lyric timing only when a suitable alignment
system exists. - Help organize songs or suggest mood-based groupings
only when the underlying feature is genuinely implemented.

Restrictions: - AI must not interrupt playback. - AI features must be
optional and clearly labelled. - Explain internet requirements and any
data sent to a service. - Do not upload audio files by default. - Do not
pretend a text model can determine exact lyric timestamps from lyric
text alone. - Core playback, cached lyrics, and synchronization must
work without AI.

Do not add a large AI chat screen to the initial release unless the
product is deliberately expanded to include it.

------------------------------------------------------------------------

## 9. Offline-first requirements

After a song's lyrics have been downloaded successfully: - Read lyrics
from local persistent storage. - Display them without making a network
request. - Keep timestamps available for synchronization. - Preserve
them across app restarts and device reboots. - Support airplane mode. -
Keep playback working if LRCLIB or AI services are unavailable. - Never
erase good cached lyrics because a later refresh fails. - Do not delete
music files when clearing lyric cache. - Clearly state that first-time
online lyric lookup requires internet. - Songs without downloaded lyrics
cannot be guaranteed to have lyrics offline.

------------------------------------------------------------------------

## 10. Data, state, and error handling

Persist as appropriate: - Cached lyric content and timestamps. - Local
song association and metadata used for matching. - Provider result ID
when available. - Sync status and match status. - Download status and
timestamps. - User favorites, playlists, and recent playback. - User
preferences. - Bulk-download queue state and summary.

Use explicit states such as: - Cached synchronized lyrics. - Cached
plain lyrics. - Queued. - Searching. - Downloaded. - Not found. - Needs
review. - Retryable failure. - Permanent failure.

Handle missing metadata, permission denial, storage changes, network
loss, HTTP 429, server errors, invalid JSON/LRC, database failures,
cancellation, duplicate requests and app restarts. Errors must not stop
audio playback or corrupt cached lyrics.

------------------------------------------------------------------------

## 11. Accessibility, performance, and privacy

-   Maintain readable contrast and adjustable lyric font size.
-   Support Android font/display scaling.
-   Provide accessible labels for icon-only controls.
-   Avoid unnecessary battery drain and frequent full-library rescans.
-   Avoid unnecessary recomposition and excessive polling.
-   Do not request broad storage access when MediaStore permissions
    suffice.
-   Respect Android scoped storage and supported audio formats.
-   Send only the metadata required for lyric lookup.
-   Never send local file paths or upload audio without explicit user
    consent.
-   Follow provider terms and review copyright/licensing requirements
    for lyric display and caching before public release.
-   Do not claim that API-accessible lyrics are automatically
    copyright-free.
-   Include third-party license notices as required.

------------------------------------------------------------------------

## 12. Testing and acceptance criteria

### Build and identity

-   Application ID is `com.rorax.music`.
-   App display name is `RORaX Music`.
-   Splash screen and icon use original branding.
-   APK builds successfully with the repository's compatible toolchain.

### Library and playback

-   Scans accessible local audio.
-   Displays real metadata, with safe fallbacks.
-   Search, play, pause, seek, next, previous, queue, favorites and
    repeat/shuffle work as implemented.
-   Playback continues while navigating screens.
-   Notification and lock-screen media controls work.
-   Existing project functionality is preserved.

### Lyrics

-   LRCLIB lookup uses documented API behavior.
-   Results are matched cautiously.
-   LRC timestamps parse correctly.
-   Current lyric follows the actual playback position.
-   Seeking and track changes update the lyric correctly.
-   Missing or plain lyrics are honestly labelled.
-   Incorrect candidates can be rejected or manually reviewed.

### Bulk download and offline use

-   Download All Lyrics reports real progress and counts.
-   Valid cached synchronized lyrics are skipped by default.
-   Completed results remain saved after cancellation.
-   Failures do not stop the queue.
-   Retry does not redownload every successful item unnecessarily.
-   Cached lyrics survive app restarts.
-   In airplane mode, a song with cached synchronized lyrics plays and
    displays correct lyrics without network access.
-   Clearing lyric cache does not delete audio files.

### UX and reliability

-   No fake sample data presented as real user content.
-   No inert buttons or placeholder flows presented as finished
    features.
-   Permission denial, empty library, no lyrics, and network errors have
    clear states.
-   Core features are tested, and any untested behavior is disclosed.

------------------------------------------------------------------------

## 13. Implementation deliverables

The coding agent must provide: 1. A short audit of the existing project
before making changes. 2. A screen inventory and navigation summary. 3.
A list of files changed and the purpose of each change. 4. The real
implementation of all agreed features, not just visual mockups. 5.
Unit/integration tests where feasible. 6. The actual APK build result
and exact output location. 7. A summary of tests actually run and their
results. 8. Remaining limitations, including songs with no available
lyrics. 9. Any unresolved decisions that genuinely require the user's
input.

Do not claim success unless the relevant feature, test, or build was
actually verified.

## 14. Final instruction to Google AI Studio

Read this complete specification first. Inspect the repository and
preserve working functionality. Use the exact app name **RORaX Music**
and application ID **`com.rorax.music`** for a new project. Implement
the five primary screens---Splash, Home, Settings, Download, and Music
Player---with the behavior described here. Build an original,
copyright-safe visual identity with a premium dark music-app aesthetic
and clean geometric sans-serif typography; do not copy Spotify's exact
branding, font, assets, or screen layouts.

The main product differentiator is the cinematic line-by-line
synchronized lyric experience and the ability to download lyrics for
local music in advance and then use them offline. Build a real, robust
Android application, not a UI-only prototype.

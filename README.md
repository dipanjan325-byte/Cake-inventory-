# Cake Shop Manager — Android app

A standalone Android app (Kotlin + Jetpack Compose) with its own on-device
SQLite database (via Room). No internet connection is required for day to
day use — billing, inventory, stock-in, returns, vendors, feedback and the
dashboard all work fully offline. The only feature that uses the internet
is the optional **Scan a photo** stock-reading feature, which calls the
Anthropic API directly using an API key you enter yourself in Settings.

## A quick note on how this was built

This project was written and checked for obvious errors (balanced braces,
correct imports, consistent types) without being compiled, because the
environment it was written in has no Android SDK and no internet access.
**The GitHub Actions workflow below is the first real compile** — it runs
on GitHub's servers, which do have the full Android toolchain. If it turns
up a build error, paste the error text back and it can be fixed quickly;
that's a normal, expected part of getting from source to a working build,
not a sign anything is fundamentally wrong.

---

## Option A — Get the APK with no software installed on your computer

This uses GitHub's free build servers to compile the APK for you.

1. Create a free account at [github.com](https://github.com) if you don't have one.
2. Create a new **repository** (public or private, either works) and upload
   every file and folder from this project into it, keeping the folder
   structure exactly as given — in particular the `.github/workflows/`
   folder must stay named exactly that.
   - Easiest way: on the repository page, click **Add file → Upload files**,
     drag the whole project folder in, and commit.
3. Click the **Actions** tab at the top of the repository. You should see
   a workflow run called "Build APK" start automatically (it runs on every
   push). If it doesn't start, click **Build APK** in the left sidebar →
   **Run workflow**.
4. Wait for it to finish — usually 3 to 6 minutes. A green check mark means
   it succeeded.
5. Get the APK either way:
   - Click the finished run → scroll to **Artifacts** → download
     **cake-shop-manager-debug-apk** (a zip containing the APK), or
   - Click the **Releases** link on the right side of the repository's main
     page → open the latest "Build N" release → download
     **app-debug.apk** directly.
6. Copy the `.apk` file to your Android phone (email it to yourself, use a
   cloud drive, or a USB cable) and open it there to install — see
   **Installing on your phone** below.

If the build fails (red ✕), open the run, open the failing step, and
you'll see the error. The most common first-build issues are a typo in a
file you edited or a folder that didn't upload correctly — paste the error
text back for a fix.

## Option B — Build it yourself in Android Studio

If you'd rather build locally:

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. **File → Open**, pick this project's folder.
3. Let it sync (first sync downloads dependencies — takes a few minutes).
   If it asks to create a Gradle wrapper, let it.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. When it finishes, click the **locate** link in the notification, or find
   the file at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Installing on your phone

Android blocks installs from outside the Play Store by default — this is
a normal, one-time step for any app installed this way, not specific to
this app:

1. Transfer the `.apk` file to your phone and open it (e.g. tap the
   download notification, or open it from your Files app).
2. Android will say installing is blocked. Tap **Settings** in that prompt,
   enable **Allow from this source** for the app you used to open the file
   (Files, Chrome, Gmail, etc.), then go back and tap **Install**.
3. Open the app. The first time you use **Stock in → Scan a photo**, it
   will ask for camera/gallery permission.

Each new build you install (from Option A or B) will update the app in
place as long as the package name hasn't changed, keeping your data.

---

## What's inside

| Feature | Where |
|---|---|
| Dashboard — KPI tiles, 7/14-day chart, shelf-life alerts, vendors to pay, needs restocking, top sellers | Dashboard tab |
| Billing — tap-to-add grid (in-stock items only), cart, discount, tax, receipt, send to customer | New bill tab |
| Bill history, void | Bills tab |
| Inventory — code, name, category, price, shelf life, reorder level, stock | Inventory tab |
| Price list import — the same 159-item catalog, matched by code so it's safe to re-run | Inventory tab (when empty) |
| Stock in — vendor or in-house, **and Scan a photo** to read a delivery note or production sheet | Stock in tab |
| Returns & wastage — customer returns (with optional restock), waste, kitchen use | Returns tab |
| Vendors & payables — outstanding, overdue, record a payment | Vendors tab |
| Customer feedback — star ratings, linked to a bill | Feedback tab |
| Settings — shop name, tax rate, phone country code, Google review link, Anthropic API key, erase all data | Settings (gear icon) |
| **CSV export** — downloads one CSV per table into `Downloads/business data/` on your phone | Settings → "Download CSV of everything" |

### The CSV export

Tapping **Download CSV of everything** in Settings writes these files into
your phone's **Downloads/business data/** folder (visible in any Files
app): `products-<date>.csv`, `bills-<date>.csv`, `stock_in-<date>.csv`,
`returns_and_wastage-<date>.csv`, `vendors-<date>.csv`,
`payments-<date>.csv`, `feedback-<date>.csv`.

### The "Scan a photo" feature

In **Settings**, paste in an Anthropic API key (get one at
[console.anthropic.com](https://console.anthropic.com) — billed separately,
pay-per-use, a few cents per photo on the model this is configured to use).
Then in **Stock in → the camera button**, choose a photo, tap **Read
photo**. Claude reads the items and quantities and matches them to your
catalog; you check the matches and quantities on the normal Add Stock
screen before saving — nothing is added automatically.

The key is stored only in this app's local database on your phone and is
sent only to `api.anthropic.com`, only when you tap Read photo.

---

## The database

Everything (except the photo-reading feature) works with **zero network
access**, stored in a local SQLite database (via Room) at
`cake_shop.db` inside the app's private storage. Uninstalling the app
deletes this data — use the CSV export regularly as your backup, since
there's no cloud sync in this version (that's what the
[self-hosted web version](#) from earlier in this conversation is for, if
you want multi-device shared data instead).

## Known limitations of this build

- It's a **debug** build (the GitHub Actions workflow builds
  `assembleDebug`), which is fine for your own phone but shows an
  "unsigned / debug" style banner in some phone security scanners — that's
  expected and not a sign of a problem. If you'd like a signed release
  build later (for wider distribution), that's a follow-up step.
- No automated tests were run against a real device or emulator — only
  static checks (imports, balanced braces, type consistency) were possible
  in the environment this was written in.
- If the first Actions build fails, it's most likely a small, fixable
  Gradle/Compose version mismatch — paste the error back and it can be
  corrected.

# ESIC Alert — get the .apk (one-time, ~10 minutes, free)

I can't compile the .apk myself — no Android build tools in my sandbox. GitHub's free
servers can do it instead. You don't need to know how to code for this.

## 1. Create a free GitHub account (skip if you have one)

Go to https://github.com/signup

## 2. Create a new repository

1. Go to https://github.com/new
2. Repository name: `esic-alert` (anything works)
3. Keep it **Public** (Actions minutes are unlimited/free for public repos) or Private is fine
   too, you just get a small free monthly quota either way — plenty for this.
4. Click **Create repository**. Don't add a README/gitignore on this screen.

## 3. Upload these files

On the new repo's page, click **"uploading an existing file"** (or Add file → Upload files).

Drag the **entire contents** of this `ESICAlertApp` folder in — including the `.github`,
`app` folders and the loose files (`build.gradle`, `settings.gradle`, `gradle.properties`).
Modern GitHub's upload page keeps folder structure if you drag folders in directly from
your file explorer, so drag the folders themselves, not just files one by one.

Scroll down, click **Commit changes**.

## 4. Watch it build

1. Click the **Actions** tab at the top of your repo.
2. You'll see a run called "Build APK" — click it, then click the **build** job.
3. Wait ~2-3 minutes (green checkmark = done). If it's red/failed, copy the error text
   and send it to me — I'll fix the code.

## 5. Download the APK

Still on that finished run's page, scroll down to **Artifacts** and click
`esic-alert-debug-apk` to download a zip. Unzip it — inside is `app-debug.apk`.

## 6. Get it onto your phone and install it

- Easiest: upload `app-debug.apk` to Google Drive/WhatsApp-to-self/email, open it on your
  phone, tap it.
- Android will ask to allow installing from this source — allow it (one-time).
- Tap install. Open the app — you'll see "ESIC Alert".

## 7. One-time WhatsApp activation (unavoidable, but quick)

The app sends WhatsApp messages via CallMeBot — the only no-cost way to do this without
a business account:

1. Go to https://www.callmebot.com/blog/free-api-whatsapp-messages/ and note the current
   bot phone number shown there (it changes now and then).
2. Save that number as a WhatsApp contact, and send it exactly:
   `I allow callmebot to send me messages`
3. Within ~2 minutes it replies with your **API key**.

## 8. Set up the app

Open the app, fill in:
- Your WhatsApp number (international format, no `+`, e.g. `919876543210`)
- The API key from step 7
- Keywords (already pre-filled with physiotherapist/paramedical/gorakhpur/lucknow/kanpur/etc
  — edit freely)
- How often to check
- Flip "Run automatically in the background" on

Tap **Save settings**, then **Check now** to confirm it works (first run just records a
baseline silently — no alert — that's expected). Tap **Send test WhatsApp message** any
time to confirm the WhatsApp pipeline itself is wired up correctly.

From then on it checks on your chosen schedule even with the app closed, and WhatsApps you
only when something new matching your keywords appears.

## Notes

- Android may eventually slow down background checks under battery saving. If alerts feel
  delayed, go to Settings → Apps → ESIC Alert → Battery → set to "Unrestricted".
- CallMeBot is a free hobby service meant for personal use — no uptime guarantee, but the
  app's own log (shown in the "Check now" box) always tells you what it found even if the
  WhatsApp send itself fails.
- If esic.gov.in ever redesigns its page and the app stops finding rows, the log will say
  "Nothing parsed" — send me that and I'll patch the parsing logic.

# ESIC Alert

Checks esic.gov.in on a schedule and pops a notification on your phone when something new
matches your keywords (physiotherapist, Gorakhpur, Lucknow, Kanpur, etc). No WhatsApp/third-party
service involved — it's a plain Android notification, since the app already lives on the phone.

## Getting the APK after any code change

1. Push to `main` (or click **Actions → Build APK → Run workflow** to trigger manually).
2. Open the **Actions** tab → click the latest run → wait for the green check (~2-3 min).
3. Scroll to the bottom of that run page → **Artifacts** → download `esic-alert-debug-apk` →
   unzip → `app-debug.apk`.
4. Send that file to your phone (Drive/email/USB), open it, allow "install from this source"
   the first time, tap install.

## Using the app

Open it, you'll see:
- **Keywords** (comma-separated, pre-filled with sensible defaults — edit freely)
- **Check every N hours** dropdown
- **Run automatically in the background** switch
- **Save settings**, **Check now**, **Send test notification** buttons
- A log box showing exactly what the last check found

First launch: Android will ask for notification permission — allow it, otherwise alerts can't
show. Tap **Check now** once to confirm it can reach esic.gov.in (the very first check just
records a baseline silently, by design — no alert on day-one postings, only on genuinely new
ones from then on). Tap **Send test notification** any time to confirm notifications work
independent of the actual site check. Flip the background switch on and hit **Save settings**
to have it keep checking on schedule even with the app closed.

## Notes

- If notifications feel delayed, go to Settings → Apps → ESIC Alert → Battery → set to
  "Unrestricted" (Android may otherwise throttle background work to save battery).
- If esic.gov.in redesigns its page and the app stops finding rows, the log will say
  "Nothing parsed" — send me that and I'll patch the parsing logic.

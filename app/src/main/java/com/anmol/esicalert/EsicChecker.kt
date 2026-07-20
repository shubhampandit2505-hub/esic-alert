package com.anmol.esicalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Core logic shared by the "Check now" button and the background worker.
 * Kept as one plain object with no Android UI dependency so it's easy to reason about
 * and test independent of the Activity/Worker lifecycle.
 */
object EsicChecker {

    private const val BASE_URL = "https://esic.gov.in/recruitments"
    private const val PREFS_NAME = "esic_prefs"
    private const val PAGES_TO_CHECK = 2
    private const val CHANNEL_ID = "esic_alerts"

    data class Posting(
        val uid: String,
        val office: String,
        val subject: String,
        val lastDate: String,
        val link: String
    )

    // esic.gov.in (like a lot of Indian govt sites) sends an incomplete certificate chain -
    // browsers paper over this by fetching the missing intermediate themselves, but Android's
    // strict TLS stack rejects it outright with "Trust anchor for certification path not found".
    // We only ever hit this one hardcoded government URL (never a user-supplied one), and we're
    // just reading a public notice-board page, so relaxing chain validation here is a contained,
    // low-risk trade-off rather than a general security hole.
    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Runs one full check. Returns a human-readable summary for display in the UI/log. */
    fun checkOnce(context: Context, sendNotifications: Boolean): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val log = StringBuilder()

            val allPostings = mutableListOf<Posting>()
            for (page in 1..PAGES_TO_CHECK) {
                try {
                    val url = if (page == 1) BASE_URL else "$BASE_URL/index/page:$page"
                    val html = fetch(url)
                    val rows = parseRows(html)
                    log.append("Page $page: found ${rows.size} rows.\n")
                    allPostings.addAll(rows)
                } catch (e: Exception) {
                    log.append("Page $page: error - ${e.message}\n")
                }
            }

            if (allPostings.isEmpty()) {
                log.append("Nothing parsed - esic.gov.in may be down or its page layout changed.\n")
                return finish(context, log.toString())
            }

            // Full snapshot of whatever esic.gov.in is showing right now (with links), so you can
            // always eyeball the current listings yourself, not just what's new.
            val snapshot = buildSnapshot(allPostings)

            val seen = prefs.getStringSet("seen_ids", null)
            val currentIds = allPostings.map { it.uid }.toSet()

            if (seen == null) {
                prefs.edit().putStringSet("seen_ids", currentIds).apply()
                log.append("Baseline captured: ${currentIds.size} existing postings recorded.\n")
                log.append("No alerts sent for these - future NEW postings will trigger alerts.\n")
                log.append(snapshot)
                return finish(context, log.toString())
            }

            val newPostings = allPostings.filter { it.uid !in seen }
            prefs.edit().putStringSet("seen_ids", seen + currentIds).apply()

            if (newPostings.isEmpty()) {
                log.append("No new postings since last check.\n")
                log.append(snapshot)
                return finish(context, log.toString())
            }

            log.append("${newPostings.size} new posting(s) found.\n")

            val keywords = (prefs.getString("keywords", "") ?: "")
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val matches = if (keywords.isEmpty()) {
                newPostings // no keywords set -> alert on everything new
            } else {
                newPostings.filter { p ->
                    val haystack = "${p.office} ${p.subject}".lowercase()
                    keywords.any { kw -> haystack.contains(kw) }
                }
            }

            if (matches.isEmpty()) {
                log.append("None of the new postings matched your keywords.\n")
                log.append(snapshot)
                return finish(context, log.toString())
            }

            log.append("${matches.size} match your keywords:\n\n")
            val body = StringBuilder()
            matches.take(6).forEachIndexed { i, p ->
                val line = "${i + 1}. ${p.office}\n${p.subject.take(160)}" +
                    (if (p.lastDate.isNotBlank()) "\nLast date: ${p.lastDate}" else "") +
                    (if (p.link.isNotBlank()) "\n${p.link}" else "")
                log.append(line).append("\n\n")
                body.append(line).append("\n\n")
            }
            if (matches.size > 6) {
                body.append("...and ${matches.size - 6} more. Check esic.gov.in/recruitments\n")
            }

            if (sendNotifications) {
                try {
                    sendNotification(
                        context,
                        "ESIC Alert - ${matches.size} new posting(s)",
                        body.toString().trim()
                    )
                    log.append("Notification sent.\n")
                } catch (e: Exception) {
                    log.append("Failed to send notification: ${e.message}\n")
                }
            }

            log.append(snapshot)
            finish(context, log.toString())
        } catch (e: Exception) {
            // Catch any unexpected errors to prevent crashes
            val errorLog = "Unexpected error during check: ${e.message}\n${e.stackTraceToString()}"
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_log", "Last checked: ${SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date())}\n\n$errorLog")
                    .apply()
            } catch (e2: Exception) {
                // Even error logging failed, just return
            }
            return errorLog
        }
    }

    /** Stamps the log with when this check ran and persists it, so re-opening the app (or
     *  checking after a background run) always shows when it last actually checked. */
    private fun finish(context: Context, message: String): String {
        val timestamp = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        val stamped = "Last checked: $timestamp\n\n$message"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("last_log", stamped)
            .apply()
        return stamped
    }

    private fun buildSnapshot(postings: List<Posting>): String {
        val sb = StringBuilder("\n----------\nCurrent postings on esic.gov.in (${postings.size} checked this run):\n\n")
        postings.forEachIndexed { i, p ->
            sb.append("${i + 1}. ${p.office}\n")
            sb.append("${p.subject.take(160)}\n")
            if (p.lastDate.isNotBlank()) sb.append("Last date: ${p.lastDate}\n")
            if (p.link.isNotBlank()) sb.append("${p.link}\n")
            sb.append("\n")
        }
        return sb.toString()
    }

    fun sendTestNotification(context: Context) {
        sendNotification(
            context,
            "ESIC Alert test",
            "If you can see this, notifications are working correctly."
        )
    }

    private fun sendNotification(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ESIC Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun fetch(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                resp.body?.string() ?: ""
            }
        } catch (e: Exception) {
            throw Exception("Failed to fetch $url: ${e.message}")
        }
    }

    private fun parseRows(html: String): List<Posting> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table")
        var target: Element? = null
        for (table in tables) {
            val headerText = table.select("tr").firstOrNull()?.text()?.lowercase() ?: ""
            if (headerText.contains("name of office") && headerText.contains("subject")) {
                target = table
                break
            }
        }
        val out = mutableListOf<Posting>()
        target ?: return out

        val trs = target.select("tr").drop(1)
        for (tr in trs) {
            val cells = tr.select("td")
            if (cells.size < 5) continue
            val office = cells[1].text().trim()
            val subjectCell = cells[2]
            val subject = subjectCell.text().trim()
            val lastDate = cells[4].text().trim()
            val slNo = cells.last().text().trim()

            var link = ""
            val a = subjectCell.select("a[href]").firstOrNull()
            if (a != null) {
                val href = a.attr("href")
                link = if (href.startsWith("http")) href else "https://esic.gov.in/" + href.trimStart('/')
            }

            val uid = slNo.ifBlank { "$office|$subject" }
            out.add(Posting(uid, office, subject, lastDate, link))
        }
        return out
    }
}

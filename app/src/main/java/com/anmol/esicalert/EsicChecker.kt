package com.anmol.esicalert

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Core logic shared by the "Check now" button and the background worker.
 * Kept as one plain object with no Android UI dependency so it's easy to reason about
 * and test independent of the Activity/Worker lifecycle.
 */
object EsicChecker {

    private const val BASE_URL = "https://esic.gov.in/recruitments"
    private const val PREFS_NAME = "esic_prefs"
    private const val PAGES_TO_CHECK = 2

    data class Posting(
        val uid: String,
        val office: String,
        val subject: String,
        val lastDate: String,
        val link: String
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Runs one full check. Returns a human-readable summary for display in the UI/log. */
    fun checkOnce(context: Context, sendAlerts: Boolean): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val log = StringBuilder()

        val allPostings = mutableListOf<Posting>()
        for (page in 1..PAGES_TO_CHECK) {
            val url = if (page == 1) BASE_URL else "$BASE_URL/index/page:$page"
            try {
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
            return log.toString()
        }

        val seen = prefs.getStringSet("seen_ids", null)
        val currentIds = allPostings.map { it.uid }.toSet()

        if (seen == null) {
            prefs.edit().putStringSet("seen_ids", currentIds).apply()
            log.append("Baseline captured: ${currentIds.size} existing postings recorded.\n")
            log.append("No alerts sent for these - future NEW postings will trigger alerts.\n")
            return log.toString()
        }

        val newPostings = allPostings.filter { it.uid !in seen }
        prefs.edit().putStringSet("seen_ids", seen + currentIds).apply()

        if (newPostings.isEmpty()) {
            log.append("No new postings since last check.\n")
            return log.toString()
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
            return log.toString()
        }

        log.append("${matches.size} match your keywords:\n")
        val message = StringBuilder("ESIC alert - ${matches.size} new posting(s):\n\n")
        for (p in matches.take(6)) {
            val line = "- ${p.office}: ${p.subject.take(140)}" +
                (if (p.lastDate.isNotBlank()) " (last date ${p.lastDate})" else "") +
                (if (p.link.isNotBlank()) " ${p.link}" else "")
            log.append(line).append("\n")
            message.append(line).append("\n")
        }
        if (matches.size > 6) {
            message.append("...and ${matches.size - 6} more. Check esic.gov.in/recruitments\n")
        }

        if (sendAlerts) {
            val sent = sendWhatsApp(context, message.toString())
            log.append(if (sent) "WhatsApp alert sent.\n" else "WhatsApp alert NOT sent - check phone/API key in settings.\n")
        }

        return log.toString()
    }

    fun sendTestMessage(context: Context): Boolean {
        return sendWhatsApp(context, "ESIC Alert app test message - if you see this on WhatsApp, it's wired up correctly.")
    }

    private fun sendWhatsApp(context: Context, text: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val phone = prefs.getString("phone", "")?.trim() ?: ""
        val apiKey = prefs.getString("apikey", "")?.trim() ?: ""
        if (phone.isEmpty() || apiKey.isEmpty()) return false

        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.callmebot.com/whatsapp.php?phone=$phone&text=$encoded&apikey=$apiKey"
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
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

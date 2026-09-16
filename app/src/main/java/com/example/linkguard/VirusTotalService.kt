package com.example.linkguard

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object VirusTotalService {

    private const val PREFS_NAME = "linkguard_vt_prefs"
    private const val KEY_API_KEY = "virustotal_api_key"
    private const val BASE_URL = "https://www.virustotal.com/api/v3/urls"

    // Real VirusTotal API Key provided by user
    const val API_KEY = "aa423e3202f66ed8189cec9856b10ed3710cc58899b2dfe4b4d2b2af10296e0d"

    fun getApiKey(context: Context): String {
        if (API_KEY.isNotBlank()) {
            return API_KEY.trim()
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "")?.trim() ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    /**
     * VirusTotal API v3 URL identifier is Base64 URL-safe encoded URL without padding.
     */
    fun encodeUrlId(url: String): String {
        val bytes = url.trim().toByteArray(StandardCharsets.UTF_8)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        ).trim()
    }

    fun getVirusTotalWebUrl(url: String): String {
        val id = encodeUrlId(url)
        return "https://www.virustotal.com/gui/url/$id"
    }

    /**
     * Calculates risk percentage (0% to 100%) based on malicious and suspicious engine counts.
     */
    fun calculateRiskPercentage(malicious: Int, suspicious: Int, total: Int): Int {
        if (total == 0 || (malicious == 0 && suspicious == 0)) return 0
        return when {
            malicious >= 10 -> 100
            malicious >= 5 -> 90 + (malicious - 5) * 2
            malicious >= 3 -> 75 + (malicious - 3) * 7
            malicious == 2 -> 60
            malicious == 1 -> 40
            suspicious >= 3 -> 35
            suspicious == 2 -> 25
            suspicious == 1 -> 15
            else -> 10
        }.coerceIn(0, 100)
    }

    /**
     * Real network call to VirusTotal API v3: GET https://www.virustotal.com/api/v3/urls/{id}
     */
    suspend fun checkUrl(url: String, apiKey: String): VirusTotalResult = withContext(Dispatchers.IO) {
        val webUrl = getVirusTotalWebUrl(url)
        if (apiKey.isBlank()) {
            val isSuspicious = url.contains(".xyz") || url.contains(".top") || url.contains("bit.ly") || url.startsWith("http://")
            return@withContext VirusTotalResult(
                url = url,
                risk = if (isSuspicious) VirusTotalRisk.SUSPICIOUS else VirusTotalRisk.UNRATED,
                message = if (isSuspicious) "Suspicious domain/HTTP link detected" else "Tap to view on VirusTotal.com",
                virusTotalWebUrl = webUrl
            )
        }

        try {
            val urlId = encodeUrlId(url)
            val apiUrl = URL("$BASE_URL/$urlId")
            val connection = (apiUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("x-apikey", apiKey)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val data = json.getJSONObject("data")
                val attributes = data.getJSONObject("attributes")
                val stats = attributes.getJSONObject("last_analysis_stats")

                val malicious = stats.optInt("malicious", 0)
                val suspicious = stats.optInt("suspicious", 0)
                val harmless = stats.optInt("harmless", 0)
                val undetected = stats.optInt("undetected", 0)
                val total = malicious + suspicious + harmless + undetected

                val risk = when {
                    malicious > 0 -> VirusTotalRisk.MALICIOUS
                    suspicious > 0 -> VirusTotalRisk.SUSPICIOUS
                    harmless > 0 || total > 0 -> VirusTotalRisk.CLEAN
                    else -> VirusTotalRisk.UNRATED
                }

                val riskPercentage = calculateRiskPercentage(malicious, suspicious, total)

                val msg = when (risk) {
                    VirusTotalRisk.MALICIOUS -> "$malicious / $total security engines flagged as malicious"
                    VirusTotalRisk.SUSPICIOUS -> "$suspicious / $total security engines flagged as suspicious"
                    VirusTotalRisk.CLEAN -> "0 / $total detections (Clean & verified harmless)"
                    VirusTotalRisk.UNRATED -> "No recent threat records"
                    VirusTotalRisk.ERROR -> ""
                }

                return@withContext VirusTotalResult(
                    url = url,
                    risk = risk,
                    maliciousCount = malicious,
                    suspiciousCount = suspicious,
                    harmlessCount = harmless,
                    undetectedCount = undetected,
                    totalEngines = total,
                    message = msg,
                    virusTotalWebUrl = webUrl,
                    riskPercentage = riskPercentage
                )
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // URL has never been scanned on VirusTotal
                return@withContext VirusTotalResult(
                    url = url,
                    risk = VirusTotalRisk.UNRATED,
                    message = "URL not in VirusTotal database yet (New or unindexed)",
                    virusTotalWebUrl = webUrl
                )
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                return@withContext VirusTotalResult(
                    url = url,
                    risk = VirusTotalRisk.ERROR,
                    message = "Invalid or expired VirusTotal API key (HTTP $responseCode)",
                    virusTotalWebUrl = webUrl
                )
            } else {
                val errorStream = connection.errorStream
                val errMsg = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).readText()
                } else {
                    "HTTP $responseCode"
                }
                return@withContext VirusTotalResult(
                    url = url,
                    risk = VirusTotalRisk.ERROR,
                    message = "VirusTotal error ($responseCode): ${errMsg.take(80)}",
                    virusTotalWebUrl = webUrl
                )
            }
        } catch (e: Exception) {
            return@withContext VirusTotalResult(
                url = url,
                risk = VirusTotalRisk.ERROR,
                message = "Network error: ${e.localizedMessage ?: "Failed to connect"}",
                virusTotalWebUrl = webUrl
            )
        }
    }
}

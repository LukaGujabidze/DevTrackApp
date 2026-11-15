package com.devtracker.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Optional online sync using the public Codeforces API. Core tracking never needs it. */
object CodeforcesApi {
    data class Rating(val contest: String, val time: Long, val rating: Int)

    fun fetchRatings(handle: String): List<Rating> {
        val url = URL("https://codeforces.com/api/user.rating?handle=" + URLEncoder.encode(handle, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        try {
            val stream = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream) ?: throw IOException("HTTP ${conn.responseCode}")
            val json = JSONObject(stream.bufferedReader().use { it.readText() })
            if (json.getString("status") != "OK") throw IOException(json.optString("comment", "Codeforces returned an error"))
            val arr = json.getJSONArray("result")
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Rating(o.getString("contestName"), o.getLong("ratingUpdateTimeSeconds") * 1000, o.getInt("newRating"))
            }
        } finally { conn.disconnect() }
    }
}

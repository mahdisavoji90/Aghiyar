package ir.aghiyar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Conservative local lifetime guard; NOT a substitute for Divar's server-side quota. */
class SearchQuota(context: Context) {
    private val prefs = context.getSharedPreferences("search_quota", Context.MODE_PRIVATE)
    @Synchronized fun used(): Int = prefs.getInt("attempts", 0)
    @Synchronized fun reserve(): Boolean {
        val count = used()
        if (count >= 100) return false
        // Reserve before networking; even a failed HTTP call consumes a local attempt.
        return prefs.edit().putInt("attempts", count + 1).commit()
    }
}

class DivarClient(private val quota: SearchQuota) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    suspend fun search(key: String, category: String, city: String, text: String): List<Listing> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "کلید API وارد نشده است" }
        require(category.matches(Regex("[a-z0-9-]{2,80}"))) { "شناسه دسته‌بندی معتبر نیست" }
        require(city.matches(Regex("[a-z0-9-]{2,80}"))) { "شناسه شهر معتبر نیست" }
        require(text.length <= 128) { "عبارت جست‌وجو باید حداکثر ۱۲۸ کاراکتر باشد" }
        if (!quota.reserve()) error("سقف محافظه‌کارانه ۱۰۰ درخواست این نصب پر شده است")
        val payload = JSONObject().put("category", category).put("city", city)
            .put("query", JSONObject()).put("query_text", text)
        val req = Request.Builder()
            .url("https://open-api.divar.ir/v2/open-platform/finder/post")
            .header("X-API-Key", key)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("خطای API دیوار: HTTP ${response.code}. مجوز، سهمیه یا پارامترها را بررسی کنید")
            val body = JSONObject(response.body?.string() ?: "{}")
            val posts = body.optJSONArray("posts") ?: return@use emptyList()
            buildList {
                for (i in 0 until posts.length()) {
                    val p = posts.optJSONObject(i) ?: continue
                    val token = p.optString("token")
                    if (token.isBlank()) continue
                    val priceObj = p.optJSONObject("price")
                    val price = priceObj?.optString("value")?.toLongOrNull()
                    add(Listing(token, p.optString("title", "آگهی بدون عنوان"), price,
                        p.optString("category", category), p.optString("city", city), "divar"))
                }
            }
        }
    }
}

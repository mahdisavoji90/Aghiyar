package ir.aghiyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AghiyarDb::class.java, "aghiyar.db").build()
        val secrets = SecureKeyStore(applicationContext)
        val quota = SearchQuota(applicationContext)
        val client = DivarClient(quota)
        setContent { MaterialTheme { AghiyarScreen(db, secrets, quota, client) } }
    }
}

private fun money(price: Long?): String = price?.let {
    NumberFormat.getNumberInstance(Locale.US).format(it) + " تومان"
} ?: "قیمت توافقی / نامشخص"

@Composable
fun AghiyarScreen(db: AghiyarDb, secrets: SecureKeyStore, quota: SearchQuota, client: DivarClient) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var isDemo by remember { mutableStateOf(true) }
    var keyInput by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(secrets.get() != null) }
    var query by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("tehran") }
    var category by remember { mutableStateOf("mobile-phones") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var listings by remember { mutableStateOf(DemoListings.items) }
    var saved by remember { mutableStateOf(emptyList<Listing>()) }
    var message by remember { mutableStateOf("حالت آزمایشی فعال است؛ هیچ درخواست واقعی ارسال نمی‌شود.") }
    var busy by remember { mutableStateOf(false) }
    var used by remember { mutableIntStateOf(quota.used()) }
    LaunchedEffect(db) { db.listings().observeSaved().collectLatest { saved = it } }
    val shown = remember(listings, minPrice, maxPrice) {
        val min = minPrice.toLongOrNull()
        val max = maxPrice.toLongOrNull()
        listings.filter { (min == null || (it.price != null && it.price >= min)) &&
            (max == null || (it.price != null && it.price <= max)) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("آگهی‌یار", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("اندروید مستقل · بدون Backend", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            listOf("جست‌وجو", "تحلیل", "ذخیره‌ها", "تنظیمات").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Row { Text("حالت آزمایشی"); Spacer(Modifier.width(12.dp)); Switch(checked = isDemo, onCheckedChange = {
                    isDemo = it
                    message = if (it) "حالت آزمایشی فعال شد" else "حالت واقعی: هر جست‌وجو یک درخواست سهمیه‌دار مصرف می‌کند"
                }) }
                OutlinedTextField(query, { query = it }, label = { Text("عبارت جست‌وجو") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("شناسه دسته‌بندی دیوار (slug)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(city, { city = it }, label = { Text("شناسه شهر دیوار (مثلاً tehran)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("شناسه دسته‌بندی را از فهرست رسمی دیوار بررسی کنید؛ مقادیر آزمایشی الزاماً در API معتبر نیستند.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minPrice, { minPrice = it.filter(Char::isDigit) }, label = { Text("حداقل قیمت") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(maxPrice, { maxPrice = it.filter(Char::isDigit) }, label = { Text("حداکثر قیمت") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (isDemo) {
                        listings = DemoListings.items.filter {
                            (query.isBlank() || it.title.contains(query, true)) &&
                            (category.isBlank() || it.category == category) &&
                            (city.isBlank() || it.city == city)
                        }
                        message = "${listings.size} آگهی آزمایشی یافت شد"
                    } else {
                        val key = secrets.get()
                        if (key.isNullOrBlank()) { message = "ابتدا در تنظیمات کلید API را ذخیره کنید" }
                        else if (!busy) {
                            busy = true
                            scope.launch {
                                try { listings = client.search(key, category, city, query); message = "${listings.size} آگهی از API دریافت شد" }
                                catch (e: Exception) { message = e.message ?: "خطای ارتباط" }
                                finally { busy = false; used = quota.used() }
                            }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال دریافت..." else "جست‌وجو") }
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text("نتایج: ${shown.size}", fontWeight = FontWeight.Bold)
                shown.forEach { item -> ListingCard(item, saved.any { it.token == item.token }, onSave = {
                    scope.launch { db.listings().save(item) }
                }, onRemove = { scope.launch { db.listings().delete(item.token) } }) }
            }
            1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("تحلیل قیمت آگهی‌های نمایش‌داده‌شده", style = MaterialTheme.typography.titleMedium)
                val prices = shown.mapNotNull { it.price }.sorted()
                val median = when {
                    prices.isEmpty() -> null
                    prices.size % 2 == 1 -> prices[prices.size / 2]
                    else -> (prices[prices.size / 2 - 1] / 2L) + (prices[prices.size / 2] / 2L) + ((prices[prices.size / 2 - 1] % 2 + prices[prices.size / 2] % 2) / 2)
                }
                Text("تعداد قیمت‌های عددی: ${prices.size}")
                Text("کمینه: ${money(prices.firstOrNull())}")
                Text("میانه: ${money(median)}")
                Text("بیشینه: ${money(prices.lastOrNull())}")
                Text("توجه: این شاخص‌ها فقط برای نتایج فعلی هستند، نه کل بازار. برای مقایسه معتبر، کالاهای هم‌مدل و هم‌وضعیت را انتخاب کنید.")
            }
            2 -> LazyColumn {
                item { Text("علاقه‌مندی‌های ذخیره‌شده (${saved.size})", style = MaterialTheme.typography.titleMedium) }
                items(saved, key = { it.token }) { item -> ListingCard(item, true,
                    onSave = {}, onRemove = { scope.launch { db.listings().delete(item.token) } }) }
            }
            3 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("اتصال مستقیم به کنار دیوار", style = MaterialTheme.typography.titleMedium)
                Text("کلید در Android Keystore رمزگذاری می‌شود، اما در دستگاه آلوده یا APK توزیع‌شده امنیت مطلق ندارد.")
                OutlinedTextField(keyInput, { keyInput = it }, label = { Text("کلید API (فقط روی دستگاه شما)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = {
                    secrets.put(keyInput.trim()); keySaved = secrets.get() != null; keyInput = ""
                    message = if (keySaved) "کلید در دستگاه ذخیره شد" else "کلید حذف شد"
                }) { Text("ذخیره / حذف کلید") }
                Text(if (keySaved) "کلید ذخیره شده است" else "کلیدی ذخیره نشده است")
                Text("مصرف ثبت‌شده این نصب: $used از ۱۰۰ درخواست")
                Text("این شمارنده محلی دائمی است، خودکار صفر نمی‌شود و سهمیه واقعی دیوار را نشان نمی‌دهد. خطاهای شبکه نیز به‌عنوان تلاش مصرف‌شده ثبت می‌شوند.")
                Text("پایش خودکار و اعلان آگهی جدید در این نسخه غیرفعال است تا سهمیه محدود مصرف نشود.")
                Text("برای استفاده واقعی، برنامه کنار دیوار باید مجوز SEARCH_POST داشته باشد.")
            }
        }
    }
}

@Composable
private fun ListingCard(item: Listing, isSaved: Boolean, onSave: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(money(item.price))
            Text("${item.city} · ${item.category} · ${if (item.source == "demo") "آزمایشی" else "دیوار"}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = if (isSaved) onRemove else onSave) { Text(if (isSaved) "حذف از ذخیره‌ها" else "ذخیره آگهی") }
        }
    }
}

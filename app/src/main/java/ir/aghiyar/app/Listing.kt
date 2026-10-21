package ir.aghiyar.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_listings")
data class Listing(
    @PrimaryKey val token: String,
    val title: String,
    val price: Long?,
    val category: String,
    val city: String,
    val source: String
)

@Dao
interface ListingDao {
    @Query("SELECT * FROM saved_listings ORDER BY title") fun observeSaved(): Flow<List<Listing>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: Listing)
    @Query("DELETE FROM saved_listings WHERE token = :token") suspend fun delete(token: String)
}

@Database(entities = [Listing::class], version = 1, exportSchema = false)
abstract class AghiyarDb : RoomDatabase() {
    abstract fun listings(): ListingDao
}

object DemoListings {
    val items = listOf(
        Listing("demo-01", "گوشی سامسونگ Galaxy A55 - 256GB", 22_500_000, "mobile-phones", "tehran", "demo"),
        Listing("demo-02", "گوشی سامسونگ Galaxy A55 تمیز", 20_900_000, "mobile-phones", "tehran", "demo"),
        Listing("demo-03", "گوشی سامسونگ Galaxy A55", 24_000_000, "mobile-phones", "karaj", "demo"),
        Listing("demo-04", "لپ‌تاپ Lenovo ThinkPad T14", 32_000_000, "laptop-notebook-macbook", "tehran", "demo"),
        Listing("demo-05", "لپ‌تاپ Lenovo ThinkPad T14", 30_500_000, "laptop-notebook-macbook", "shiraz", "demo"),
        Listing("demo-06", "هدفون Sony WH-1000XM4", 8_500_000, "audio-video", "tehran", "demo"),
        Listing("demo-07", "مانیتور LG 27 اینچ", 11_200_000, "computers", "isfahan", "demo"),
        Listing("demo-08", "کنسول بازی PlayStation 5", 38_000_000, "game-consoles", "tehran", "demo"),
        Listing("demo-09", "دوربین Canon EOS 80D", 26_000_000, "camera", "ahvaz", "demo"),
        Listing("demo-10", "گوشی آیفون 13", 34_000_000, "mobile-phones", "tehran", "demo")
    )
}

package ito.telegram.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ito.telegram.core.FeatureRegistry
import ito.telegram.core.text.TextLab
import ito.telegram.ui.SectionCard
import ito.telegram.ui.StatRow
import ito.telegram.ui.faNum

/**
 * صفحه‌ی صداقت: چون قرار شد قابلیت‌ها «الکی» نباشند، همین‌جا شمارش را باز می‌کنیم.
 */
@Composable
fun HonestyScreen() {
    val core = FeatureRegistry.countByImpl("core")
    val data = FeatureRegistry.countByImpl("data")
    val combo = FeatureRegistry.countByImpl("combo")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(
            title = "بی‌تعارف: این ${faNum(FeatureRegistry.total)} تا یعنی چه؟",
            subtitle = "هیچ آیتمی «تغییر رنگ به صورتی / آبی / …» نیست. نسخه‌ی رنگی هیچ چیزی جدا شمرده نشده.",
        ) {
            StatRow("موتور اختصاصی (کد جدا برای همان کار)", faNum(core))
            StatRow("محتوای واقعی (متن/هنر/قالب آماده)", faNum(data))
            StatRow("ترکیب دو موتورِ واقعی", faNum(combo))
            StatRow("جمع", faNum(FeatureRegistry.total))
        }

        SectionCard(
            title = "«ترکیبی» دقیقاً چیست؟",
            subtitle = "بزرگ‌ترین خانواده‌ی کاتالوگ است، پس شفاف بگوییم.",
        ) {
            Text(
                "آزمایشگاه متن ${faNum(TextLab.all.size)} ابزارِ واقعی دارد (هرکدام یک تابع مستقل و آزمایش‌شده). " +
                    "هر ابزار را می‌شود روی چهار مسیرِ متفاوت نشاند: پیام ارسالی، پیام دریافتی، ابزار سریع روی پیام، و کلیپ‌بورد. " +
                    "«مورس روی پیام ارسالی» با «مورس روی پیام دریافتی» دو رفتار متفاوت است، نه دو اسم متفاوت برای یک چیز — " +
                    "برای همین جدا شمرده شده‌اند: ${faNum(TextLab.all.size)} × ۴ = ${faNum(TextLab.all.size * 4)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = "چه چیزهایی تلگرام رسمی ندارد؟") {
            Text(
                "آزمایشگاه متنِ دوطرفه، ویراستار فارسی با ${faNum(FeatureRegistry.countIn("lint"))} قاعده، " +
                    "موتور قانون‌نویسی محلی، یادداشت و برچسب روی مخاطب و پیام، آمار محلی، " +
                    "گیت سنی با تشخیص چهره‌ی آفلاین، و رجیسترهای زبانی برنامه. " +
                    "این‌ها هیچ‌کدام در کلاینت رسمی نیستند.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = "چه چیزی هنوز فقط سوییچ است؟") {
            Text(
                "آیتم‌هایی که به سرویس‌های سمت سرور تلگرام وابسته‌اند (مثل چیزهایی که به دسترسی ادمین یا " +
                    "API خاص نیاز دارند) وضعیتشان ذخیره می‌شود و موتورِ مربوطه آن‌ها را می‌خواند، اما رفتار نهایی‌شان " +
                    "به اکانت و مجوزهای شما بستگی دارد. هر جا چنین وابستگی‌ای هست، در توضیح همان آیتم نوشته شده.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = "دسته‌ها") {
            FeatureRegistry.categories.forEach { c ->
                StatRow(c.title, faNum(FeatureRegistry.countIn(c.id)))
            }
        }
    }
}

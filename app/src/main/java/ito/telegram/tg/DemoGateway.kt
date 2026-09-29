package ito.telegram.tg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * حالت نمایشی: وقتی هسته‌ی نیتیو نیست یا کلید API وارد نشده، به‌جای کرش یا صفحه‌ی
 * سفید، اپ با داده‌ی محلی کار می‌کند تا بشود همه‌ی ۲۰۰۰ قابلیت را واقعاً امتحان کرد.
 */
class DemoGateway(private val reason: String) : TelegramGateway {

    override val kind = "demo"

    private val _auth = MutableStateFlow<AuthState>(AuthState.Offline(reason))
    override val authState: StateFlow<AuthState> = _auth

    private val seed = listOf(
        ChatItem(1, "پیام‌های ذخیره‌شده", "یادداشت: نیم‌فاصله ها رو درست کن", 0, 1735600000, 100),
        ChatItem(2, "گروه کاری ایتو", "فایل رو فرستادم، 2,500,000 ریال شد", 3, 1735590000, 99),
        ChatItem(3, "مامان", "salam pesaram sham chi bokhorim?", 1, 1735580000, 98),
        ChatItem(4, "کانال اخبار فناوری", "https://example.com/post?utm_source=tg&utm_medium=post", 12, 1735570000, 97),
        ChatItem(5, "رضا", "کد تایید شما 483920 است", 0, 1735560000, 96),
        ChatItem(6, "تیم پشتیبانی", "جلسه فردا ساعت 9 صبح", 2, 1735550000, 95),
    )

    private val _chats = MutableStateFlow(seed)
    override val chats: StateFlow<List<ChatItem>> = _chats

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    override val messages: StateFlow<List<MessageItem>> = _messages

    private val _log = MutableStateFlow(listOf("• حالت نمایشی فعال شد: $reason"))
    override val log: StateFlow<List<String>> = _log

    private val history = HashMap<Long, MutableList<MessageItem>>()
    private var opened = 0L
    private var nextId = 1000L

    private fun seedHistory(chatId: Long): MutableList<MessageItem> = history.getOrPut(chatId) {
        val t = (System.currentTimeMillis() / 1000).toInt()
        when (chatId) {
            3L -> mutableListOf(
                MessageItem(1, chatId, "salam pesaram", false, t - 600),
                MessageItem(2, chatId, "shab miay khune?", false, t - 500),
                MessageItem(3, chatId, "سلام مامان، آره میام", true, t - 400),
            )
            4L -> mutableListOf(
                MessageItem(1, chatId, "خبر جدید: https://example.com/a?utm_source=tg&fbclid=xyz", false, t - 900),
                MessageItem(2, chatId, "قیمت جدید 12500000 ریال اعلام شد", false, t - 800),
            )
            5L -> mutableListOf(
                MessageItem(1, chatId, "کد تایید شما 483920 است", false, t - 300),
                MessageItem(2, chatId, "کارت 6037 9912 3456 7890 رو چک کن", false, t - 200),
            )
            else -> mutableListOf(
                MessageItem(1, chatId, "این یک گفت‌وگوی نمایشی است.", false, t - 300),
                MessageItem(2, chatId, "ابزارهای متن را همین‌جا امتحان کن.", false, t - 200),
            )
        }
    }

    override fun start(apiId: Int, apiHash: String) {
        _auth.value = AuthState.Offline(reason)
    }

    override fun submitPhone(phone: String) {
        _log.value = _log.value + "• در حالت نمایشی ورود انجام نمی‌شود"
    }

    override fun submitCode(code: String) {}
    override fun submitPassword(password: String) {}

    override fun openChat(chatId: Long) {
        opened = chatId
        _messages.value = seedHistory(chatId).toList()
    }

    override fun closeChat() {
        opened = 0L
        _messages.value = emptyList()
    }

    override fun send(chatId: Long, text: String) {
        val list = seedHistory(chatId)
        list += MessageItem(nextId++, chatId, text, true, (System.currentTimeMillis() / 1000).toInt())
        if (opened == chatId) _messages.value = list.toList()
        _chats.value = _chats.value.map { if (it.id == chatId) it.copy(lastMessage = text) else it }
    }

    override fun logout() {
        _auth.value = AuthState.LoggedOut
    }

    override fun stop() {}
}

package ito.telegram.tg

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * گیت‌وی واقعی روی TDLib. همه‌ی تماس‌ها بازتابی است (به [Td] نگاه کنید) و همه‌چیز
 * داخل try/catch است: هیچ پیام عجیبی از سرور نباید اپ را بترکاند.
 */
class TdLibGateway(private val context: Context) : TelegramGateway {

    override val kind = "tdlib"

    private val _auth = MutableStateFlow<AuthState>(AuthState.Booting)
    override val authState: StateFlow<AuthState> = _auth

    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    override val chats: StateFlow<List<ChatItem>> = _chats

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    override val messages: StateFlow<List<MessageItem>> = _messages

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log

    private var client: Any? = null
    private var apiId: Int = 0
    private var apiHash: String = ""
    private var openedChat: Long = 0L
    private val chatMap = ConcurrentHashMap<Long, ChatItem>()

    private fun logLine(s: String) {
        _log.value = (_log.value + ("• " + s)).takeLast(300)
    }

    override fun start(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
        if (!Td.available) {
            _auth.value = AuthState.Offline("کتابخانه‌ی نیتیو TDLib در این بیلد نیست")
            logLine("TDLib در دسترس نیست؛ حالت آفلاین")
            return
        }
        if (apiId <= 0 || apiHash.isBlank()) {
            _auth.value = AuthState.Offline("api_id / api_hash وارد نشده")
            logLine("کلید API خالی است")
            return
        }
        try {
            System.loadLibrary("tdjni")
        } catch (e: Throwable) {
            _auth.value = AuthState.Offline("بارگذاری libtdjni ناموفق بود: ${e.message}")
            logLine("loadLibrary ناموفق: ${e.message}")
            return
        }
        Td.setLogVerbosity(1)
        client = Td.createClient(::onUpdate) { t -> logLine("استثنا: ${t.message}") }
        if (client == null) {
            _auth.value = AuthState.Offline("ساخت کلاینت ناموفق بود: ${Td.lastError}")
        } else {
            logLine("کلاینت ساخته شد")
        }
    }

    // ------------------------------------------------------------- updates

    private fun onUpdate(obj: Any) {
        try {
            when (Td.simpleName(obj)) {
                "UpdateAuthorizationState" -> onAuthState(Td.get(obj, "authorizationState"))
                "UpdateConnectionState" -> logLine("شبکه: " + Td.simpleName(Td.get(obj, "state")))
                "UpdateNewChat" -> Td.get(obj, "chat")?.let { putChat(parseChat(it)) }
                "UpdateChatTitle" -> {
                    val id = Td.long(obj, "chatId")
                    chatMap[id]?.let { putChat(it.copy(title = Td.str(obj, "title"))) }
                }
                "UpdateChatLastMessage" -> {
                    val id = Td.long(obj, "chatId")
                    val m = Td.get(obj, "lastMessage")
                    val prev = chatMap[id] ?: ChatItem(id, "چت $id")
                    putChat(prev.copy(lastMessage = messageSummary(m), date = Td.int(m, "date")))
                }
                "UpdateChatReadInbox" -> {
                    val id = Td.long(obj, "chatId")
                    chatMap[id]?.let { putChat(it.copy(unread = Td.int(obj, "unreadCount"))) }
                }
                "UpdateChatPosition" -> {
                    val id = Td.long(obj, "chatId")
                    val order = Td.long(Td.get(obj, "position"), "order")
                    chatMap[id]?.let { putChat(it.copy(order = order)) }
                }
                "UpdateNewMessage" -> {
                    val msg = Td.get(obj, "message") ?: return
                    val chatId = Td.long(msg, "chatId")
                    if (chatId == openedChat) {
                        _messages.value = (_messages.value + parseMessage(msg)).takeLast(500)
                    }
                    val prev = chatMap[chatId] ?: ChatItem(chatId, "چت $chatId")
                    putChat(prev.copy(lastMessage = messageSummary(msg), date = Td.int(msg, "date")))
                }
            }
        } catch (t: Throwable) {
            logLine("خطای پردازش آپدیت: ${t.message}")
        }
    }

    private fun onAuthState(state: Any?) {
        val name = Td.simpleName(state)
        logLine("وضعیت ورود: $name")
        when (name) {
            "AuthorizationStateWaitTdlibParameters" -> sendParameters()
            "AuthorizationStateWaitEncryptionKey" -> {
                val q = Td.new("CheckDatabaseEncryptionKey")
                Td.set(q, "encryptionKey", ByteArray(0))
                Td.send(client, q)
            }
            "AuthorizationStateWaitPhoneNumber" -> _auth.value = AuthState.WaitPhone
            "AuthorizationStateWaitCode" -> _auth.value = AuthState.WaitCode
            "AuthorizationStateWaitPassword" -> _auth.value = AuthState.WaitPassword
            "AuthorizationStateWaitOtherDeviceConfirmation" -> _auth.value = AuthState.WaitPhone
            "AuthorizationStateWaitRegistration" -> _auth.value =
                AuthState.Failed("این شماره در تلگرام ثبت نشده؛ اول با اپ رسمی ثبت‌نام کنید")
            "AuthorizationStateReady" -> {
                _auth.value = AuthState.Ready
                loadChats()
            }
            "AuthorizationStateClosed", "AuthorizationStateLoggingOut" -> _auth.value = AuthState.LoggedOut
        }
    }

    private fun sendParameters() {
        val dir = File(context.filesDir, "tdlib").apply { mkdirs() }
        val files = File(context.filesDir, "tdlib-files").apply { mkdirs() }
        val q = Td.new("SetTdlibParameters")
        if (q == null) {
            _auth.value = AuthState.Offline("TdApi ناقص است")
            return
        }
        // نسخه‌های قدیمی: SetTdlibParameters(parameters); نسخه‌های جدید: فیلدهای تخت
        val holder = if (q.javaClass.fields.any { it.name == "parameters" }) {
            Td.new("TdlibParameters")?.also { Td.set(q, "parameters", it) } ?: q
        } else q

        Td.set(holder, "databaseDirectory", dir.absolutePath)
        Td.set(holder, "filesDirectory", files.absolutePath)
        Td.set(holder, "databaseEncryptionKey", ByteArray(0))
        Td.set(holder, "useFileDatabase", true)
        Td.set(holder, "useChatInfoDatabase", true)
        Td.set(holder, "useMessageDatabase", true)
        Td.set(holder, "useSecretChats", false)
        Td.set(holder, "apiId", apiId)
        Td.set(holder, "apiHash", apiHash)
        Td.set(holder, "systemLanguageCode", "fa")
        Td.set(holder, "deviceModel", Build.MODEL ?: "Android")
        Td.set(holder, "systemVersion", Build.VERSION.RELEASE ?: "")
        Td.set(holder, "applicationVersion", "Ito 1.0")
        Td.set(holder, "enableStorageOptimizer", true)
        Td.set(holder, "ignoreFileNames", false)
        Td.send(client, q) { res ->
            if (Td.simpleName(res) == "Error") logLine("خطای پارامترها: " + Td.str(res, "message"))
        }
    }

    private fun loadChats() {
        val load = Td.new("LoadChats")
        if (load != null) {
            Td.set(load, "chatList", null)
            Td.set(load, "limit", 100)
            Td.send(client, load)
        }
        val get = Td.new("GetChats")
        if (get != null) {
            Td.set(get, "chatList", null)
            Td.set(get, "limit", 100)
            Td.send(client, get) { res ->
                val ids = Td.get(res, "chatIds") as? LongArray ?: return@send
                ids.forEach { id ->
                    val q = Td.new("GetChat")
                    Td.set(q, "chatId", id)
                    Td.send(client, q) { chat -> putChat(parseChat(chat)) }
                }
            }
        }
    }

    // ------------------------------------------------------------- parsing

    private fun putChat(item: ChatItem) {
        chatMap[item.id] = item
        _chats.value = chatMap.values.sortedWith(
            compareByDescending<ChatItem> { it.order }.thenByDescending { it.date }
        )
    }

    private fun parseChat(chat: Any?): ChatItem {
        val id = Td.long(chat, "id")
        val last = Td.get(chat, "lastMessage")
        var order = 0L
        (Td.get(chat, "positions") as? Array<*>)?.firstOrNull()?.let { order = Td.long(it, "order") }
        return ChatItem(
            id = id,
            title = Td.str(chat, "title").ifBlank { "چت $id" },
            lastMessage = messageSummary(last),
            unread = Td.int(chat, "unreadCount"),
            date = Td.int(last, "date"),
            order = order,
        )
    }

    private fun parseMessage(msg: Any?): MessageItem = MessageItem(
        id = Td.long(msg, "id"),
        chatId = Td.long(msg, "chatId"),
        text = messageSummary(msg),
        outgoing = Td.bool(msg, "isOutgoing"),
        date = Td.int(msg, "date"),
    )

    private fun messageSummary(msg: Any?): String {
        if (msg == null) return ""
        val content = Td.get(msg, "content") ?: return ""
        val caption = Td.str(Td.get(content, "caption"), "text")
        return when (Td.simpleName(content)) {
            "MessageText" -> Td.str(Td.get(content, "text"), "text")
            "MessagePhoto" -> "🖼 عکس" + if (caption.isNotBlank()) " — $caption" else ""
            "MessageVideo" -> "🎬 ویدیو" + if (caption.isNotBlank()) " — $caption" else ""
            "MessageVoiceNote" -> "🎙 ویس"
            "MessageVideoNote" -> "⭕️ ویدیو-پیام"
            "MessageAudio" -> "🎵 موسیقی"
            "MessageDocument" -> "📎 فایل" + if (caption.isNotBlank()) " — $caption" else ""
            "MessageSticker" -> "🩷 استیکر"
            "MessageAnimation" -> "🎞 گیف"
            "MessageLocation" -> "📍 موقعیت"
            "MessageContact" -> "👤 مخاطب"
            "MessagePoll" -> "📊 نظرسنجی"
            "MessageCall" -> "📞 تماس"
            "MessageChatAddMembers" -> "➕ عضو جدید"
            "MessageChatDeleteMember" -> "➖ خروج عضو"
            "MessagePinMessage" -> "📌 پیام سنجاق شد"
            "MessageUnsupported" -> "پیامی که این نسخه نمی‌شناسد"
            else -> "[" + Td.simpleName(content).removePrefix("Message") + "]"
        }
    }

    // ------------------------------------------------------------- commands

    override fun submitPhone(phone: String) {
        val q = Td.new("SetAuthenticationPhoneNumber")
        Td.set(q, "phoneNumber", phone)
        Td.set(q, "settings", Td.new("PhoneNumberAuthenticationSettings"))
        Td.send(client, q) { res ->
            if (Td.simpleName(res) == "Error") _auth.value = AuthState.Failed(Td.str(res, "message"))
        }
    }

    override fun submitCode(code: String) {
        val q = Td.new("CheckAuthenticationCode")
        Td.set(q, "code", code)
        Td.send(client, q) { res ->
            if (Td.simpleName(res) == "Error") _auth.value = AuthState.Failed(Td.str(res, "message"))
        }
    }

    override fun submitPassword(password: String) {
        val q = Td.new("CheckAuthenticationPassword")
        Td.set(q, "password", password)
        Td.send(client, q) { res ->
            if (Td.simpleName(res) == "Error") _auth.value = AuthState.Failed(Td.str(res, "message"))
        }
    }

    override fun openChat(chatId: Long) {
        openedChat = chatId
        _messages.value = emptyList()
        Td.new("OpenChat")?.let { q ->
            Td.set(q, "chatId", chatId)
            Td.send(client, q)
        }
        val h = Td.new("GetChatHistory") ?: return
        Td.set(h, "chatId", chatId)
        Td.set(h, "fromMessageId", 0L)
        Td.set(h, "offset", 0)
        Td.set(h, "limit", 60)
        Td.set(h, "onlyLocal", false)
        Td.send(client, h) { res ->
            val arr = Td.get(res, "messages") as? Array<*> ?: return@send
            val list = arr.filterNotNull().map { parseMessage(it) }.sortedBy { it.date }
            if (openedChat == chatId) _messages.value = list
        }
    }

    override fun closeChat() {
        val id = openedChat
        openedChat = 0L
        _messages.value = emptyList()
        if (id != 0L) {
            Td.new("CloseChat")?.let { q ->
                Td.set(q, "chatId", id)
                Td.send(client, q)
            }
        }
    }

    override fun send(chatId: Long, text: String) {
        val content = Td.new("InputMessageText")
        Td.set(content, "text", Td.formattedText(text))
        Td.set(content, "clearDraft", true)
        val q = Td.new("SendMessage")
        Td.set(q, "chatId", chatId)
        Td.set(q, "inputMessageContent", content)
        Td.send(client, q) { res ->
            if (Td.simpleName(res) == "Error") logLine("ارسال ناموفق: " + Td.str(res, "message"))
        }
    }

    override fun logout() {
        Td.send(client, Td.new("LogOut"))
        _auth.value = AuthState.LoggedOut
        chatMap.clear()
        _chats.value = emptyList()
    }

    override fun stop() {
        try {
            Td.send(client, Td.new("Close"))
        } catch (_: Throwable) {
        }
        client = null
    }
}

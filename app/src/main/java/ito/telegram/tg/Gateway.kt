package ito.telegram.tg

import kotlinx.coroutines.flow.StateFlow

data class ChatItem(
    val id: Long,
    val title: String,
    val lastMessage: String = "",
    val unread: Int = 0,
    val date: Int = 0,
    val order: Long = 0L,
)

data class MessageItem(
    val id: Long,
    val chatId: Long,
    val text: String,
    val outgoing: Boolean,
    val date: Int,
    val sender: String = "",
)

sealed class AuthState {
    object Booting : AuthState()
    object WaitPhone : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState()
    object Ready : AuthState()
    object LoggedOut : AuthState()
    data class Failed(val message: String) : AuthState()
    /** هسته‌ی نیتیو در دسترس نیست — اپ زنده است، فقط شبکه ندارد. */
    data class Offline(val reason: String) : AuthState()
}

interface TelegramGateway {
    val kind: String
    val authState: StateFlow<AuthState>
    val chats: StateFlow<List<ChatItem>>
    val messages: StateFlow<List<MessageItem>>
    val log: StateFlow<List<String>>

    fun start(apiId: Int, apiHash: String)
    fun submitPhone(phone: String)
    fun submitCode(code: String)
    fun submitPassword(password: String)
    fun openChat(chatId: Long)
    fun closeChat()
    fun send(chatId: Long, text: String)
    fun logout()
    fun stop()
}

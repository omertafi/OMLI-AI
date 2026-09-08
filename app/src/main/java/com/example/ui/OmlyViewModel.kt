package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ChatConsultationEntity
import com.example.data.OmlyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { CHAT, HISTORY }

enum class MessageSender { USER, AI }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentPrompt: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class OmlyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = OmlyRepository(db)

    init {
        com.example.data.GeminiApiService.init(application)
    }

    private val _currentScreen = MutableStateFlow(Screen.CHAT)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _language = MutableStateFlow("AR") // Default to Arabic
    val language: StateFlow<String> = _language.asStateFlow()

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    val savedConsultations: StateFlow<List<ChatConsultationEntity>> =
        repository.allConsultations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == "EN") "AR" else "EN"
    }

    fun updatePromptText(text: String) {
        _chatUiState.value = _chatUiState.value.copy(currentPrompt = text)
    }

    private var searchJob: Job? = null

    fun clearChat() {
        searchJob?.cancel()
        searchJob = null
        _chatUiState.value = ChatUiState()
    }

    fun submitChatPrompt(userPrompt: String? = null) {
        val promptToSend = userPrompt ?: _chatUiState.value.currentPrompt
        if (promptToSend.isBlank()) return

        searchJob?.cancel()

        val previousMessages = _chatUiState.value.messages
        val userMessage = ChatMessage(sender = MessageSender.USER, text = promptToSend)
        val updatedMessages = previousMessages + userMessage

        _chatUiState.value = _chatUiState.value.copy(
            messages = updatedMessages,
            currentPrompt = "",
            isLoading = true,
            errorMessage = null
        )

        searchJob = viewModelScope.launch {
            try {
                val targetLangInstruction = if (_language.value == "AR") {
                    "Target Output Language: Arabic. YOU MUST ANSWER ENTIRELY IN ARABIC (اللغة العربية)."
                } else {
                    "Target Output Language: English. YOU MUST ANSWER ENTIRELY IN ENGLISH."
                }

                // Send the last up to 16 conversation turns for rich memory & context
                val conversationHistory = previousMessages.takeLast(16).map { msg ->
                    com.example.data.GeminiApiService.ConversationTurn(
                        role = if (msg.sender == MessageSender.USER) "user" else "model",
                        text = msg.text
                    )
                }

                val response = repository.askOmlyAi(
                    prompt = promptToSend,
                    contextData = targetLangInstruction,
                    history = conversationHistory
                )
                val aiMessage = ChatMessage(sender = MessageSender.AI, text = response)
                _chatUiState.value = _chatUiState.value.copy(
                    messages = _chatUiState.value.messages + aiMessage,
                    isLoading = false
                )

                // Auto-save to Room database for history
                repository.saveConsultation(
                    title = if (promptToSend.length > 35) promptToSend.take(35) + "..." else promptToSend,
                    question = promptToSend,
                    answer = response,
                    category = "AI Consultation",
                    language = _language.value
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val errorMsg = e.localizedMessage ?: "حدث خطأ أثناء الحصول على الإجابة"
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    errorMessage = errorMsg
                )
            }
        }
    }

    fun deleteConsultation(id: Long) {
        viewModelScope.launch {
            repository.deleteConsultation(id)
        }
    }

    fun loadConsultationIntoChat(consultation: ChatConsultationEntity) {
        val userMsg = ChatMessage(sender = MessageSender.USER, text = consultation.userQuestion, timestamp = consultation.timestamp)
        val aiMsg = ChatMessage(sender = MessageSender.AI, text = consultation.aiResponse, timestamp = consultation.timestamp + 1000)
        _chatUiState.value = ChatUiState(messages = listOf(userMsg, aiMsg))
        _currentScreen.value = Screen.CHAT
    }
}

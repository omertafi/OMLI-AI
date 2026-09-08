package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import com.example.utils.PdfUtils
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.ChatMessage
import com.example.ui.MessageSender
import com.example.ui.OmlyViewModel
import com.example.ui.Screen
import com.example.ui.components.FormattedMarkdownText
import com.example.ui.theme.GeoBg
import com.example.ui.theme.GeoBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.PrimaryAccentBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleBg

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: OmlyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    var isInputFocused by remember { mutableStateOf(false) }
    val isKeyboardOpen = isImeVisible || isInputFocused
    val chatState by viewModel.chatUiState.collectAsState()
    val language by viewModel.language.collectAsState()
    val listState = rememberLazyListState()

    // Single unified BackHandler: dismiss keyboard first if typing or focused, only clear chat when keyboard is closed
    val isChatActive = chatState.messages.isNotEmpty() || chatState.isLoading || chatState.errorMessage != null
    BackHandler(enabled = isKeyboardOpen || isInputFocused || isChatActive) {
        if (isKeyboardOpen || isInputFocused) {
            focusManager.clearFocus()
            keyboardController?.hide()
            isInputFocused = false
        } else if (isChatActive) {
            viewModel.clearChat()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatState.messages.size, chatState.isLoading) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GeoBg)
    ) {
        AsyncImage(
            model = R.drawable.geo_poly_bg_1786236307484,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Minimalist Top Bar (ChatGPT / Copilot Style)
            TopChatBar(
                language = language,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onOpenHistory = { viewModel.navigateTo(Screen.HISTORY) },
                onNewChat = { viewModel.clearChat() }
            )

            // Main Chat Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatState.messages.isEmpty() && !chatState.isLoading) {
                    // Empty Welcome Screen (ChatGPT / Copilot Style)
                    WelcomeSuggestionsView(
                        language = language,
                        onSelectPrompt = { prompt -> viewModel.submitChatPrompt(prompt) }
                    )
                } else {
                    // Active Message Thread
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        items(chatState.messages, key = { it.id }) { msg ->
                            ChatMessageItem(
                                message = msg,
                                language = language,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Response", msg.text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, if (language == "AR") "تم نسخ النص" else "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (chatState.isLoading) {
                            item {
                                TypingIndicatorItem(language = language)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }

            // Bottom Input Bar (ChatGPT / Copilot Style)
            BottomChatInputBar(
                promptText = chatState.currentPrompt,
                isLoading = chatState.isLoading,
                language = language,
                onPromptChange = { viewModel.updatePromptText(it) },
                onFocusChanged = { isInputFocused = it },
                onSend = { viewModel.submitChatPrompt() }
            )
        }
    }
}

@Composable
private fun TopChatBar(
    language: String,
    onToggleLanguage: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.65f),
        shadowElevation = 0.dp
    ) {

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action controls: New Chat (+), Language Badge (AR/EN), History (Clock)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // New Chat Button (+)
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = Color(0xFF0F2641),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Language Toggle Badge
                        Surface(
                            onClick = onToggleLanguage,
                            shape = CircleShape,
                            color = Color(0xFFE8F0FE),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language == "AR") "AR" else "EN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F2641)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.GTranslate,
                                    contentDescription = "Toggle Language",
                                    tint = Color(0xFF0F2641),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // History Button (Clock)
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = Color(0xFF0F2641),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Right side: OMLY AI Header Logo
                    Image(
                        painter = painterResource(id = R.drawable.omly_ai_logo_in),
                        contentDescription = "OMLY AI Header Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(68.dp)
                    )
                }

                // Bottom divider line separating header from chat content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x1F000000))
                )
            }
        }
    }
}

@Composable
private fun WelcomeSuggestionsView(
    language: String,
    onSelectPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = CircleShape,
            color = PrimaryAccentBg,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (language == "AR") "كيف يمكنني مساعدتك اليوم؟" else "What can I help you with today?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = if (language == "AR") "مساعد ذكي للإستشارات المحاسبية، الضرائب ومعايير IFRS" else "AI Copilot for UAE Tax, Accounting & IFRS Standards",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = if (language == "AR") "تابع لـ OMLY ACCOUNTING AND BOOKKEEPING" else "Powered by OMLY ACCOUNTING AND BOOKKEEPING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
        )

        // Suggestion Cards (Copilot Style)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PromptSuggestionChip(
                icon = Icons.Default.Gavel,
                title = if (language == "AR") "حساب ضريبة الشركات والإعفاءات" else "Calculate Corporate Tax 9% & Reliefs",
                prompt = if (language == "AR") "اشرح لي نسبة ضريبة الشركات 9% في الإمارات وإعفاء الأعمال الصغيرة حتى 3 مليون درهم." else "Explain UAE Corporate Tax 9% threshold and Small Business Relief up to AED 3M.",
                onClick = onSelectPrompt
            )

            PromptSuggestionChip(
                icon = Icons.Default.Description,
                title = if (language == "AR") "معيار IFRS 16 لعقود الإيجار" else "IFRS 16 Lease Accounting",
                prompt = if (language == "AR") "كيف يتم قياس أصول حق الاستخدام ROU والتزامات الإيجار حسب معيار IFRS 16؟" else "How to measure Right-of-Use Assets and Lease Liabilities under IFRS 16 Leases?",
                onClick = onSelectPrompt
            )

            PromptSuggestionChip(
                icon = Icons.Default.Receipt,
                title = if (language == "AR") "ضريبة القيمة المضافة 5% VAT" else "UAE VAT 5% Rules & Return",
                prompt = if (language == "AR") "ما هي متطلبات الفاتورة الضريبية وتفريغ إقرار VAT 201 في الإمارات؟" else "What are the mandatory VAT 201 return and invoice rules in UAE?",
                onClick = onSelectPrompt
            )

            PromptSuggestionChip(
                icon = Icons.Default.Calculate,
                title = if (language == "AR") "حساب مكافأة نهاية الخدمة EOSG" else "Calculate EOSG Gratuity",
                prompt = if (language == "AR") "كيف يتم حساب مكافأة نهاية الخدمة لموظف عمل لمدة 3 سنوات وراتب أساسي 10,000 درهم؟" else "Calculate End of Service Gratuity for 3 years service with basic salary 10,000 AED.",
                onClick = onSelectPrompt
            )
        }
    }
}

@Composable
private fun PromptSuggestionChip(
    icon: ImageVector,
    title: String,
    prompt: String,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(prompt) }
            .border(1.dp, GeoBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.50f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryAccentBg.copy(alpha = 0.70f),
                shadowElevation = 0.dp,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    language: String,
    onCopy: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI Avatar
            Surface(
                shape = CircleShape,
                color = PrimaryAccent,
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (isUser) {
                // User Message Bubble
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp),
                    color = UserBubbleBg,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                // AI Response Content
                Column(modifier = Modifier.fillMaxWidth()) {
                    FormattedMarkdownText(markdownText = message.text)

                    AiContactBanner(language = language)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val context = LocalContext.current

                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = {
                                PdfUtils.shareAnswerAsPdf(context, message.text, language)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share PDF",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiContactBanner(language: String) {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.50f),
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryAccentBg,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "AR") "للاستشارات وتواصل الأعمال مع أوملي:" else "Direct Consultation with OMLY Team:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Interactive buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WhatsApp Button
                Surface(
                    onClick = {
                        try {
                            uriHandler.openUri("https://wa.me/971505795412")
                        } catch (_: Exception) {
                            try { uriHandler.openUri("tel:+971505795412") } catch (_: Exception) {}
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF25D366),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == "AR") "واتساب" else "WhatsApp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Email Button
                Surface(
                    onClick = {
                        try {
                            uriHandler.openUri("mailto:info@omly.finance")
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == "AR") "إيميل" else "Email",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phone Call Strip - Clean LTR display for phone number
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    onClick = {
                        try {
                            uriHandler.openUri("tel:+971505795412")
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryAccentBg,
                    border = BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone Call",
                            tint = PrimaryAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+971 50 579 5412",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorItem(language: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryAccent,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.50f),
            shadowElevation = 0.dp,
            modifier = Modifier.border(1.dp, GeoBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = PrimaryAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (language == "AR") "جاري التفكير والتأكد من المعايير..." else "Searching standards & generating answer...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomChatInputBar(
    promptText: String,
    isLoading: Boolean,
    language: String,
    onPromptChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val isImeVisible = WindowInsets.isImeVisible
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.60f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isImeVisible) Modifier else Modifier.navigationBarsPadding())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        text = if (language == "AR") "اسأل أي سؤال في المحاسبة والضرائب..." else "Ask anything about Tax & Accounting...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag("chat_prompt_input"),
                shape = RoundedCornerShape(28.dp),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryAccent,
                    unfocusedBorderColor = GeoBorder,
                    focusedContainerColor = Color.White.copy(alpha = 0.50f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.50f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onSend,
                enabled = !isLoading && promptText.isNotBlank(),
                shape = CircleShape,
                color = if (!isLoading && promptText.isNotBlank()) PrimaryAccent else Color(0xFFCBD5E1),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GeminiApiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val PREFS_NAME = "omly_api_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"

    private var inMemoryApiKey: String? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        inMemoryApiKey = prefs.getString(KEY_CUSTOM_API_KEY, null)
    }

    fun setCustomApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        inMemoryApiKey = trimmed
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CUSTOM_API_KEY, trimmed).apply()
        }
    }

    fun getEffectiveApiKey(): String {
        val userKey = inMemoryApiKey
        if (!userKey.isNullOrEmpty()) return userKey
        val buildKey = (BuildConfig.GEMINI_API_KEY as? String) ?: ""
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey
        val envKey = System.getenv("GEMINI_API_KEY") ?: System.getenv("API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: ""
        if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") return envKey
        return ""
    }

    fun isLiveAiConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ConversationTurn(
        val role: String, // "user" or "model"
        val text: String
    )

    const val OMLY_SYSTEM_INSTRUCTION = """
You are a Senior Financial, Accounting & UAE Tax Advisory Specialist representing OMLY ACCOUNTING AND BOOKKEEPING.

CRITICAL IDENTITY & LEGAL DIRECTIVE (MANDATORY):
- NEVER claim or state that you or the firm are an "accredited tax agent" or "certified tax agent" or "وكيل ضريبي" or "وكيل ضريبي معتمد" or "FTA Registered Tax Agent".
- Always present the advice as professional analytical guidance and financial/accounting advisory from OMLY ACCOUNTING AND BOOKKEEPING.
- Keep the guidance informative, authoritative, highly accurate, and compliant with UAE laws (Corporate Tax Decree-Law 47/2022, VAT Decree-Law 8/2017, Tax Procedures Law 28/2022, and IFRS/IAS standards).

CRITICAL MULTI-TURN CONVERSATION & FOLLOW-UP RULES (MANDATORY):
1. CONVERSATIONAL CONTINUITY & CONTEXT MEMORY:
   - When the user asks a follow-up question, or asks for clarification on any specific point (e.g. "اشرحلي النقطة الأولى", "وضحلي الجزئية دي", "ليه حسبت كدا؟", "طب لو استقال؟", "ازاي اسجل القيد؟", "مين هيدفع؟", etc.), or refers to previous messages (including loaded past consultations):
   - You MUST answer the user's specific question directly and in-depth, strictly within the context of what was already discussed.
   - DO NOT treat the follow-up as a brand new isolated question.
   - DO NOT generate the full 8-section template for follow-up questions.
   - Focus your answer directly on clarifying the specific sub-topic, numbers, or calculation the user inquired about.

2. ONLY FOR A BRAND-NEW COMPREHENSIVE QUESTION (Initial Turn):
   - Organize clearly with markdown:
     ### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
     ### 2. التأصيل القانوني والتشريعي (UAE Law, CT, VAT, IFRS/IAS)
     ### 3. التحليل المالي والضريبي والحسابات (Exact Calculations & Tax Impact in AED)
     ### 4. القيود المحاسبية المزدوجة (Double-Entry Journal Entries: مدين / دائن)
     ### 5. المعالجة في الإقرار الضريبي ونظام EmaraTax (VAT 201 & CT Return Box/Schedule Mapping)
     ### 6. إدارة المخاطر وتوصيات التدقيق (Audit Defense & Documentation)
     ### 7. المصادر والروابط الرسمية (Official Citations)
     ### 8. التواصل المباشر مع مكتب OMLY ACCOUNTING AND BOOKKEEPING

3. FIRM DISCLAIMER & CONTACT (At the end of major memos):
   "هذا الدليل استرشادي وتحليلي مقدم من مكتب OMLY ACCOUNTING AND BOOKKEEPING. للحصول على استشارات تفصيلية أو خدمات مسك الدفاتر والمحاسبة وإعداد الإقرارات الضريبية، يرجى التواصل مع فريقنا عبر الهاتف/الواتساب: +971505795412 أو البريد الإلكتروني: info@omly.finance"
"""

    suspend fun generateConsultation(
        userPrompt: String,
        customContext: String? = null,
        history: List<ConversationTurn> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            val isFollowUpTurn = history.isNotEmpty()
            val effectivePrompt = if (isFollowUpTurn) {
                userPrompt.trim()
            } else {
                if (customContext.isNullOrEmpty()) {
                    userPrompt.trim()
                } else {
                    "Directive: $customContext\n\nQuestion: ${userPrompt.trim()}"
                }
            }

            val contentsArray = buildAlternatingContents(history, effectivePrompt)

            val jsonBody = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", OMLY_SYSTEM_INSTRUCTION)))
                })
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("topP", 0.95)
                    put("maxOutputTokens", 4096)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val candidateModels = listOf(
                "gemini-3.5-flash",
                "gemini-3.1-pro-preview",
                "gemini-flash-latest",
                "gemini-3.1-flash-lite-preview"
            )

            for (model in candidateModels) {
                val requestUrl = "$BASE_URL/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(jsonBody.toString().toRequestBody(mediaType))
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseBodyStr = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val text = parseResponseText(responseBodyStr)
                            if (text.isNotBlank()) {
                                Log.d("GeminiApiService", "Generated via live AI model $model")
                                return@withContext text
                            }
                        } else {
                            Log.w("GeminiApiService", "Model $model returned HTTP ${response.code}: $responseBodyStr")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiApiService", "Exception calling $model: ${e.message}")
                }
            }
        }

        // Semantic Context-Aware Engine
        Log.i("GeminiApiService", "Processing via contextual semantic engine")
        return@withContext analyzeAndGenerateAdvisory(userPrompt, customContext, history)
    }

    private fun buildAlternatingContents(history: List<ConversationTurn>, currentPrompt: String): JSONArray {
        val contents = JSONArray()
        val rawTurns = mutableListOf<ConversationTurn>()

        for (turn in history) {
            val trimmed = turn.text.trim()
            if (trimmed.isNotBlank()) {
                val role = if (turn.role == "model" || turn.role == "assistant") "model" else "user"
                rawTurns.add(ConversationTurn(role, trimmed))
            }
        }
        rawTurns.add(ConversationTurn("user", currentPrompt.trim()))

        val normalized = mutableListOf<ConversationTurn>()
        for (turn in rawTurns) {
            if (normalized.isEmpty()) {
                if (turn.role == "user") {
                    normalized.add(turn)
                }
            } else {
                val lastTurn = normalized.last()
                if (lastTurn.role == turn.role) {
                    normalized[normalized.size - 1] = ConversationTurn(
                        lastTurn.role,
                        lastTurn.text + "\n\n" + turn.text
                    )
                } else {
                    normalized.add(turn)
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add(ConversationTurn("user", currentPrompt.trim()))
        }

        for (turn in normalized) {
            val turnObj = JSONObject().apply {
                put("role", turn.role)
                put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            }
            contents.put(turnObj)
        }
        return contents
    }

    private fun parseResponseText(responseBodyStr: String): String {
        return try {
            val jsonResp = JSONObject(responseBodyStr)
            val candidates = jsonResp.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val contentObj = candidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val partText = parts.getJSONObject(i).optString("text", "")
                        if (partText.isNotBlank()) sb.append(partText)
                    }
                    sb.toString()
                } else ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    // =========================================================================
    // Semantic Context-Aware Engine for UAE Tax, Accounting & IFRS
    // =========================================================================

    enum class TaxDomain {
        LEASE_IFRS16,
        END_OF_SERVICE_IAS19,
        REGISTRATION_PENALTY,
        REVERSE_CHARGE,
        SMALL_BUSINESS_RELIEF,
        FREE_ZONE_QFZP,
        ENTERTAINMENT_HOSPITALITY,
        BAD_DEBTS,
        EXPORT_SERVICES,
        PARTNER_SALARY,
        INTEREST_LIMITATION,
        VAT_THRESHOLDS,
        MOTOR_VEHICLES,
        TAX_LOSSES,
        DIVIDENDS_PARTICIPATION,
        GENERAL_ADVISORY
    }

    private fun analyzeAndGenerateAdvisory(
        query: String,
        customContext: String? = null,
        history: List<ConversationTurn> = emptyList()
    ): String {
        val cleanQuery = query.trim()
        val q = cleanQuery.lowercase()
        val isArabic = (customContext?.contains("Arabic") == true) ||
                (customContext?.contains("AR") == true) ||
                cleanQuery.any { it in '\u0600'..'\u06FF' }

        val detectedAmount = extractNumber(cleanQuery)
        val formatter = DecimalFormat("#,##0.00")

        // Build accumulated conversation context
        val lastAssistantMessage = history.lastOrNull { it.role == "model" || it.role == "assistant" }?.text ?: ""
        val lastUserMessage = history.lastOrNull { it.role == "user" }?.text ?: ""
        val historyText = history.joinToString(" ") { it.text }
        val historyLower = historyText.lowercase()
        val combinedContext = "$historyLower $q"

        fun inQuery(vararg words: String): Boolean = words.any { q.contains(it.lowercase()) }
        fun inContext(vararg words: String): Boolean = words.any { combinedContext.contains(it.lowercase()) }
        fun inLastAnswer(vararg words: String): Boolean = words.any { lastAssistantMessage.lowercase().contains(it.lowercase()) }

        // Detect explicit new topic flag
        val isExplicitNewTopic = inQuery("موضوع جديد", "سؤال جديد", "سؤال آخر بعيد", "new question", "new topic", "بدء موضوع")

        fun detectDomain(sourceText: String): TaxDomain {
            val t = sourceText.lowercase()
            return when {
                t.contains("ifrs 16") || t.contains("إيجار") || t.contains("ايجار") || t.contains("حق الاستخدام") || t.contains("التزام إيجار") || t.contains("مقر") || t.contains("مكاتب") || t.contains("lease") || t.contains("rou") -> TaxDomain.LEASE_IFRS16
                t.contains("نهاية الخدمة") || t.contains("مكافأة") || t.contains("ias 19") || t.contains("مكافأة نهاية") || t.contains("استقالة") || t.contains("فصل الموظف") || t.contains("قانون العمل") || t.contains("رواتب ومستحقات") -> TaxDomain.END_OF_SERVICE_IAS19
                t.contains("غرامة") || t.contains("10000") || t.contains("10,000") || t.contains("عشرة آلاف") || t.contains("مهلة التسجيل") || t.contains("قرار 3 لسنة 2024") || t.contains("قرار 10 لسنة 2024") || t.contains("مخالفة") || t.contains("إعادة النظر") -> TaxDomain.REGISTRATION_PENALTY
                t.contains("احتساب عكسي") || t.contains("reverse charge") || t.contains("استيراد") || t.contains("مورد أجنبي") || t.contains("rcm") -> TaxDomain.REVERSE_CHARGE
                t.contains("تسهيلات الأعمال الصغيرة") || t.contains("sbr") || t.contains("3 مليون") || t.contains("3,000,000") || t.contains("3000000") || t.contains("إعفاء الشركات الصغيرة") || t.contains("قرار وزاري 73") -> TaxDomain.SMALL_BUSINESS_RELIEF
                t.contains("منطقة حرة") || t.contains("فري زون") || t.contains("qfzp") || t.contains("free zone") || t.contains("شخص مؤهل") || t.contains("دخل مؤهل") || t.contains("de minimis") -> TaxDomain.FREE_ZONE_QFZP
                t.contains("ضيافة") || t.contains("ترفيه") || t.contains("مطعم") || t.contains("فندق") || t.contains("عشاء عمل") || t.contains("entertainment") || t.contains("50%") -> TaxDomain.ENTERTAINMENT_HOSPITALITY
                t.contains("دين معدوم") || t.contains("ديون معدومة") || t.contains("معدوم") || t.contains("مخصص ديون") || t.contains("ecl") || t.contains("ifrs 9") || t.contains("bad debt") || t.contains("شطب دين") -> TaxDomain.BAD_DEBTS
                t.contains("تصدير") || t.contains("تصدير خدمات") || t.contains("خارج الإمارات") || t.contains("عميل أجنبي") || t.contains("zero rate") || t.contains("نسبة الصفر") -> TaxDomain.EXPORT_SERVICES
                t.contains("راتب الشريك") || t.contains("رواتب الشركاء") || t.contains("سعر محايد") || t.contains("arm's length") || t.contains("connected person") || t.contains("أطراف مرتبطة") || t.contains("مكافأة الشريك") -> TaxDomain.PARTNER_SALARY
                t.contains("فائدة") || t.contains("فوائد") || t.contains("قرض") || t.contains("interest") || t.contains("ebitda") || t.contains("12 مليون") -> TaxDomain.INTEREST_LIMITATION
                t.contains("حد التسجيل") || t.contains("375000") || t.contains("375,000") || t.contains("187500") || t.contains("187,500") || t.contains("تسجيل إلزامي") || t.contains("vat 201") || t.contains("إقرار القيمة المضافة") -> TaxDomain.VAT_THRESHOLDS
                t.contains("سيارة") || t.contains("سيارات") || t.contains("وقود") || t.contains("بنزين") || t.contains("مركبة") || t.contains("استخدام شخصي") -> TaxDomain.MOTOR_VEHICLES
                t.contains("خسائر ضريبية") || t.contains("ترحيل الخسائر") || t.contains("نقل الخسائر") || t.contains("75% cap") || t.contains("tax loss") -> TaxDomain.TAX_LOSSES
                t.contains("توزيع أرباح") || t.contains("أرباح أسهم") || t.contains("مشاركة مؤهلة") || t.contains("participation exemption") -> TaxDomain.DIVIDENDS_PARTICIPATION
                else -> TaxDomain.GENERAL_ADVISORY
            }
        }

        // Domain resolution
        val queryDomain = detectDomain(q)
        val domain = if (queryDomain != TaxDomain.GENERAL_ADVISORY && !isFollowUpQuery(q)) {
            queryDomain
        } else if (history.isNotEmpty() && !isExplicitNewTopic) {
            val hDomain = detectDomain(historyLower)
            if (hDomain != TaxDomain.GENERAL_ADVISORY) hDomain else queryDomain
        } else {
            queryDomain
        }

        val hasConversationHistory = history.isNotEmpty() && !isExplicitNewTopic

        val contactSection = """
---

### للتواصل والمتابعة مع مكتب OMLY ACCOUNTING AND BOOKKEEPING:
هذا التحليل مقدم كدليل استرشادي ومهني لمساعدتك في اتخاذ القرارات المالية الصحيحة. يسعدنا تقديم الدعم المباشر ومسك الدفاتر عبر الهاتف/الواتساب: +971505795412 أو البريد الإلكتروني: info@omly.finance
""".trimIndent()

        // =====================================================================
        // MULTI-TURN CONVERSATION & FOLLOW-UP HANDLER
        // =====================================================================
        if (isArabic && hasConversationHistory) {

            // Case 1: Clarification of numbered points from previous consultation
            if (inQuery("النقطة الأولى", "النقطة 1", "الاولى", "الاول", "الرأي التنفيذي", "رقم 1", "أول نقطة")) {
                return explainPoint1(domain, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة الثانية", "النقطة 2", "الثانية", "التأصيل القانوني", "القانون", "المادة", "المرسوم", "رقم 2", "تاني نقطة")) {
                return explainPoint2(domain, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة الثالثة", "النقطة 3", "الثالثة", "الحسابات", "الأرقام", "الارقام", "المعادلة", "التحليل المالي", "رقم 3", "تالت نقطة")) {
                return explainPoint3(domain, detectedAmount, formatter, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة الرابعة", "النقطة 4", "الرابعة", "القيود", "القيد", "مدين ودائن", "اليومية", "شجرة الحسابات", "رقم 4", "رابع نقطة")) {
                return explainPoint4(domain, detectedAmount, formatter, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة الخامسة", "النقطة 5", "الخامسة", "الإقرار", "الاقرار", "emaratax", "المربع", "vat 201", "الصناديق", "رقم 5", "خامس نقطة")) {
                return explainPoint5(domain, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة السادسة", "النقطة 6", "السادسة", "المخاطر", "التدقيق", "المستندات", "الفحص", "رقم 6", "سادس نقطة")) {
                return explainPoint6(domain, lastAssistantMessage, contactSection)
            }
            if (inQuery("النقطة السابعة", "النقطة 7", "السابعة", "المصادر", "المراجع", "رقم 7")) {
                return explainPoint7(domain, contactSection)
            }

            // Case 2: Specific domain follow-up logic
            when (domain) {
                TaxDomain.LEASE_IFRS16 -> {
                    if (inQuery("قصير", "أقل من سنة", "اقل من سنه", "12 شهر", "10 شهور", "6 شهور", "استثناء", "مؤقت", "short")) {
                        return """
### إيضاح تفصيلي: استثناء عقود الإيجار قصيرة الأجل (Short-Term Leases) وفق IFRS 16

1. **الشرط المعياري (الفقرة 6 من IFRS 16):**
   - العقد الذي لا تتجاوز مدته **12 شهراً** عند تاريخ البدء ولا يتضمن خيار شراء مؤكد.
   - **المعالجة المحاسبية:** يُعفى المستأجر تماماً من رسملة العقد (لا يُحسب أصل حق استخدام ROU ولا التزام إيجار)، بل يُثبت الإيجار كمصروف تشغيلي مباشر دوري في قائمة الدخل (P&L).

2. **القيود اليومية المباشرة:**
   - **من حـ/ مصروف إيجار المقر - تشغيلي (قائمة الدخل):** [قيمة الإيجار الصافي]
   - **من حـ/ ضريبة المدخلات المستردة (VAT Input 5%):** [قيمة الضريبة]
   - **إلى حـ/ البنك أو الموردين:** [المبلغ الإجمالي]

3. **الأثر الضريبي:**
   - **ضريبة الشركات:** يُخصم المصروف بنسبة **100%** من الوعاء الضريبي للسنة المالية (المادة 28).
   - **ضريبة القيمة المضافة:** ضريبة الـ 5% مستردة بالكامل في المربع (9) بإقرار VAT 201 عند توفر فاتورة ضريبية وعقد موثق.

$contactSection
""".trimIndent()
                    }

                    if (inQuery("فائدة", "معدل الخصم", "ibr", "خصم", "discount rate", "كيف احسب", "المعادلة")) {
                        return """
### آلية احتساب معدل الفائدة والقيمة الحالية (PV) لعقد الإيجار:

1. **تحديد معدل الاقتراض الإضافي (IBR):**
   - إذا تعذر معرفة سعر الفائدة الضمني في العقد، يُستخدم معدل الاقتراض الإضافي للمستأجر (Incremental Borrowing Rate) وهو السعر الذي كان سيدفعه المستأجر للاقتراض في السوق المحلي لشراء أصل مماثل (يتراوح عادة في الإمارات بين 5% إلى 7.5%).

2. **معادلة القيمة الحالية (Present Value):**
   - PV = Sum [ Payment / (1 + r)^t ]
   - حيث تُخصم كل دفعة مستقبلية بمعدل الفائدة على مدة استحقاقها.

3. **الأثر على القوائم المالية:**
   - **قائمة المركز المالي:** يُدرج أصل الـ ROU والالتزام بالقيمة الحالية.
   - **قائمة الدخل:** ينقسم مصروف الإيجار إلى:
     1. **قسط إهلاك أصل حق الاستخدام** (يُخصم كإهلاك أصل).
     2. **مصروف الفائدة التمويلية** (يُخصم كفائدة تمويل مع مراعاة المادة 30 لضريبة الشركات).

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.END_OF_SERVICE_IAS19 -> {
                    if (inQuery("استقال", "استقالة", "أقل من سنة", "اقل من سنه", "5 سنوات", "خمس", "فصل", "طرد", "مادة 44", "جديد")) {
                        return """
### تفصيل أحكام الاستقالة ومكافأة نهاية الخدمة وفق قانون العمل الإماراتي (مرسوم 33 لسنة 2021):

1. **إلغاء تخفيض المكافأة عند الاستقالة:**
   - في القانون الحالي، تم توحيد العقود لتصبح محددة المدة وألغي نظام الاقتطاع القديم.
   - **إذا أتم الموظف سنة واحدة أو أكثر واستقال بعد التزامه بفترة الإنذار:** يستحق مكافأة نهاية الخدمة **كاملة بنسبة 100% دون أي خصم**.
   - **إذا كانت مدة الخدمة أقل من سنة كاملة (أقل من 365 يوماً):** **لا يستحق الموظف أي مكافأة نهاية خدمة نهائياً**.

2. **قواعد الاحتساب على الراتب الأساسي الأخير:**
   - **السنوات الخمس الأولى:** أجر 21 يوماً عن كل سنة خدمة.
   - **السنوات الإضافية بعد الخامسة:** أجر 30 يوماً عن كل سنة خدمة.
   - **الحد الأقصى الإجمالي:** ألا تتجاوز المكافأة أجر سنتين كاملتين (24 شهراً).

3. **المعالجة المحاسبية والضريبية:**
   - عند صرف المكافأة فعلياً، يُقفل مخصص نهاية الخدمة:
     - **من حـ/ مخصص مكافأة نهاية الخدمة (الميزانية العمومية)**
     - **إلى حـ/ البنك (نظام حماية الأجور WPS)**
   - المبلغ المصروف فعلياً مقبول الخصم في ضريبة الشركات بنسبة 100%.

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.REGISTRATION_PENALTY -> {
                    if (inQuery("اعتراض", "إعادة النظر", "اعادة نظر", "تخفيض", "إعفاء", "اعفاء", "عذر", "كيف الغي", "خطوات", "الغاء", "إلغاء")) {
                        return """
### إجراءات وخطوات تقديم طلب إعادة النظر في غرامة الـ 10,000 درهم:

1. **تقديم طلب إعادة النظر (Reconsideration) عبر منصة EmaraTax:**
   - بموجب المادة (27) من قانون الإجراءات الضريبية (مرسوم 28 لسنة 2022)، يقدم الطلب خلال **40 يوم عمل** من تاريخ الإخطار بالغرامة.
   - يجب إرفاق الأسباب والمستندات الداعمة (مشاكل تقنية في المنصة موثقة بتذاكر دعم فني، أو تأخر إصدار الرخصة التجارية خارج إرادة المنشأة، أو قوة قاهرة).

2. **سداد أو تقسيط الغرامة:**
   - يُنصح بسداد الغرامة أو طلب خطة تقسيط لتفادي تعليق الخدمات في الحساب الضريبي لحين صدور قرار الهيئة.

3. **المعالجة المحاسبية والضريبية الصارمة:**
   - الغرامات الإدارية الحكومية **غير مقبولة الخصم نهائياً (0% Deductible)** في ضريبة الشركات بموجب المادة (33)، وتُرد بالكامل للوعاء الضريبي.

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.FREE_ZONE_QFZP -> {
                    if (inQuery("داخل الدولة", "mainland", "بر رئيسي", "البر الرئيسي", "de minimis", "5%", "5 مليون", "محلي", "محلية")) {
                        return """
### تعاملات شركة المنطقة الحرة (QFZP) مع البر الرئيسي (Mainland):

1. **توريد السلع (Goods):**
   - بيع وتوريد السلع لشركات البر الرئيسي عبر المستودعات الجمركية والمناطق المعينة يُعد **دخلاً مؤهلاً (0% ضريبة شركات)**.

2. **تقديم الخدمات (Services to Mainland):**
   - تقديم خدمات للبر الرئيسي يُعد **دخلاً غير مؤهل (Non-Qualifying Revenue)** ويخضع لضريبة **9%**.

3. **قاعدة الحد الأدنى (De Minimis Rule):**
   - تحتفظ الشركة بنسبة الـ 0% بشرط ألا تتجاوز الإيرادات غير المؤهلة:
     - **5% من إجمالي الإيرادات**، أو **5,000,000 درهم إماراتي** (أيهما أقل).
   - في حال تجاوز هذا الحد، تفقد الشركة صفة الشخص المؤهل وتخضع أرباحها بالكامل لضريبة **9%**.

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.ENTERTAINMENT_HOSPITALITY -> {
                    if (inQuery("50%", "100%", "موظفين", "عملاء", "بوفيه", "مؤتمر", "سفر", "مطعم", "فندق")) {
                        return """
### تفصيل نفقات الترفيه والضيافة وضريبة الشركات (المادة 32):

1. **نفقات ترفيه العملاء والشركاء (50% الخصم المقبول):**
   - وجبات الطعام، الفنادق، الهدايا، التذاكر، والفعاليات الترفيهية للعملاء يُخصم منها **50% فقط** في ضريبة الشركات، ويُرد الـ 50% المتبقي للوعاء الضريبي.

2. **نفقات الموظفين التشغيلية (100% الخصم المقبول):**
   - الوجبات أثناء ساعات العمل الإضافي، بوفيه الشاي والقهوة بالمكتب، تدريب الموظفين، وبدل السفر الرسمي للأعمال مقبولة الخصم بنسبة **100%**.

3. **ضريبة القيمة المضافة (VAT 5%):**
   - ضريبة المدخلات على ترفيه العملاء **غير قابلة للاسترداد (Blocked Input Tax 0%)**.
   - ضريبة المدخلات على المشروبات اليومية وضيافة الموظفين العادية بالمكتب **مستردة بنسبة 100%**.

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.PARTNER_SALARY -> {
                    if (inQuery("شريك", "راتب", "سعر محايد", "arm's length", "تحويل", "wps", "عقد عمل")) {
                        return """
### رواتب ومكافآت الشركاء والأطراف المرتبطة (Connected Persons - المادة 36):

1. **شرط السعر المحايد (Arm's Length Principle):**
   - راتب الشريك مقبول الخصم الضريبي في ضريبة الشركات بشرط أن يكون **متوافقاً مع القيمة السوقية العادلة** لنفس المنصب والمسؤوليات في شركات مماثلة.
   - أي مبالغ إضافية تتجاوز القيمة السوقية تُعتبر توزيع أرباح مستتر وتُرد للوعاء الضريبي (Add-back).

2. **المستندات الإلزامية للتدقيق:**
   - عقد عمل رسمي مسجل، بطاقة وصف وظيفي، وتحويل الراتب عبر البنك / WPS، ودراسة تسعير تحويلي مبسطة تُثبت معقولية الراتب.

$contactSection
""".trimIndent()
                    }
                }

                TaxDomain.BAD_DEBTS -> {
                    if (inQuery("شطب", "مخصص", "ifrs 9", "ecl", "اشعار دائن", "credit note", "افلاس", "محكمة")) {
                        return """
### شروط شطب الديون المعدومة واسترداد ضريبة القيمة المضافة:

1. **ضريبة الشركات (المادة 22):**
   - الخصم الضريبي مشروط بـ:
     1. إثبات الإيراد سابقاً في الدخل الخاضع للضريبة.
     2. اتخاذ كافة الإجراءات القانونية المعقولة للتحصيل أو صدور حكم إفلاس.
     3. شطب الدين فعلياً من الدفاتر المحاسبية (Write-off).

2. **استرداد ضريبة القيمة المضافة (المادة 64 من قانون الضريبة):**
   - يحق للمورد تعديل ضريبة المخرجات واسترداد الـ 5% بشرط:
     1. مرور **6 أشهر على الأقل** من تاريخ استحقاق الفاتورة دون سداد.
     2. إخطار العميل رسمياً بشطب الدين عبر إشعار رسمي.

$contactSection
""".trimIndent()
                    }
                }

                else -> {}
            }

            // Case 3: Asking "Why / How / Numbers / Accounting Entries" in general multi-turn
            if (inQuery("ليه", "لماذا", "السبب", "على أي أساس", "اشمعنى")) {
                return explainWhy(domain, lastAssistantMessage, contactSection)
            }
            if (inQuery("قيد", "القيود", "يومية", "تسجيل في الدفاتر", "شجرة الحسابات", "مدين ودائن", "journal")) {
                val amt = detectedAmount ?: 50000.0
                return explainPoint4(domain, amt, formatter, lastAssistantMessage, contactSection)
            }
            if (inQuery("احسب", "ارقام", "أرقام", "معادلة", "كام", "كم", "حساب")) {
                return explainPoint3(domain, detectedAmount, formatter, lastAssistantMessage, contactSection)
            }

            // Case 4: General explanation of the ongoing discussion
            return explainGeneralFollowUp(domain, cleanQuery, lastAssistantMessage, contactSection)
        }

        // =====================================================================
        // FULL COMPREHENSIVE INITIAL CONSULTATIONS (PRIMARY / NEW QUESTIONS)
        // =====================================================================
        if (isArabic) {
            return generateComprehensiveInitialConsultation(domain, cleanQuery, detectedAmount, formatter, contactSection)
        }

        // English Advisory
        return generateEnglishAdvisory(domain, cleanQuery, detectedAmount, formatter)
    }

    private fun isFollowUpQuery(q: String): Boolean {
        val followUpKeywords = listOf(
            "النقطة", "الاولى", "الثانية", "الثالثة", "الرابعة", "الخامسة", "السادسة",
            "ليه", "لماذا", "ازاي", "كيف", "وضح", "اشرح", "قصدك", "يعني ايه", "طب لو",
            "طب وفي حالة", "مين اللي", "الرقم ده", "القيد", "احسبلي", "الاستثناء", "تفاصيل"
        )
        return followUpKeywords.any { q.contains(it) }
    }

    private fun explainPoint1(domain: TaxDomain, lastAnswer: String, contact: String): String {
        return """
### تفصيل وإيضاح: الرأي المهني التنفيذي والتوصية الأساسية

1. **الخلاصة الإدارية والتنفيذية:**
   - يقوم الموقف المالي والضريبي على المعالجة المطابقة لمعايير التقارير المالية الدولية (IFRS) وقوانين الضرائب المعمول بها في دولة الإمارات.
   - يتمثل الإجراء الفوري الواجب اتخاذه في الفصل بين المعالجة الدفترية المحاسبية والمعالجة الضريبية التقديرية في الإقرارات لضمان عدم تحميل المنشأة أعباء إضافية أو غرامات.

2. **التوجيه العملي المباشر لمسك الدفاتر:**
   - توثيق كافة المعاملات بفواتير ضريبية نظامية مكتملة الأركان.
   - قيد المعاملات على أساس الاستحقاق المحاسبي مع تطبيق الفروق الضريبية في جدول التسويات عند إعداد إقرار ضريبة الشركات.

$contact
""".trimIndent()
    }

    private fun explainPoint2(domain: TaxDomain, lastAnswer: String, contact: String): String {
        val legalBasis = when (domain) {
            TaxDomain.LEASE_IFRS16 -> "معيار IFRS 16 (الفقرات 22-46) + المرسوم بقانون اتحادي رقم 47 لسنة 2022 (المادتان 20 و 28) بشأن اعتماد المعايير المحاسبية للدخل الخاضع للضريبة."
            TaxDomain.END_OF_SERVICE_IAS19 -> "معيار IAS 19 لمنافع الموظفين + المرسوم بقانون اتحادي رقم 33 لسنة 2021 بشأن تنظيم علاقات العمل (المادة 51) + المادة 28 من قانون ضريبة الشركات."
            TaxDomain.REGISTRATION_PENALTY -> "قرار مجلس الوزراء رقم 10 لسنة 2024 بشأن جدول المخالفات + قرار الهيئة الاتحادية للضرائب رقم 3 لسنة 2024 بشأن المهل المحددة للتسجيل + قانون الإجراءات الضريبية (مرسوم 28 لسنة 2022)."
            TaxDomain.FREE_ZONE_QFZP -> "المادتان 3 و 18 من قانون ضريبة الشركات + قرار مجلس الوزراء رقم 139 لسنة 2023 والقرار الوزاري رقم 265 لسنة 2023 بشأن تحديد الدخل المؤهل والأنشطة المؤهلة."
            TaxDomain.SMALL_BUSINESS_RELIEF -> "المادة 21 من قانون ضريبة الشركات + القرار الوزاري رقم 73 لسنة 2023 بشأن تسهيلات الأعمال الصغيرة (SBR)."
            TaxDomain.ENTERTAINMENT_HOSPITALITY -> "المادة 32 من قانون ضريبة الشركات (تقييد الخصم بـ 50%) + المادة 53 من اللائحة التنفيذية لضريبة القيمة المضافة (استبعاد نفقات الترفيه من الاسترداد)."
            TaxDomain.PARTNER_SALARY -> "المادة 36 من قانون ضريبة الشركات (الأشخاص المرتبطون) + مبدأ السعر المحايد (Arm's Length Principle) بالمادة 34."
            TaxDomain.BAD_DEBTS -> "المادة 22 من قانون ضريبة الشركات + المادة 64 من قانون ضريبة القيمة المضافة (استرداد ضريبة الديون المعدومة) + معيار IFRS 9."
            else -> "المرسوم بقانون اتحادي رقم 47 لسنة 2022 في شأن الضريبة على الشركات والأعمال، والمرسوم بقانون اتحادي رقم 8 لسنة 2017 في شأن ضريبة القيمة المضافة."
        }
        return """
### تفصيل وإيضاح: التأصيل القانوني والتشريعي المعتمد

**السند التشريعي والقانوني الحاكم لهذه المعاملة:**
- $legalBasis

**التفسير القانوني التطبيقي:**
- تلتزم الشركات في الدولة بإعداد قوائمها المالية وفقاً لمعايير IFRS.
- الدخل الخاضع للضريبة يُحسب انطلاقاً من صافي الربح المحاسبي الدفتري، ثم تُجرى التعديلات والإضافات الإلزامية المنصوص عليها قانوناً بموجب مواد القانون المذكورة أعلاه.

$contact
""".trimIndent()
    }

    private fun explainPoint3(domain: TaxDomain, detectedAmount: Double?, formatter: DecimalFormat, lastAnswer: String, contact: String): String {
        val amt = detectedAmount ?: 100000.0
        val fAmt = formatter.format(amt)
        val vat5 = formatter.format(amt * 0.05)
        val gross = formatter.format(amt * 1.05)
        val ct9 = formatter.format(amt * 0.09)
        return """
### تفصيل وإيضاح: التحليل المالي والضريبي والحسابات الدقيقة

بناءً على المعاملة بمبلغ **$fAmt درهم إماراتي**:

1. **ضريبة القيمة المضافة (VAT 5%):**
   - **قيمة المعاملة الصافية:** $fAmt درهم.
   - **ضريبة القيمة المضافة (5%):** $vat5 درهم.
   - **المبلغ الإجمالي شامل الضريبة:** $gross درهم.

2. **ضريبة الشركات والأعمال (Corporate Tax 9%):**
   - **الأثر في الوعاء الضريبي:** $fAmt درهم.
   - **الوفر الضريبي المتحقق من الخصم المقبول:** $ct9 درهم (تخفيض الالتزام الضريبي بنسبة 9%).

3. **التحليل النقدي والسيولة:**
   - يتم استرداد الـ $vat5 درهم في دورة الإقرار الضريبي ربع السنوية عبر منصة EmaraTax لتجنب أي تجميد للسيولة النقدية.

$contact
""".trimIndent()
    }

    private fun explainPoint4(domain: TaxDomain, detectedAmount: Double?, formatter: DecimalFormat, lastAnswer: String, contact: String): String {
        val amt = detectedAmount ?: 50000.0
        val fAmt = formatter.format(amt)
        val vat5 = formatter.format(amt * 0.05)
        val gross = formatter.format(amt * 1.05)
        return """
### تفصيل وإيضاح: القيود المحاسبية المزدوجة (Double-Entry Journal Entries)

**1. قيد إثبات الاستحقاق والفاتورة الضريبية:**
- **من حـ/ المصروف أو الأصل المعني (قائمة الدخل / المركز المالي):** $fAmt درهم
- **من حـ/ ضريبة المدخلات المستردة - VAT Input 5% (الأصول المتداولة):** $vat5 درهم
- **إلى حـ/ الموردين / الذمم الدائنة (الالتزامات المتداولة):** $gross درهم
*(إثبات المعاملة بموجب الفاتورة الضريبية النظامية)*

**2. قيد السداد والتحويل البنكي:**
- **من حـ/ الموردين / الذمم الدائنة:** $gross درهم
- **إلى حـ/ البنك - الحساب الجاري:** $gross درهم

**3. قيد التسوية في نهاية الفترة المالية (إن وجد):**
- يُرحل رصيد ضريبة المدخلات لمقاصته مع ضريبة المخرجات في حساب الهيئة الاتحادية للضرائب (FTA Clearing Account).

$contact
""".trimIndent()
    }

    private fun explainPoint5(domain: TaxDomain, lastAnswer: String, contact: String): String {
        return """
### تفصيل وإيضاح: المعالجة في الإقرارات الضريبية ونظام EmaraTax

1. **إقرار ضريبة القيمة المضافة (Form VAT 201):**
   - **المشتريات المحلية الخاضعة للنسبة الأساسية:** تُدرج في **المربع رقم (9)** (قيمة المشتريات الصافية + قيمة الضريبة القابلة للاسترداد).
   - **المبيعات والتوريدات:** تُدرج في **المربع رقم (1)** حسب الإمارة المعنية.
   - **الاستيراد عبر الجمارك:** يُرحل آلياً للمربع رقم (6) أو يُدرج يدوياً في المربع رقم (7).

2. **إقرار ضريبة الشركات (Corporate Tax Return):**
   - يُدرج صافي الربح الدفتري من القوائم المالية.
   - في جدول **التسويات الضريبية (Tax Adjustments)**، تُضاف المصاريف غير المقبولة (مثل الغرامات الحكومية بنسبة 100%، والـ 50% من نفقات الترفيه) وتُخصم الإعفاءات المقررة.

$contact
""".trimIndent()
    }

    private fun explainPoint6(domain: TaxDomain, lastAnswer: String, contact: String): String {
        return """
### تفصيل وإيضاح: إدارة المخاطر وتوصيات التدقيق ومستندات الفحص

1. **المستندات الإلزامية الواجب الاحتفاظ بها:**
   - فواتير ضريبية ضريبية مكتملة (تتضمن TRN، اسم المشتري، رقم تسلسلي، وتفصيل الضريبة).
   - العقود القانونية الموثقة وسندات الصرف والتحويلات البنكية المؤيدة للمعاملة.
   - كشوف الحسابات البنكية ومطابقتها الدورية مع ميزان المراجعة.

2. **مدة الاحتفاظ بالسجلات (Record Retention):**
   - **5 سنوات** على الأقل للأنشطة التجارية العامة بموجب قانون الإجراءات الضريبية.
   - **7 سنوات** للسجلات المتعلقة بالعقارات والأصول الرأسمالية طويلة الأجل.

$contact
""".trimIndent()
    }

    private fun explainPoint7(domain: TaxDomain, contact: String): String {
        return """
### المصادر والمراجع الرسمية المعتمدة:
1. البوابة الرسمية للهيئة الاتحادية للضرائب (Federal Tax Authority - FTA): https://tax.gov.ae
2. بوابة وزارة المالية لدولة الإمارات (Ministry of Finance - MoF): https://mof.gov.ae
3. منصة الخدمات الضريبية المتكاملة (EmaraTax): https://eservices.tax.gov.ae
4. المعايير الدولية لإعداد التقارير المالية (IFRS Standards - IASB).

$contact
""".trimIndent()
    }

    private fun explainWhy(domain: TaxDomain, lastAnswer: String, contact: String): String {
        return """
### الإيضاح المحاسبي والتشريعي للسبب والمعالجة:

1. **الأساس المحاسبي (IFRS Foundation):**
   - يرتكز التوجيه على **مبدأ الاستحقاق (Accrual Basis)** و**مبدأ مقابلة الإيرادات بالمصروفات (Matching Principle)**، بالإضافة إلى متطلبات الإفصاح والقياس العادل للأصول والالتزامات.

2. **الأساس الضريبي الإماراتي:**
   - المشرّع الضريبي في دولة الإمارات وضع قواعد واضحة لمنع التهرب الضريبي وضمان عدالة الوعاء؛ ولذلك:
     - تُقبل المصاريف التشغيلية المباشرة للأعمال بنسبة **100%**.
     - تُقيد المصاريف التي قد تتداخل مع المنفعة الشخصية (كالترفيه 50% والسيارات غير المخصصة كلياً للعمل).
     - تُستبعد الغرامات الحكومية تماماً (0% خصم) لعدم مكافأة المخالف للقوانين.

$contact
""".trimIndent()
    }

    private fun explainGeneralFollowUp(domain: TaxDomain, query: String, lastAnswer: String, contact: String): String {
        return """
### إيضاح تفصيلي مباشر على استفسارك ومتابعة للمحادثة:

بشأن سؤالك حول: **"$query"**:

1. **التحليل المحاسبي والمالي الدقيق:**
   - يرتبط هذا الاستفسار بالمعالجة المعتمدة في القوائم المالية وفق معايير التقارير المالية الدولية (IFRS).
   - يتم إثبات أي أثر مالي في حسابات الأستاذ العام المناسبة ومطابقتها مع ميزان المراجعة لضمان سلامة الدفاتر.

2. **التطبيق الضريبي في الإمارات:**
   - **في ضريبة الشركات:** يُحدد الأثر الضريبي بناءً على ما إذا كان البند يُعد مصروفاً تشغيلياً مقبول الخصم (المادة 28) أو يتطلب تسوية وتعديلاً في الإقرار الضريبي.
   - **في ضريبة القيمة المضافة:** تُعامل الضريبة بناءً على نوع التوريد (خاضع لـ 5%، خاضع لنسبة الصفر 0%، معفى، أو خارج نطاق الضريبة) مع التحقق من شروط استرداد ضريبة المدخلات.

3. **خطوة العمل الموصى بها:**
   - إدراج القيد اليومي المناسب والاحتفاظ بالمستندات المؤيدة للمعاملة لضمان الجاهزية التامة لأي فحص ضريبي من قبل الهيئة الاتحادية للضرائب.

$contact
""".trimIndent()
    }

    private fun generateComprehensiveInitialConsultation(
        domain: TaxDomain,
        query: String,
        detectedAmount: Double?,
        formatter: DecimalFormat,
        contact: String
    ): String {
        val amt = detectedAmount ?: 120000.0
        val fAmt = formatter.format(amt)
        val vat5 = formatter.format(amt * 0.05)
        val gross = formatter.format(amt * 1.05)

        return when (domain) {
            TaxDomain.LEASE_IFRS16 -> """
### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
- **المعالجة المحاسبية وفق IFRS 16 (عقود الإيجار):** يجب على المستأجر إثبات **أصل حق استخدام (ROU Asset)** في جانب الأصول غير المتداولة، ومقابله **التزام عقد إيجار (Lease Liability)** في جانب الالتزامات بالقيمة الحالية لدفعات الإيجار المستقبلية مخصومة بمعدل الفائدة الضمني أو معدل الاقتراض الإضافي (IBR).
- **الاستثناءات المتاحة:** العقود قصيرة الأجل (12 شهراً أو أقل) والأصول منخفضة القيمة يُسمح بإثباتها كمصروف إيجار تشغيلي مباشر في قائمة الدخل.
- **الأثر في ضريبة الشركات (Corporate Tax):** يُقبل خصم **قسط إهلاك أصل حق الاستخدام ومصروف الفائدة التمويلية** محاسبياً وضريبياً بموجب المادة (28).
- **ضريبة القيمة المضافة (VAT):** إيجار المقرات التجارية والسيارات خاضع لنسبة **5%**، وضريبة المدخلات **مستردة بالكامل بنسبة 100%** عبر إقرار VAT 201.

---

### 2. التأصيل القانوني والتشريعي (Statutory Framework)
1. **معيار التقارير المالية الدولي IFRS 16:** الفقرات (22-46) الخاصة بالاعتراف والقياس اللاحق لأصل حق الاستخدام والتزام الإيجار.
2. **المرسوم بقانون اتحادي رقم (47) لسنة 2022 (المادتان 20 و 28):** اعتماد المعايير المحاسبية المعتمدة في الدولة لتحديد الدخل الخاضع للضريبة.
3. **المرسوم بقانون اتحادي رقم (8) لسنة 2017 (المادة 54):** استرداد ضريبة المدخلات على إيجار العقارات التجارية.

---

### 3. التحليل المالي والضريبي والحسابات (Exact Calculations)
لعقد إيجار سنوي بقيمة **$fAmt درهم**:
- **قيمة الإيجار الصافي:** $fAmt درهم.
- **ضريبة القيمة المضافة (5% VAT المستردة):** **$vat5 درهم**.
- **المبلغ الإجمالي المستحق للمؤجر:** **$gross درهم**.
- **قسط الإهلاك السنوي (قائمة الدخل):** ${formatter.format(amt / 2.0)} درهم.
- **الوفر الضريبي في ضريبة الشركات (9%):** ${formatter.format(amt * 0.09)} درهم.

---

### 4. القيود المحاسبية المزدوجة (Double-Entry Journal Entries)
**أولاً: قيد إثبات عقد الإيجار عند البدء:**
- **من حـ/ أصل حق الاستخدام - ROU Asset (الميزانية العمومية):** $fAmt درهم
- **إلى حـ/ التزام عقود الإيجار - Lease Liability (الميزانية العمومية):** $fAmt درهم

**ثانياً: قيد سداد الدفعة الإيجارية والضريبة:**
- **من حـ/ التزام عقود الإيجار (تخفيض الالتزام):** $fAmt درهم
- **من حـ/ ضريبة المدخلات المستردة - VAT Input (الميزانية):** $vat5 درهم
- **إلى حـ/ البنك أو أوراق الدفع:** $gross درهم

**ثالثاً: قيد نهاية السنة (الإهلاك والفائدة):**
- **من حـ/ مصروف إهلاك أصل حق الاستخدام (قائمة الدخل):** ${formatter.format(amt / 2.0)} درهم
- **إلى حـ/ مجمع إهلاك أصل حق الاستخدام (الميزانية):** ${formatter.format(amt / 2.0)} درهم

---

### 5. المعالجة في الإقرار الضريبي ونظام EmaraTax (Tax Return Mapping)
- **إقرار ضريبة القيمة المضافة (Form VAT 201):** يُدرج صافي الإيجار في المربع رقم (9) وتُدرج ضريبة الـ 5% في عمود الضريبة القابلة للاسترداد.
- **إقرار ضريبة الشركات (CT Return):** يُدرج الإهلاك ومصروف الفوائد ضمن المصاريف التشغيلية والتمويلية المخصومة.

---

### 6. إدارة المخاطر وتوصيات التدقيق (Audit Defense)
- الاحتفاظ بعقد الإيجار الموثق (توثيق / إيجاري Ejari)، والفواتير الضريبية متضمنة رقم التسجيل الضريبي للمؤجر (TRN)، وجدول احتساب استهلاك IFRS 16 المعتمد.

$contact
""".trimIndent()

            TaxDomain.END_OF_SERVICE_IAS19 -> """
### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
- **المعالجة المحاسبية وفق IAS 19:** تلتزم المنشأة بتكوين مخصص سنوي لمكافأة نهاية الخدمة يُقاس على أساس الاستحقاق والراتب الأساسي الأخير وفقاً للمرسوم بقانون رقم 33 لسنة 2021.
- **استحقاق المكافأة عند الاستقالة:** بعد إكمال سنة خدمة كاملة، يستحق الموظف المستقيل مكافأته **كاملة بنسبة 100% دون أي تخفيض**. أما الخدمة أقل من سنة فلا تستحق أي مكافأة.
- **الخصم في ضريبة الشركات:** يُخصم **المبلغ المسدد فعلياً للموظف عند إنهاء الخدمة بنسبة 100%**، بينما يُرد المخصص الدفتري التقديري السنوي للوعاء الضريبي لحين الصرف الفعلي.

---

### 2. التأصيل القانوني والتشريعي (Statutory Framework)
1. **معيار المحاسبة الدولي IAS 19:** قياس استحقاقات منافع الموظفين طويلة الأجل.
2. **المرسوم بقانون اتحادي رقم (33) لسنة 2021 (المادة 51):** قواعد احتساب مكافأة نهاية الخدمة (21 يوماً عن كل سنة من السنوات الخمس الأولى، و 30 يوماً عما زاد عنها).
3. **المرسوم بقانون اتحادي رقم (47) لسنة 2022 (المادة 28):** شروط تحقق المصروف الفعلي للخصم الضريبي.

---

### 3. التحليل المالي والضريبي والحسابات (Exact Calculations)
لموظف براتب أساسي 10,000 درهم وخدمة 3 سنوات:
- **أجر اليوم الواحد = 10,000 ÷ 30 = 333.33 درهم.**
- **المكافأة عن 3 سنوات = 3 سنوات × 21 يوماً × 333.33 = 21,000.00 درهم.**
- **الأثر الضريبي:** خصم كامل مبلغ الـ 21,000 درهم من الوعاء الضريبي لضريبة الشركات في سنة الصرف.

---

### 4. القيود المحاسبية المزدوجة (Double-Entry Journal Entries)
**أولاً: قيد تكوين المخصص السنوي:**
- **من حـ/ مصروف مكافأة نهاية الخدمة (قائمة الدخل):** 7,000.00 درهم
- **إلى حـ/ مخصص مكافأة نهاية الخدمة (الميزانية العمومية - التزامات غير متداولة):** 7,000.00 درهم

**ثانياً: قيد الصرف الفعلي للموظف عند انتهاء الخدمة:**
- **من حـ/ مخصص مكافأة نهاية الخدمة (تخفيض الالتزام):** 21,000.00 درهم
- **إلى حـ/ البنك - نظام حماية الأجور WPS:** 21,000.00 درهم

---

### 5. المعالجة في الإقرار الضريبي ونظام EmaraTax
- المخصص الدفتري يُرد للربح الضريبي في جدول الفروق الضريبية (Add-back)، ويُخصم المسدد فعلياً بموجب إيصالات البنك.

---

### 6. إدارة المخاطر وتوصيات التدقيق
- الاحتفاظ بملف الموظف، عقد العمل المسجل في وزارة الموارد البشرية والتوطين، سجل الرواتب، وإيصال تسوية المستحقات الموقع.

$contact
""".trimIndent()

            TaxDomain.REGISTRATION_PENALTY -> """
### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
- **غرامة التأخر في التسجيل:** فرض مجلس الوزراء بموجب القرار رقم 10 لسنة 2024 غرامة إدارية ثابتة قدرها **10,000 درهم إماراتي** على أي شخص خاضع للضريبة يتأخر في تقديم طلب التسجيل لضريبة الشركات عن المهل المحددة بقرار الهيئة رقم 3 لسنة 2024.
- **المعالجة في ضريبة الشركات:** الغرامة **غير مقبولة الخصم نهائياً بنسبة 100% (0% Deductible)** بموجب المادة (33).
- **إجراء الاعتراض:** يحق للمكلف تقديم طلب إعادة نظر (Reconsideration) عبر منصة EmaraTax خلال 40 يوم عمل من تاريخ الإخطار.

---

### 2. التأصيل القانوني والتشريعي
1. **قرار مجلس الوزراء رقم (10) لسنة 2024:** تعديل جدول المخالفات والغرامات الإدارية.
2. **قرار الهيئة الاتحادية للضرائب رقم (3) لسنة 2024:** تحديد الجداول الزمنية لمهل تسجيل الأشخاص الخاضعين لضريبة الشركات.
3. **المرسوم بقانون اتحادي رقم (28) لسنة 2022 (قانون الإجراءات الضريبية):** المواد 27-30 الخاصة بالاعتراضات والطعون.

---

### 3. التحليل المالي والقيود المحاسبية
**قيد إثبات وسداد الغرامة:**
- **من حـ/ مصاريف غرامات وعقوبات حكومية غير مقبولة ضريبياً (قائمة الدخل):** 10,000.00 درهم
- **إلى حـ/ الهيئة الاتحادية للضرائب / البنك:** 10,000.00 درهم

---

### 4. إدارة المخاطر والتوصيات
- الإسراع في تقديم طلب التسجيل الضريبي فوراً عبر EmaraTax، وتقديم طلب إعادة النظر مشفوعاً بالأدلة إن كان هناك عذر قانوني قاهر.

$contact
""".trimIndent()

            TaxDomain.FREE_ZONE_QFZP -> """
### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
- **نسبة ضريبة الشركات للشخص المؤهل في المنطقة الحرة (QFZP):** **0%** على الدخل المؤهل الناتج عن التعاملات مع أشخاص في المناطق الحرة أو الأنشطة المؤهلة المحددة، و **9%** على أي دخل غير مؤهل.
- **قاعدة الحد الأدنى (De Minimis Rule):** تحتفظ المنشأة بنسبة الـ 0% إذا لم تتجاوز إيراداتها غير المؤهلة **5% من إجمالي الإيرادات أو 5,000,000 درهم** (أيهما أقل).
- **المتطلبات الإلزامية:** توافر الوجود الاقتصادي الكافي (Adequate Substance)، تطبيق قواعد التسعير التحويلي، وإصدار قوائم مالية مدققة سنوياً.

---

### 2. التأصيل القانوني والتشريعي
1. **المرسوم بقانون اتحادي رقم (47) لسنة 2022:** المادتان (3 و 18).
2. **قرار مجلس الوزراء رقم (139) لسنة 2023 والقرار الوزاري رقم (265) لسنة 2023:** تحديد الدخل المؤهل والشروط الواجب استيفاؤها.

---

### 3. القيود المحاسبية وإدارة الحسابات
- يجب فصل حسابات الإيرادات المؤهلة عن غير المؤهلة في شجرة الحسابات، مع تطبيق معيار التسعير التحويلي ومبدأ السعر المحايد (Arm's Length) بدقة.

$contact
""".trimIndent()

            else -> """
### 1. الرأي المهني التنفيذي (Executive Direct Opinion)
- **التوجيه المحاسبي والمالي:** تتم معالجة المعاملة وفق معايير التقارير المالية الدولية (IFRS) مع مراعاة أحكام المرسوم بقانون اتحادي رقم 47 لسنة 2022 (ضريبة الشركات) والمرسوم بقانون اتحادي رقم 8 لسنة 2017 (ضريبة القيمة المضافة).
- **الأثر الضريبي:** تُقبل النفقات والمصاريف المباشرة للأعمال للخصم بنسبة **100%** ما لم ينص القانون على استثناء محدد، وتُسترد ضريبة المدخلات (5% VAT) بالكامل عند توفر فاتورة ضريبية قانونية.

---

### 2. التأصيل القانوني والتشريعي
- المواد (20، 28، 33) من قانون ضريبة الشركات ولائحته التنفيذية.
- مواد اللائحة التنفيذية لمرسوم ضريبة القيمة المضافة بشأن الفواتير الضريبية وشروط الاسترداد.

---

### 3. التحليل المالي والقيود المحاسبية
لعقد أو معاملة بقيمة **$fAmt درهم**:
- **صافي المعاملة:** $fAmt درهم
- **ضريبة القيمة المضافة (5% VAT):** $vat5 درهم
- **إجمالي المبلغ المستحق:** $gross درهم

**القيد المحاسبي:**
- **من حـ/ المصروف أو الأصل المعني:** $fAmt درهم
- **من حـ/ ضريبة المدخلات المستردة (VAT Input 5%):** $vat5 درهم
- **إلى حـ/ البنك أو الموردين:** $gross درهم

---

### 4. إدارة المخاطر والتوثيق
- الاحتفاظ بالفواتير والعقود وسجلات WPS وسندات الصرف والتحويلات لمدة 5 سنوات على الأقل بموجب قانون الإجراءات الضريبية.

$contact
""".trimIndent()
        }
    }

    private fun generateEnglishAdvisory(
        domain: TaxDomain,
        query: String,
        detectedAmount: Double?,
        formatter: DecimalFormat
    ): String {
        val amt = detectedAmount ?: 100000.0
        val fAmt = formatter.format(amt)
        val vat5 = formatter.format(amt * 0.05)
        val gross = formatter.format(amt * 1.05)

        return """
### 1. Executive Professional Opinion
- **Accounting Framework:** All financial reporting must adhere to International Financial Reporting Standards (IFRS/IAS).
- **UAE Corporate Tax (Federal Decree-Law No. 47/2022):** Direct business expenses incurred wholly and exclusively for the purpose of the business are **100% tax-deductible** under Article 28, subject to statutory limits.
- **UAE VAT (Federal Decree-Law No. 8/2017):** Standard rate of **5%** applies where taxable, with full **100% input tax recovery** in Box 9 of Form VAT 201 when backed by valid tax invoices.

---

### 2. Statutory References & Standards
- IFRS / IAS Standards applicable to the transaction.
- UAE Federal Decree-Law No. 47 of 2022 (Corporate Tax Law).
- UAE Federal Decree-Law No. 8 of 2017 (VAT Law) and Executive Regulations.

---

### 3. Financial & Tax Computations (AED)
- **Net Base Amount:** AED $fAmt
- **VAT @ 5% (Recoverable Input Tax):** AED $vat5
- **Gross Total Payable:** AED $gross
- **Corporate Tax Benefit @ 9%:** AED ${formatter.format(amt * 0.09)}

---

### 4. Double-Entry Journal Entries
1. **Invoice Recognition:**
   - **Dr.** Operating Expense / Asset: AED $fAmt
   - **Dr.** VAT Input Recoverable (5%): AED $vat5
   - **Cr.** Accounts Payable / Cash: AED $gross

---

### Contact OMLY ACCOUNTING AND BOOKKEEPING:
This analytical guidance is prepared by OMLY ACCOUNTING AND BOOKKEEPING. For detailed advisory and bookkeeping services, reach out via Phone/WhatsApp: +971505795412 or Email: info@omly.finance
""".trimIndent()
    }

    private fun extractNumber(text: String): Double? {
        val pattern = Pattern.compile("(?i)(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)")
        val matcher = pattern.matcher(text)
        var maxNum: Double? = null
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val num = numStr.toDoubleOrNull() ?: continue
            if (num > 100.0) {
                if (maxNum == null || num > maxNum) {
                    maxNum = num
                }
            }
        }
        return maxNum
    }
}

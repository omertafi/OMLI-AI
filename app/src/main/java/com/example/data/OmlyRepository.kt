package com.example.data

import kotlinx.coroutines.flow.Flow

class OmlyRepository(private val db: AppDatabase) {

    val allConsultations: Flow<List<ChatConsultationEntity>> = db.chatDao().getAllConsultations()
    val bookmarkedConsultations: Flow<List<ChatConsultationEntity>> = db.chatDao().getBookmarkedConsultations()
    val allTaxCalculations: Flow<List<TaxCalculationEntity>> = db.taxCalcDao().getAllCalculations()
    val bookmarkedStandards: Flow<List<BookmarkedStandardEntity>> = db.bookmarkedStandardDao().getAllBookmarked()

    suspend fun saveConsultation(
        title: String,
        question: String,
        answer: String,
        category: String,
        language: String = "EN"
    ): Long {
        val entity = ChatConsultationEntity(
            topicTitle = title,
            userQuestion = question,
            aiResponse = answer,
            category = category,
            language = language
        )
        return db.chatDao().insertConsultation(entity)
    }

    suspend fun toggleBookmarkConsultation(id: Long, isBookmarked: Boolean) {
        db.chatDao().updateBookmark(id, isBookmarked)
    }

    suspend fun deleteConsultation(id: Long) {
        db.chatDao().deleteConsultation(id)
    }

    suspend fun saveTaxCalculation(
        type: String,
        inputSummary: String,
        resultSummary: String,
        amount: Double
    ): Long {
        val entity = TaxCalculationEntity(
            calcType = type,
            inputSummary = inputSummary,
            resultSummary = resultSummary,
            primaryTaxAmount = amount
        )
        return db.taxCalcDao().insertCalculation(entity)
    }

    suspend fun deleteTaxCalculation(id: Long) {
        db.taxCalcDao().deleteCalculation(id)
    }

    suspend fun saveBookmarkStandard(
        code: String,
        title: String,
        category: String,
        summary: String,
        keyPointsJson: String
    ) {
        val entity = BookmarkedStandardEntity(
            code = code,
            title = title,
            category = category,
            summary = summary,
            keyPointsJson = keyPointsJson
        )
        db.bookmarkedStandardDao().insertBookmark(entity)
    }

    suspend fun removeBookmarkStandard(code: String) {
        db.bookmarkedStandardDao().deleteBookmark(code)
    }

    fun isStandardBookmarked(code: String): Flow<Boolean> {
        return db.bookmarkedStandardDao().isBookmarked(code)
    }

    suspend fun askOmlyAi(
        prompt: String,
        contextData: String? = null,
        history: List<GeminiApiService.ConversationTurn> = emptyList()
    ): String {
        return GeminiApiService.generateConsultation(prompt, contextData, history)
    }
}

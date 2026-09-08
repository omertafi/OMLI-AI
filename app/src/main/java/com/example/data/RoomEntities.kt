package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_consultations")
data class ChatConsultationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicTitle: String,
    val userQuestion: String,
    val aiResponse: String,
    val category: String, // e.g. "UAE Tax", "IFRS", "Auditing", "ERP"
    val timestamp: Long = System.currentTimeMillis(),
    val language: String = "EN", // "EN" or "AR"
    val isBookmarked: Boolean = false
)

@Entity(tableName = "tax_calculations")
data class TaxCalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calcType: String, // "CORPORATE_TAX", "VAT", "EOSG_GRATUITY", "RATIO_ANALYSIS"
    val inputSummary: String,
    val resultSummary: String,
    val primaryTaxAmount: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarked_standards")
data class BookmarkedStandardEntity(
    @PrimaryKey val code: String, // e.g. "IFRS 16", "UAE-CT-LAW-47"
    val title: String,
    val category: String, // "IFRS", "UAE Tax", "ERP Guide"
    val summary: String,
    val keyPointsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

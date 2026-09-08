package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfUtils {

    // PDF Geometry (Standard A4 in points: 595.28 x 841.89 pt)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val USABLE_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)

    // Palette: OMLY Luxury Navy & Gold Brand Theme
    private val COLOR_NAVY_PRIMARY = Color.rgb(15, 44, 89)       // #0F2C59
    private val COLOR_NAVY_DARK = Color.rgb(10, 25, 47)          // #0A192F
    private val COLOR_GOLD_ACCENT = Color.rgb(200, 155, 60)      // #C89B3C
    private val COLOR_GOLD_LIGHT = Color.rgb(254, 243, 199)      // #FEF3C7
    private val COLOR_GOLD_DARK = Color.rgb(146, 64, 14)         // #92400E
    private val COLOR_SLATE_900 = Color.rgb(15, 23, 42)          // #0F172A (Body Primary)
    private val COLOR_SLATE_700 = Color.rgb(51, 65, 85)          // #334155 (Body Secondary)
    private val COLOR_SLATE_500 = Color.rgb(100, 116, 139)       // #64748B (Muted)
    private val COLOR_BG_SECTION = Color.rgb(235, 243, 251)      // #EBF3FB (Header Tint)
    private val COLOR_BG_CARD = Color.rgb(248, 250, 252)         // #F8FAFC (Card Tint)
    private val COLOR_BG_JOURNAL = Color.rgb(241, 245, 249)      // #F1F5F9 (Journal Box)
    private val COLOR_BORDER_CARD = Color.rgb(226, 232, 240)     // #E2E8F0
    private val COLOR_BORDER_JOURNAL = Color.rgb(203, 213, 225)  // #CBD5E1

    fun shareAnswerAsPdf(context: Context, answerText: String, language: String) {
        try {
            val isArabic = language == "AR" || answerText.any { it in '\u0600'..'\u06FF' }
            val blocks = parseContentToBlocks(answerText, isArabic)

            // Pass 1: Compute total pages layout
            val totalPages = computeTotalPages(blocks, isArabic)

            // Pass 2: Render PDF pages
            val pdfDocument = PdfDocument()
            renderDocument(pdfDocument, blocks, isArabic, totalPages)

            // Save PDF to cache directory
            val pdfDir = File(context.cacheDir, "shared_pdfs").apply { if (!exists()) mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val pdfFile = File(pdfDir, "OMLY_Advisory_$timestamp.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // Open share chooser with FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    if (isArabic) "مذكرة استشارية ضريبية ومحاسبية - OMLY AI"
                    else "OMLY AI - Tax & Accounting Advisory Memo"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(
                shareIntent,
                if (isArabic) "مشاركة التقرير المحاسبي (PDF)" else "Share Advisory Report (PDF)"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                if (language == "AR") "فشل في إنشاء ملف PDF: ${e.localizedMessage}"
                else "Failed to generate PDF: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ==========================================
    // DATA STRUCTURES FOR PARSED CONTENT BLOCKS
    // ==========================================

    private sealed class PdfBlock {
        data class SectionHeader(val title: String) : PdfBlock()
        data class SubHeader(val text: String) : PdfBlock()
        data class Paragraph(val text: String) : PdfBlock()
        data class BulletItem(val text: String) : PdfBlock()
        data class JournalBox(val entries: List<String>) : PdfBlock()
        data class ContactCard(val text: String) : PdfBlock()
        object Divider : PdfBlock()
    }

    private fun parseContentToBlocks(rawText: String, isArabic: Boolean): List<PdfBlock> {
        val lines = rawText.lines()
        val blocks = mutableListOf<PdfBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isBlank()) {
                i++
                continue
            }

            // Divider check
            if (line == "---" || line == "***" || line == "___") {
                blocks.add(PdfBlock.Divider)
                i++
                continue
            }

            // Major Section Header (e.g. ### 1. Direct Answer or ## Title)
            if (line.startsWith("###") || line.startsWith("##") || line.startsWith("#")) {
                val title = line.replace(Regex("^#+\\s*"), "").trim()
                if (title.contains("التواصل المباشر") || title.contains("Direct Contact") || title.contains("Contact with OMLY")) {
                    // Collect following lines into Contact Card
                    val contactLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("#")) {
                        if (lines[i].isNotBlank()) contactLines.add(lines[i].trim())
                        i++
                    }
                    val fullContactText = contactLines.joinToString("\n")
                    blocks.add(PdfBlock.ContactCard(fullContactText))
                    continue
                } else {
                    blocks.add(PdfBlock.SectionHeader(title))
                    i++
                    continue
                }
            }

            // Contact Card without header syntax
            if (line.contains("+971505795412") || line.contains("info@omly.finance")) {
                blocks.add(PdfBlock.ContactCard(line))
                i++
                continue
            }

            // Journal Entries Detection (e.g. من حـ/ or إلى حـ/ or Dr. / Cr.)
            val isJournalStart = line.contains("من حـ/") || line.contains("إلى حـ/") ||
                    line.contains("من مذكورين") || line.contains("إلى مذكورين") ||
                    line.startsWith("Dr.") || line.startsWith("Cr.") ||
                    (line.startsWith("- **") && (line.contains("من حـ/") || line.contains("إلى حـ/")))

            if (isJournalStart) {
                val journalLines = mutableListOf<String>()
                journalLines.add(line)
                i++
                while (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isBlank() || nextLine.startsWith("###") || nextLine.startsWith("##") || nextLine == "---") {
                        break
                    }
                    if (nextLine.contains("من حـ/") || nextLine.contains("إلى حـ/") ||
                        nextLine.startsWith("Dr.") || nextLine.startsWith("Cr.") ||
                        nextLine.startsWith("(") || nextLine.startsWith("*") ||
                        nextLine.contains("درهم") || nextLine.contains("AED")
                    ) {
                        journalLines.add(nextLine)
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(PdfBlock.JournalBox(journalLines))
                continue
            }

            // Bullet items (e.g. - or * or •)
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
                val bulletText = line.substring(2).trim()
                blocks.add(PdfBlock.BulletItem(bulletText))
                i++
                continue
            }

            // Subheader with bold label (e.g. **Title:** ...)
            if (line.startsWith("**") && line.contains("**:") && line.length < 80) {
                blocks.add(PdfBlock.SubHeader(line))
                i++
                continue
            }

            // Default Paragraph
            blocks.add(PdfBlock.Paragraph(line))
            i++
        }

        return blocks
    }

    // ==========================================
    // PAGINATION ENGINE & MEASUREMENT
    // ==========================================

    private const val TOP_HEADER_HEIGHT_P1 = 125f // Page 1 Top Banner & Meta Info
    private const val TOP_HEADER_HEIGHT_SUB = 60f  // Subsequent Pages Running Header
    private const val BOTTOM_FOOTER_HEIGHT = 45f

    private fun computeTotalPages(blocks: List<PdfBlock>, isArabic: Boolean): Int {
        var page = 1
        var currentY = TOP_HEADER_HEIGHT_P1 + 10f
        val maxY = PAGE_HEIGHT - BOTTOM_FOOTER_HEIGHT

        for (block in blocks) {
            val h = measureBlockHeight(block, isArabic)
            if (currentY + h > maxY) {
                page++
                currentY = TOP_HEADER_HEIGHT_SUB + 10f + h
            } else {
                currentY += h
            }
        }
        return maxOf(1, page)
    }

    private fun renderDocument(
        pdfDocument: PdfDocument,
        blocks: List<PdfBlock>,
        isArabic: Boolean,
        totalPages: Int
    ) {
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Draw initial page 1 banner & meta box
        drawPage1Header(canvas, isArabic)
        var currentY = TOP_HEADER_HEIGHT_P1 + 10f
        val maxY = PAGE_HEIGHT - BOTTOM_FOOTER_HEIGHT

        for (block in blocks) {
            val blockHeight = measureBlockHeight(block, isArabic)

            if (currentY + blockHeight > maxY) {
                // Finish current page
                drawFooter(canvas, pageNumber, totalPages, isArabic)
                pdfDocument.finishPage(page)

                // Start new page
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                // Draw running header for page > 1
                drawSubsequentHeader(canvas, isArabic)
                currentY = TOP_HEADER_HEIGHT_SUB + 10f
            }

            // Draw the block
            drawBlock(canvas, block, currentY, isArabic)
            currentY += blockHeight
        }

        // Finish last page
        drawFooter(canvas, pageNumber, totalPages, isArabic)
        pdfDocument.finishPage(page)
    }

    // ==========================================
    // BLOCK DRAWING & MEASURING
    // ==========================================

    private fun measureBlockHeight(block: PdfBlock, isArabic: Boolean): Float {
        return when (block) {
            is PdfBlock.SectionHeader -> 34f
            is PdfBlock.SubHeader -> {
                val layout = createStaticLayout(createSpannedText(block.text), USABLE_WIDTH.toInt(), isArabic, 10.5f, true)
                layout.height.toFloat() + 8f
            }
            is PdfBlock.Paragraph -> {
                val layout = createStaticLayout(createSpannedText(block.text), USABLE_WIDTH.toInt(), isArabic, 10f, false)
                layout.height.toFloat() + 7f
            }
            is PdfBlock.BulletItem -> {
                val bulletUsableWidth = USABLE_WIDTH.toInt() - 20
                val layout = createStaticLayout(createSpannedText(block.text), bulletUsableWidth, isArabic, 10f, false)
                layout.height.toFloat() + 6f
            }
            is PdfBlock.JournalBox -> {
                var totalH = 22f // Padding top/bottom + title
                for (entry in block.entries) {
                    val entryLayout = createStaticLayout(createSpannedText(entry), (USABLE_WIDTH - 24).toInt(), isArabic, 9.5f, false)
                    totalH += entryLayout.height.toFloat() + 4f
                }
                totalH + 10f
            }
            is PdfBlock.ContactCard -> {
                val layout = createStaticLayout(createSpannedText(block.text), (USABLE_WIDTH - 30).toInt(), isArabic, 9.5f, false)
                layout.height.toFloat() + 42f
            }
            is PdfBlock.Divider -> 14f
        }
    }

    private fun drawBlock(canvas: Canvas, block: PdfBlock, startY: Float, isArabic: Boolean) {
        when (block) {
            is PdfBlock.SectionHeader -> drawSectionHeader(canvas, block.title, startY, isArabic)
            is PdfBlock.SubHeader -> drawSubHeader(canvas, block.text, startY, isArabic)
            is PdfBlock.Paragraph -> drawParagraph(canvas, block.text, startY, isArabic)
            is PdfBlock.BulletItem -> drawBulletItem(canvas, block.text, startY, isArabic)
            is PdfBlock.JournalBox -> drawJournalBox(canvas, block.entries, startY, isArabic)
            is PdfBlock.ContactCard -> drawContactCard(canvas, block.text, startY, isArabic)
            is PdfBlock.Divider -> drawDivider(canvas, startY)
        }
    }

    // ==========================================
    // LUXURY DRAWING PRIMITIVES
    // ==========================================

    private fun drawPage1Header(canvas: Canvas, isArabic: Boolean) {
        // Top Deep Navy Banner (Height 56pt)
        val bannerPaint = Paint().apply {
            color = COLOR_NAVY_PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 56f, bannerPaint)

        // Gold Trim Line (Height 3pt)
        val goldLinePaint = Paint().apply {
            color = COLOR_GOLD_ACCENT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 56f, PAGE_WIDTH.toFloat(), 59f, goldLinePaint)

        // OMLY Brand Title in Header
        val brandPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = COLOR_GOLD_LIGHT
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        if (isArabic) {
            brandPaint.textAlign = Paint.Align.RIGHT
            subtitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("OMLY ACCOUNTING AND BOOKKEEPING", PAGE_WIDTH - MARGIN_X, 26f, brandPaint)
            canvas.drawText("مكتب أوملي للمحاسبة ومسك الدفاتر • تطبيق المستشار الذكي OMLY AI", PAGE_WIDTH - MARGIN_X, 42f, subtitlePaint)
        } else {
            brandPaint.textAlign = Paint.Align.LEFT
            subtitlePaint.textAlign = Paint.Align.LEFT
            canvas.drawText("OMLY ACCOUNTING AND BOOKKEEPING", MARGIN_X, 26f, brandPaint)
            canvas.drawText("OMLY Accounting & Bookkeeping Office • OMLY AI Advisory System", MARGIN_X, 42f, subtitlePaint)
        }

        // Meta Info Card (Height 52pt) under header
        val metaCardRect = RectF(MARGIN_X, 68f, PAGE_WIDTH - MARGIN_X, 120f)
        val metaCardBg = Paint().apply {
            color = COLOR_BG_CARD
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val metaCardBorder = Paint().apply {
            color = COLOR_BORDER_CARD
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(metaCardRect, 6f, 6f, metaCardBg)
        canvas.drawRoundRect(metaCardRect, 6f, 6f, metaCardBorder)

        // Gold Accent Bar on Meta Card (Left/Right)
        val accentBarX = if (isArabic) PAGE_WIDTH - MARGIN_X - 4f else MARGIN_X
        val accentRect = RectF(accentBarX, 68f, accentBarX + 4f, 120f)
        canvas.drawRoundRect(accentRect, 2f, 2f, goldLinePaint)

        // Meta Text Items
        val metaLabelPaint = TextPaint().apply {
            color = COLOR_SLATE_500
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val metaValuePaint = TextPaint().apply {
            color = COLOR_SLATE_900
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val dateStr = dateFormat.format(Date())
        val refCode = "OMLY-ADV-${(System.currentTimeMillis() % 900000 + 100000)}"

        if (isArabic) {
            metaLabelPaint.textAlign = Paint.Align.RIGHT
            metaValuePaint.textAlign = Paint.Align.RIGHT

            // Column 1 (Right)
            canvas.drawText("الرقم المرجعي:", PAGE_WIDTH - MARGIN_X - 16f, 85f, metaLabelPaint)
            canvas.drawText(refCode, PAGE_WIDTH - MARGIN_X - 75f, 85f, metaValuePaint)

            canvas.drawText("تاريخ الإصدار:", PAGE_WIDTH - MARGIN_X - 16f, 105f, metaLabelPaint)
            canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_X - 75f, 105f, metaValuePaint)

            // Column 2 (Left)
            canvas.drawText("النطاق التشريعي:", 230f, 85f, metaLabelPaint)
            canvas.drawText("الإمارات (FTA / MoF / IFRS)", 160f, 85f, metaValuePaint)

            canvas.drawText("التصنيف الفني:", 230f, 105f, metaLabelPaint)
            canvas.drawText("دليل استرشادي وتوضيحي", 160f, 105f, metaValuePaint)
        } else {
            metaLabelPaint.textAlign = Paint.Align.LEFT
            metaValuePaint.textAlign = Paint.Align.LEFT

            // Column 1 (Left)
            canvas.drawText("REFERENCE:", MARGIN_X + 16f, 85f, metaLabelPaint)
            canvas.drawText(refCode, MARGIN_X + 80f, 85f, metaValuePaint)

            canvas.drawText("DATE & TIME:", MARGIN_X + 16f, 105f, metaLabelPaint)
            canvas.drawText(dateStr, MARGIN_X + 80f, 105f, metaValuePaint)

            // Column 2 (Right)
            canvas.drawText("JURISDICTION:", 320f, 85f, metaLabelPaint)
            canvas.drawText("UAE (FTA / MoF / IFRS)", 400f, 85f, metaValuePaint)

            canvas.drawText("CLASSIFICATION:", 320f, 105f, metaLabelPaint)
            canvas.drawText("Advisory & Analytical Guide", 400f, 105f, metaValuePaint)
        }
    }

    private fun drawSubsequentHeader(canvas: Canvas, isArabic: Boolean) {
        // Thin Navy Top Bar
        val bannerPaint = Paint().apply {
            color = COLOR_NAVY_PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 34f, bannerPaint)

        // Gold Trim Line
        val goldLinePaint = Paint().apply {
            color = COLOR_GOLD_ACCENT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 34f, PAGE_WIDTH.toFloat(), 36f, goldLinePaint)

        val runningTitlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        if (isArabic) {
            runningTitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("OMLY ACCOUNTING AND BOOKKEEPING • دليل استشاري ضريبي ومحاسبي (تابع)", PAGE_WIDTH - MARGIN_X, 22f, runningTitlePaint)
        } else {
            runningTitlePaint.textAlign = Paint.Align.LEFT
            canvas.drawText("OMLY ACCOUNTING AND BOOKKEEPING • Advisory & Tax Memo (Cont.)", MARGIN_X, 22f, runningTitlePaint)
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int, isArabic: Boolean) {
        val yDivider = PAGE_HEIGHT - 32f

        // Footer Divider Line
        val linePaint = Paint().apply {
            color = COLOR_BORDER_CARD
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(MARGIN_X, yDivider, PAGE_WIDTH - MARGIN_X, yDivider, linePaint)

        val footerTextPaint = TextPaint().apply {
            color = COLOR_SLATE_500
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val pageNumText = if (isArabic) "صفحة $pageNumber من $totalPages" else "Page $pageNumber of $totalPages"
        val brandFooter = if (isArabic) "OMLY ACCOUNTING AND BOOKKEEPING • هاتف/واتساب: +971505795412" else "OMLY ACCOUNTING AND BOOKKEEPING • Phone/WhatsApp: +971505795412"

        if (isArabic) {
            footerTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(pageNumText, MARGIN_X, PAGE_HEIGHT - 16f, footerTextPaint)

            footerTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(brandFooter, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 16f, footerTextPaint)
        } else {
            footerTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(pageNumText, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 16f, footerTextPaint)

            footerTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(brandFooter, MARGIN_X, PAGE_HEIGHT - 16f, footerTextPaint)
        }
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, startY: Float, isArabic: Boolean) {
        val headerHeight = 24f
        val rect = RectF(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + headerHeight)

        // Section Background Capsule
        val bgPaint = Paint().apply {
            color = COLOR_BG_SECTION
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)

        // Navy & Gold Left/Right Accent Bar
        val accentWidth = 4f
        val accentX = if (isArabic) PAGE_WIDTH - MARGIN_X - accentWidth else MARGIN_X
        val accentRect = RectF(accentX, startY, accentX + accentWidth, startY + headerHeight)
        val accentPaint = Paint().apply {
            color = COLOR_NAVY_PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(accentRect, 2f, 2f, accentPaint)

        // Clean Cleaned Title
        val cleanTitle = title.replace("**", "").replace("#", "").trim()
        val textPaint = TextPaint().apply {
            color = COLOR_NAVY_PRIMARY
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        if (isArabic) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(cleanTitle, PAGE_WIDTH - MARGIN_X - 12f, startY + 16f, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(cleanTitle, MARGIN_X + 12f, startY + 16f, textPaint)
        }
    }

    private fun drawSubHeader(canvas: Canvas, text: String, startY: Float, isArabic: Boolean) {
        val layout = createStaticLayout(createSpannedText(text), USABLE_WIDTH.toInt(), isArabic, 10.5f, true)
        canvas.save()
        canvas.translate(MARGIN_X, startY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawParagraph(canvas: Canvas, text: String, startY: Float, isArabic: Boolean) {
        val layout = createStaticLayout(createSpannedText(text), USABLE_WIDTH.toInt(), isArabic, 10f, false)
        canvas.save()
        canvas.translate(MARGIN_X, startY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawBulletItem(canvas: Canvas, text: String, startY: Float, isArabic: Boolean) {
        val bulletRadius = 2.5f
        val bulletPaint = Paint().apply {
            color = COLOR_GOLD_ACCENT
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val bulletUsableWidth = USABLE_WIDTH.toInt() - 20
        val layout = createStaticLayout(createSpannedText(text), bulletUsableWidth, isArabic, 10f, false)

        if (isArabic) {
            // Draw bullet on the right
            canvas.drawCircle(PAGE_WIDTH - MARGIN_X - 6f, startY + 7f, bulletRadius, bulletPaint)
            canvas.save()
            canvas.translate(MARGIN_X, startY)
            layout.draw(canvas)
            canvas.restore()
        } else {
            // Draw bullet on the left
            canvas.drawCircle(MARGIN_X + 6f, startY + 7f, bulletRadius, bulletPaint)
            canvas.save()
            canvas.translate(MARGIN_X + 16f, startY)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawJournalBox(canvas: Canvas, entries: List<String>, startY: Float, isArabic: Boolean) {
        // Measure exact box height
        var entriesHeight = 0f
        val layouts = entries.map { entry ->
            val layout = createStaticLayout(createSpannedText(entry), (USABLE_WIDTH - 24).toInt(), isArabic, 9.5f, false)
            entriesHeight += layout.height + 4f
            layout
        }

        val boxHeight = entriesHeight + 20f
        val rect = RectF(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + boxHeight)

        // Box background
        val bgPaint = Paint().apply {
            color = COLOR_BG_JOURNAL
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = COLOR_BORDER_JOURNAL
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        // Navy Top Border Accent on Journal Box
        val topAccent = RectF(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + 3f)
        val topAccentPaint = Paint().apply {
            color = COLOR_NAVY_PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(topAccent, 2f, 2f, topAccentPaint)

        // Draw Entries inside box
        var currentEntryY = startY + 12f
        for (layout in layouts) {
            canvas.save()
            canvas.translate(MARGIN_X + 12f, currentEntryY)
            layout.draw(canvas)
            canvas.restore()
            currentEntryY += layout.height + 4f
        }
    }

    private fun drawContactCard(canvas: Canvas, text: String, startY: Float, isArabic: Boolean) {
        val layout = createStaticLayout(createSpannedText(text), (USABLE_WIDTH - 32).toInt(), isArabic, 9.5f, false)
        val cardHeight = layout.height.toFloat() + 38f
        val rect = RectF(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + cardHeight)

        // Gold soft background
        val bgPaint = Paint().apply {
            color = COLOR_GOLD_LIGHT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = COLOR_GOLD_ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        // Contact Header Label
        val labelPaint = TextPaint().apply {
            color = COLOR_GOLD_DARK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerText = if (isArabic) "للحصول على استشارة رسمية أو خدمات مسك الدفاتر يرجى التواصل مع مكتبنا OMLY ACCOUNTING AND BOOKKEEPING:" else "For Official Advisory & Bookkeeping Services, contact OMLY ACCOUNTING AND BOOKKEEPING:"
        if (isArabic) {
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(headerText, PAGE_WIDTH - MARGIN_X - 16f, startY + 16f, labelPaint)
        } else {
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(headerText, MARGIN_X + 16f, startY + 16f, labelPaint)
        }

        // Draw body text
        canvas.save()
        canvas.translate(MARGIN_X + 16f, startY + 26f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawDivider(canvas: Canvas, startY: Float) {
        val linePaint = Paint().apply {
            color = COLOR_BORDER_CARD
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(MARGIN_X, startY + 6f, PAGE_WIDTH - MARGIN_X, startY + 6f, linePaint)
    }

    // ==========================================
    // TEXT SPANNING & STATIC LAYOUT BUILDER
    // ==========================================

    private fun createSpannedText(rawText: String): CharSequence {
        val clean = rawText
            .replace(Regex("<[^>]*>"), "")
            .replace("###", "")
            .replace("##", "")
            .replace("#", "")
            .trim()

        val ssb = SpannableStringBuilder()
        val parts = clean.split("**")

        var isBold = false
        for (part in parts) {
            if (part.isEmpty()) {
                isBold = !isBold
                continue
            }

            val start = ssb.length
            ssb.append(part)
            val end = ssb.length

            if (isBold) {
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(COLOR_SLATE_900), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                ssb.setSpan(ForegroundColorSpan(COLOR_SLATE_700), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            isBold = !isBold
        }

        return ssb
    }

    private fun createStaticLayout(
        text: CharSequence,
        width: Int,
        isArabic: Boolean,
        textSizeSp: Float,
        isBold: Boolean
    ): StaticLayout {
        val textPaint = TextPaint().apply {
            color = COLOR_SLATE_900
            textSize = textSizeSp
            typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val alignment = if (isArabic) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val textDirection = if (isArabic) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxOf(10, width))
                .setAlignment(alignment)
                .setTextDirection(textDirection)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                maxOf(10, width),
                alignment,
                1.25f,
                0f,
                true
            )
        }
    }
}

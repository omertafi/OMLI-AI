package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AiBubbleBg
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CodeBlockText
import com.example.ui.theme.GeoBorder
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.PrimaryAccentBg
import com.example.ui.theme.QuoteBlockBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FormattedMarkdownText(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AiBubbleBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                val cleanedText = remember(markdownText) { cleanLatexAndMath(markdownText) }
                val blocks = parseMarkdownBlocks(cleanedText)
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is MarkdownBlock.Header -> {
                            RichLinkText(
                                text = block.text,
                                fontSize = if (block.level == 1) 16.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        is MarkdownBlock.BulletItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryAccent,
                                    modifier = Modifier
                                        .padding(top = 8.dp, end = 8.dp, start = 2.dp)
                                        .size(6.dp)
                                ) {}
                                RichLinkText(
                                    text = block.text,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                        is MarkdownBlock.NumberedItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PrimaryAccentBg,
                                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                                ) {
                                    Text(
                                        text = "${block.number}.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                RichLinkText(
                                    text = block.text,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        is MarkdownBlock.CodeBlock -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CodeBlockBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (block.language.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = null,
                                                tint = PrimaryAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = block.language.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = block.code,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = CodeBlockText
                                    )
                                }
                            }
                        }
                        is MarkdownBlock.Quote -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = QuoteBlockBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                RichLinkText(
                                    text = block.text,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is MarkdownBlock.Paragraph -> {
                            RichLinkText(
                                text = block.text,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                    if (index < blocks.size - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RichLinkText(
    text: String,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = TextPrimary,
    lineHeight: TextUnit = 22.sp,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = remember(text) { buildRichAnnotatedString(text) }
    val hasUrl = remember(annotatedString) {
        annotatedString.getStringAnnotations(tag = "URL", start = 0, end = annotatedString.length).isNotEmpty()
    }

    if (!hasUrl) {
        Text(
            text = annotatedString,
            modifier = modifier,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                lineHeight = lineHeight
            )
        )
    } else {
        ClickableText(
            text = annotatedString,
            modifier = modifier,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                lineHeight = lineHeight
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (e: Exception) {
                            // ignore or handle invalid uri
                        }
                    }
            }
        )
    }
}

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString().trim()))
                codeBuffer.clear()
                inCodeBlock = false
                codeLang = ""
            } else {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            continue
        }

        if (trimmed.isBlank()) continue

        when {
            trimmed.startsWith("# ") -> blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            trimmed.startsWith("## ") -> blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            trimmed.startsWith("### ") -> blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                val itemText = trimmed.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
                blocks.add(MarkdownBlock.BulletItem(itemText))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val dotIndex = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIndex)
                val itemText = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MarkdownBlock.NumberedItem(num, itemText))
            }
            trimmed.startsWith("> ") -> blocks.add(MarkdownBlock.Quote(trimmed.removePrefix("> ").trim()))
            else -> blocks.add(MarkdownBlock.Paragraph(trimmed))
        }
    }

    if (inCodeBlock && codeBuffer.isNotBlank()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString().trim()))
    }

    return if (blocks.isEmpty()) listOf(MarkdownBlock.Paragraph(text)) else blocks
}

private fun buildRichAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)|(https?://[^\s,;)\]]+)""")
        var currentIndex = 0

        fun appendWithBold(subText: String) {
            val parts = subText.split("**")
            for (i in parts.indices) {
                if (i % 2 == 1) { // inside **
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append(parts[i])
                    }
                } else {
                    append(parts[i])
                }
            }
        }

        regex.findAll(text).forEach { match ->
            val matchRange = match.range
            if (matchRange.first > currentIndex) {
                appendWithBold(text.substring(currentIndex, matchRange.first))
            }

            val linkText: String
            val url: String

            if (match.groups[1] != null && match.groups[2] != null) {
                linkText = match.groups[1]!!.value
                url = match.groups[2]!!.value
            } else {
                linkText = match.value
                url = match.value
            }

            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = PrimaryAccent,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(linkText)
            }
            pop()

            currentIndex = matchRange.last + 1
        }

        if (currentIndex < text.length) {
            appendWithBold(text.substring(currentIndex))
        }
    }
}

private fun cleanLatexAndMath(text: String): String {
    if (!text.contains("\\") && !text.contains("$$")) return text

    var result = text
    // Remove LaTeX block/inline equation markers $$ and $
    result = result.replace("$$", "")

    // Replace \frac{numerator}{denominator} with (numerator / denominator)
    val fracRegex = Regex("""\\frac\s*\{([^}]+)\}\s*\{([^}]+)\}""")
    while (fracRegex.containsMatchIn(result)) {
        result = fracRegex.replace(result) { match ->
            val num = match.groupValues[1]
            val den = match.groupValues[2]
            "($num ÷ $den)"
        }
    }

    // Replace \text{content} with content
    val textRegex = Regex("""\\text\s*\{([^}]+)\}""")
    while (textRegex.containsMatchIn(result)) {
        result = textRegex.replace(result) { match ->
            match.groupValues[1]
        }
    }

    // Replace common LaTeX symbols with clean readable characters
    result = result
        .replace("\\left(", "(")
        .replace("\\right)", ")")
        .replace("\\left[", "[")
        .replace("\\right]", "]")
        .replace("\\times", "×")
        .replace("\\cdot", "·")
        .replace("\\div", "÷")
        .replace("\\approx", "≈")
        .replace("\\le", "≤")
        .replace("\\ge", "≥")
        .replace("\\neq", "≠")
        .replace("\\%", "%")

    // Remove remaining stray backslashes before words or brackets
    result = result.replace(Regex("""\\([a-zA-Z]+|\{|\})""")) { match ->
        val group = match.groupValues[1]
        if (group == "{" || group == "}") "" else " " + group
    }

    // Clean up leftover standalone braces
    result = result.replace("{", "").replace("}", "")

    return result
}


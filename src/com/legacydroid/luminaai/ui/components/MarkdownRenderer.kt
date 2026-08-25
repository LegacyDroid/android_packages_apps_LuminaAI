/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Block model for the parsed markdown.
private sealed interface MarkdownBlock {
    object HorizontalRule : MarkdownBlock
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class UnorderedList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private data class ParsedInline(
    val annotatedString: AnnotatedString,
    val linkRanges: List<Pair<IntRange, String>>
)

// Inline regexes, tried in order.
private val INLINE_CODE_REGEX = Regex("""^`([^`]+)`""")
private val BOLD_ITALIC_REGEX = Regex("""^(?:\*\*\*(.+?)\*\*\*|___(.+?)___)""")
private val BOLD_REGEX = Regex("""^(?:\*\*(.+?)\*\*|__(.+?)__)""")
private val ITALIC_REGEX = Regex("""^(?:\*(.+?)\*|_(.+?)_)""")
private val STRIKETHROUGH_REGEX = Regex("""^~~(.+?)~~""")
private val LINK_REGEX = Regex("""^\[([^\]]+)\]\(([^)]+)\)""")
private val AUTOLINK_REGEX = Regex("""^https?://[^\s\])]+""")

// Block regexes, checked top to bottom.
private val HEADER_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val HORIZONTAL_RULE_REGEX = Regex("""^(?:---|\*\*\*|___)+$""")
private val BLOCKQUOTE_REGEX = Regex("""^>\s?(.*)$""")
private val UL_REGEX = Regex("""^[-*+]\s+(.*)$""")
private val OL_REGEX = Regex("""^\d+\.\s+(.*)$""")

private fun parseInlineMarkdown(
    text: String,
    linkColor: Color
): ParsedInline {
    val linkRanges = mutableListOf<Pair<IntRange, String>>()
    val annotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // escaped character
            if (i < text.length - 1 && text[i] == '\\' && text[i + 1] in "*_`~[]\\") {
                append(text[i + 1])
                i += 2
                continue
            }

            val remaining = text.substring(i)

            // bold italic
            val boldItalicMatch = BOLD_ITALIC_REGEX.find(remaining)
            if (boldItalicMatch != null) {
                val content = boldItalicMatch.groupValues[1].ifEmpty { boldItalicMatch.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(content)
                }
                i += boldItalicMatch.value.length
                continue
            }

            // bold
            val boldMatch = BOLD_REGEX.find(remaining)
            if (boldMatch != null) {
                val content = boldMatch.groupValues[1].ifEmpty { boldMatch.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
                i += boldMatch.value.length
                continue
            }

            // italic
            val italicMatch = ITALIC_REGEX.find(remaining)
            if (italicMatch != null) {
                val content = italicMatch.groupValues[1].ifEmpty { italicMatch.groupValues[2] }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
                i += italicMatch.value.length
                continue
            }

            // strikethrough
            val strikethroughMatch = STRIKETHROUGH_REGEX.find(remaining)
            if (strikethroughMatch != null) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(strikethroughMatch.groupValues[1])
                }
                i += strikethroughMatch.value.length
                continue
            }

            // inline code
            val codeMatch = INLINE_CODE_REGEX.find(remaining)
            if (codeMatch != null) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        background = Color.Gray.copy(alpha = 0.2f)
                    )
                ) {
                    append(codeMatch.groupValues[1])
                }
                i += codeMatch.value.length
                continue
            }

            // markdown link
            val linkMatch = LINK_REGEX.find(remaining)
            if (linkMatch != null) {
                val displayText = linkMatch.groupValues[1]
                val url = linkMatch.groupValues[2]
                val start = length
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(displayText)
                }
                pop()
                linkRanges.add(start until length to url)
                i += linkMatch.value.length
                continue
            }

            // bare url
            val autoLinkMatch = AUTOLINK_REGEX.find(remaining)
            if (autoLinkMatch != null) {
                val url = autoLinkMatch.value
                val start = length
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                linkRanges.add(start until length to url)
                i += autoLinkMatch.value.length
                continue
            }

            append(text[i])
            i++
        }
    }
    return ParsedInline(annotatedString, linkRanges)
}

private fun parseBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // horizontal rule
        if (HORIZONTAL_RULE_REGEX.matches(line.trim())) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // heading
        val headerMatch = HEADER_REGEX.matchEntire(line.trim())
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, content))
            i++
            continue
        }

        // fenced code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip the closing fence
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // blockquote
        val bqMatch = BLOCKQUOTE_REGEX.matchEntire(line.trim())
        if (bqMatch != null) {
            val quoteLines = mutableListOf(bqMatch.groupValues[1])
            i++
            while (i < lines.size) {
                val nextBq = BLOCKQUOTE_REGEX.matchEntire(lines[i].trim())
                if (nextBq != null) {
                    quoteLines.add(nextBq.groupValues[1])
                    i++
                } else break
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // unordered list
        val ulMatch = UL_REGEX.matchEntire(line.trim())
        if (ulMatch != null) {
            val items = mutableListOf(ulMatch.groupValues[1])
            i++
            while (i < lines.size) {
                val nextUl = UL_REGEX.matchEntire(lines[i].trim())
                if (nextUl != null) {
                    items.add(nextUl.groupValues[1])
                    i++
                } else break
            }
            blocks.add(MarkdownBlock.UnorderedList(items))
            continue
        }

        // ordered list
        val olMatch = OL_REGEX.matchEntire(line.trim())
        if (olMatch != null) {
            val items = mutableListOf(olMatch.groupValues[1])
            i++
            while (i < lines.size) {
                val nextOl = OL_REGEX.matchEntire(lines[i].trim())
                if (nextOl != null) {
                    items.add(nextOl.groupValues[1])
                    i++
                } else break
            }
            blocks.add(MarkdownBlock.OrderedList(items))
            continue
        }

        // blank lines
        if (line.isBlank()) {
            i++
            continue
        }

        // plain paragraph text
        val textLines = mutableListOf(line)
        i++
        while (i < lines.size && lines[i].isNotBlank()
            && !HEADER_REGEX.matches(lines[i].trim())
            && !HORIZONTAL_RULE_REGEX.matches(lines[i].trim())
            && !BLOCKQUOTE_REGEX.matches(lines[i].trim())
            && !UL_REGEX.matches(lines[i].trim())
            && !OL_REGEX.matches(lines[i].trim())
            && !lines[i].trimStart().startsWith("```")
        ) {
            textLines.add(lines[i])
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(textLines.joinToString("\n")))
    }

    return blocks
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    isUser: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(text) { parseBlocks(text) }
    val uriHandler = LocalUriHandler.current
    val linkColor = if (isUser) scheme.onPrimary else scheme.primary
    val defaultTextColor = if (isUser) scheme.onPrimary else scheme.onSurface

    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.HorizontalRule -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(scheme.onSurface.copy(alpha = 0.2f))
                    )
                }

                is MarkdownBlock.Header -> {
                    val parsed = remember(block.text, linkColor) {
                        parseInlineMarkdown(block.text, linkColor)
                    }
                    val fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 18.sp
                        3 -> 16.sp
                        4 -> 14.sp
                        5 -> 13.sp
                        else -> 12.sp
                    }
                    val fontWeight = when {
                        block.level <= 2 -> FontWeight.Bold
                        block.level <= 4 -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    }
                    ClickableText(
                        text = parsed.annotatedString,
                        style = style.copy(
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            lineHeight = (fontSize.value * 1.3f).sp,
                            color = defaultTextColor
                        ),
                        modifier = Modifier.padding(vertical = 2.dp),
                        onClick = { offset ->
                            parsed.linkRanges.find { offset in it.first }?.let { (_, url) ->
                                uriHandler.openUri(url)
                            }
                        }
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(
                                width = 0.5.dp,
                                color = scheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            if (block.language.isNotEmpty()) {
                                Text(
                                    text = block.language,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                color = Color(0xFFE0E0E0),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    val parsed = remember(block.text, linkColor) {
                        parseInlineMarkdown(block.text, linkColor)
                    }
                    val barColor = scheme.primary.copy(alpha = 0.6f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.4f))
                            .drawBehind {
                                drawLine(
                                    color = barColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        ClickableText(
                            text = parsed.annotatedString,
                            style = style.copy(
                                fontStyle = FontStyle.Italic,
                                color = scheme.onSurface.copy(alpha = 0.85f),
                                lineHeight = 20.sp
                            ),
                            onClick = { offset ->
                                parsed.linkRanges.find { offset in it.first }?.let { (_, url) ->
                                    uriHandler.openUri(url)
                                }
                            }
                        )
                    }
                }

                is MarkdownBlock.UnorderedList -> {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        block.items.forEach { item ->
                            val parsed = remember(item, linkColor) {
                                parseInlineMarkdown(item, linkColor)
                            }
                            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                Text(
                                    text = "\u2022",
                                    color = linkColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                ClickableText(
                                    text = parsed.annotatedString,
                                    style = style.copy(
                                        color = defaultTextColor,
                                        lineHeight = 20.sp
                                    ),
                                    onClick = { offset ->
                                        parsed.linkRanges.find { offset in it.first }?.let { (_, url) ->
                                            uriHandler.openUri(url)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.OrderedList -> {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        block.items.forEachIndexed { index, item ->
                            val parsed = remember(item, linkColor) {
                                parseInlineMarkdown(item, linkColor)
                            }
                            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                Text(
                                    text = "${index + 1}.",
                                    color = linkColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                ClickableText(
                                    text = parsed.annotatedString,
                                    style = style.copy(
                                        color = defaultTextColor,
                                        lineHeight = 20.sp
                                    ),
                                    onClick = { offset ->
                                        parsed.linkRanges.find { offset in it.first }?.let { (_, url) ->
                                            uriHandler.openUri(url)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    val parsed = remember(block.text, linkColor) {
                        parseInlineMarkdown(block.text, linkColor)
                    }
                    ClickableText(
                        text = parsed.annotatedString,
                        style = style.copy(
                            color = defaultTextColor,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.padding(vertical = 1.dp),
                        onClick = { offset ->
                            parsed.linkRanges.find { offset in it.first }?.let { (_, url) ->
                                uriHandler.openUri(url)
                            }
                        }
                    )
                }
            }
        }
    }
}

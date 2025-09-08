package com.dublikunt.dmclient.component

import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri

@Composable
fun MarkdownText(text: String) {
    val context = LocalContext.current
    val annotatedString = parseMarkdownToAnnotatedString(text)

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { tapOffsetPosition ->
                    val layoutResult = textLayoutResult ?: return@detectTapGestures
                    val position = layoutResult.getOffsetForPosition(tapOffsetPosition)
                    annotatedString
                        .getStringAnnotations(start = position, end = position)
                        .firstOrNull { it.tag == "URL" }
                        ?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            context.startActivity(intent)
                        }
                }
            },
        onTextLayout = { result ->
            textLayoutResult = result
        }
    )
}

@Composable
fun parseMarkdownToAnnotatedString(markdown: String): AnnotatedString {
    val builder = AnnotatedString.Builder()

    fun parse(text: String, startStyle: SpanStyle? = null) {
        var cursor = 0

        val patterns = listOf(
            Regex("""\*\*\*(.+?)\*\*\*""") to { match: MatchResult ->
                val content = match.groupValues[1]
                val style = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                parse(content, style)
            },
            Regex("""\*\*(.+?)\*\*""") to { match: MatchResult ->
                val content = match.groupValues[1]
                val style = SpanStyle(fontWeight = FontWeight.Bold)
                parse(content, style)
            },
            Regex("""\*(.+?)\*""") to { match: MatchResult ->
                val content = match.groupValues[1]
                val style = SpanStyle(fontStyle = FontStyle.Italic)
                parse(content, style)
            },
            Regex("""\[(.+?)]\((.+?)\)""") to { match: MatchResult ->
                val linkText = match.groupValues[1]
                val url = match.groupValues[2]
                val style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)
                val start = builder.length
                parse(linkText, style)
                builder.addStringAnnotation("URL", url, start, builder.length)
            },
            Regex("""`(.+?)`""") to { match: MatchResult ->
                val content = match.groupValues[1]
                val style = SpanStyle(
                    background = Color.LightGray,
                    fontFamily = FontFamily.Monospace
                )
                parse(content, style)
            }
        )

        while (cursor < text.length) {
            var matched = false
            for ((regex, handler) in patterns) {
                val result = regex.find(text, cursor)
                if (result != null && result.range.first == cursor) {
                    handler(result)
                    cursor = result.range.last + 1
                    matched = true
                    break
                }
            }
            if (!matched) {
                val ch = text[cursor]
                val start = builder.length
                builder.append(ch)
                startStyle?.let {
                    builder.addStyle(it, start, builder.length)
                }
                cursor++
            }
        }
    }

    parse(markdown)

    return builder.toAnnotatedString()
}

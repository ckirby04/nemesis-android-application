package io.github.ckirby04.nemesis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")

/** **bold** and *italic* spans; everything else is literal text. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(i, m.range.first))
        val (bold, italic) = m.destructured
        if (bold.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        i = m.range.last + 1
    }
    append(text.substring(i))
}

/**
 * Just enough Markdown for the advisor's reports: #/##/### headings, dash/star and numbered bullets,
 * --- rules, paragraphs, and inline bold/italic.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> Unit
                trimmed.startsWith("### ") -> Text(inlineMarkdown(trimmed.drop(4)), style = type.titleSmall)
                trimmed.startsWith("## ") ->
                    Text(inlineMarkdown(trimmed.drop(3)), style = type.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp))
                trimmed.startsWith("# ") -> Text(inlineMarkdown(trimmed.drop(2)), style = type.titleLarge)
                trimmed.matches(Regex("""-{3,}|\*{3,}""")) -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("""\d+\.\s.*""")) -> {
                    val indent = (line.length - trimmed.length).coerceAtMost(8) * 4
                    val marker = if (trimmed[0].isDigit()) trimmed.substringBefore(' ') else "•"
                    Row(Modifier.padding(start = indent.dp)) {
                        Text(marker, style = type.bodyMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(if (marker == "•") 14.dp else 22.dp))
                        Text(inlineMarkdown(trimmed.substringAfter(' ')), style = type.bodyMedium)
                    }
                }
                else -> Text(inlineMarkdown(trimmed), style = type.bodyMedium)
            }
        }
    }
}

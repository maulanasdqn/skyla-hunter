package dev.reforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark

/**
 * Lightweight Markdown renderer for the agent's answers — no external deps.
 * Supports: headings, bullet & numbered lists, bold/italic/inline-code,
 * fenced code blocks, and GitHub-style tables.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    Column(modifier) {
        var i = 0
        val codeBuf = StringBuilder()
        var inCode = false
        while (i < lines.size) {
            val line = lines[i]

            if (line.trimStart().startsWith("```")) {
                if (inCode) { CodeBlock(codeBuf.toString().trimEnd()); codeBuf.clear(); inCode = false }
                else inCode = true
                i++; continue
            }
            if (inCode) { codeBuf.append(line).append('\n'); i++; continue }

            // table: header row + separator row
            if (isTableRow(line) && i + 1 < lines.size && isTableSep(lines[i + 1])) {
                val header = splitRow(line)
                var j = i + 2
                val rows = mutableListOf<List<String>>()
                while (j < lines.size && isTableRow(lines[j]) && !isTableSep(lines[j])) {
                    rows.add(splitRow(lines[j])); j++
                }
                TableBlock(header, rows)
                i = j; continue
            }

            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("### ") -> Heading(line.removePrefix("### "), 16.sp)
                line.startsWith("## ") -> Heading(line.removePrefix("## "), 18.sp)
                line.startsWith("# ") -> Heading(line.removePrefix("# "), 20.sp)
                isBullet(line) -> ListItem("•", stripBullet(line), muted)
                isNumbered(line) -> {
                    val num = line.trimStart().substringBefore('.')
                    ListItem("$num.", line.trimStart().substringAfter(". "), muted)
                }
                else -> Text(
                    parseInline(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (muted) MutedDark else MaterialTheme.colorScheme.onSurface,
                )
            }
            i++
        }
        if (inCode && codeBuf.isNotEmpty()) CodeBlock(codeBuf.toString().trimEnd())
    }
}

// ---- tables ----

private fun isTableRow(l: String): Boolean = l.trim().startsWith("|") && l.contains('|')

private fun isTableSep(l: String): Boolean {
    val t = l.trim()
    return t.startsWith("|") && t.all { it == '|' || it == '-' || it == ':' || it == ' ' } && t.contains('-')
}

private fun splitRow(l: String): List<String> =
    l.trim().trim('|').split("|").map { it.trim() }

@Composable
private fun TableBlock(header: List<String>, rows: List<List<String>>) {
    val cols = header.size.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFF0C1016), RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        TableRow(header, cols, headerStyle = true)
        Spacer(Modifier.height(2.dp).fillMaxWidth().background(Color(0xFF232A36)))
        rows.forEachIndexed { idx, r ->
            TableRow(r, cols, headerStyle = false)
            if (idx < rows.size - 1) {
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(Color(0xFF1A202A)))
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, cols: Int, headerStyle: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 6.dp), verticalAlignment = Alignment.Top) {
        for (c in 0 until cols) {
            val txt = cells.getOrElse(c) { "" }
            Text(
                parseInline(txt),
                modifier = Modifier.weight(if (c == 0) 0.9f else 1.1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = if (headerStyle) FontWeight.SemiBold else FontWeight.Normal,
                color = if (headerStyle) MaterialTheme.colorScheme.onSurface else MutedDark,
            )
        }
    }
}

// ---- blocks ----

private fun isBullet(l: String): Boolean {
    val t = l.trimStart(); return t.startsWith("- ") || t.startsWith("* ")
}

private fun stripBullet(l: String): String = l.trimStart().drop(2)

private fun isNumbered(l: String): Boolean {
    val t = l.trimStart(); val dot = t.indexOf(". ")
    return dot in 1..3 && t.substring(0, dot).all { it.isDigit() }
}

@Composable
private fun Heading(text: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        parseInline(text), fontSize = size, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun ListItem(marker: String, text: String, muted: Boolean) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(marker, color = Accent, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            parseInline(text), style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MutedDark else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CodeBlock(code: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(Color(0xFF0C1016), RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()).padding(12.dp),
    ) {
        Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MutedDark)
    }
}

private val codeSpan = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Accent)

/** Inline parser: **bold**, *italic*, `code`. */
fun parseInline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end >= 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }; i = end + 2 }
                else { append(s[i]); i++ }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end >= 0) { withStyle(codeSpan) { append(s.substring(i + 1, end)) }; i = end + 1 }
                else { append(s[i]); i++ }
            }
            s[i] == '*' -> {
                val end = s.indexOf('*', i + 1)
                if (end >= 0) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }; i = end + 1 }
                else { append(s[i]); i++ }
            }
            else -> { append(s[i]); i++ }
        }
    }
}

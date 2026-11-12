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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import uniffi.skyla_core.MdBlock
import uniffi.skyla_core.parseMarkdown
import uniffi.skyla_core.Span as MdSpan
import uniffi.skyla_core.SpanStyle as MdStyle

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Spacer -> Spacer(Modifier.height(6.dp))
                is MdBlock.Heading -> Heading(block.spans, headingSize(block.level.toInt()))
                is MdBlock.Bullet -> ListItem("•", block.spans, muted)
                is MdBlock.Numbered -> ListItem("${block.number}.", block.spans, muted)
                is MdBlock.Code -> CodeBlock(block.code)
                is MdBlock.Table -> TableBlock(block.header, block.rows)
                is MdBlock.Paragraph -> Text(
                    spansToAnnotated(block.spans),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (muted) MutedDark else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun headingSize(level: Int): TextUnit = when (level) {
    1 -> 20.sp
    2 -> 18.sp
    else -> 16.sp
}

private val codeSpan = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Accent)

fun spansToAnnotated(spans: List<MdSpan>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        when (s.style) {
            MdStyle.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) }
            MdStyle.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.text) }
            MdStyle.CODE -> withStyle(codeSpan) { append(s.text) }
            MdStyle.PLAIN -> append(s.text)
        }
    }
}

@Composable
private fun TableBlock(header: List<List<MdSpan>>, rows: List<List<List<MdSpan>>>) {
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
private fun TableRow(cells: List<List<MdSpan>>, cols: Int, headerStyle: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 6.dp), verticalAlignment = Alignment.Top) {
        for (c in 0 until cols) {
            val cell = cells.getOrElse(c) { emptyList() }
            Text(
                spansToAnnotated(cell),
                modifier = Modifier.weight(if (c == 0) 0.9f else 1.1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = if (headerStyle) FontWeight.SemiBold else FontWeight.Normal,
                color = if (headerStyle) MaterialTheme.colorScheme.onSurface else MutedDark,
            )
        }
    }
}

@Composable
private fun Heading(spans: List<MdSpan>, size: TextUnit) {
    Text(
        spansToAnnotated(spans), fontSize = size, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun ListItem(marker: String, spans: List<MdSpan>, muted: Boolean) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(marker, color = Accent, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            spansToAnnotated(spans), style = MaterialTheme.typography.bodyMedium,
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

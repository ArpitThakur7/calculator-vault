package com.example.calcvault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor

/**
 * A fully working calculator that doubles as the lock screen.
 *
 * Open the vault: type your secret code and press "=".
 * Long-press "=":
 *   - no vault yet -> one-time setup
 *   - vault exists -> fingerprint unlock (if enabled)
 *
 * The code is checked by trying to decrypt the key file on a background thread,
 * so normal math stays instant and a wrong guess just computes as usual.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    configured: Boolean,
    biometricEnabled: Boolean,
    onCodeAttempt: (String) -> Unit,
    onSetup: () -> Unit,
    onBiometric: () -> Unit
) {
    var expr by remember { mutableStateOf("") }

    fun equals() {
        val typed = expr
        if (typed.isNotEmpty()) onCodeAttempt(typed)
        expr = Evaluator.eval(expr)
    }

    fun longEquals() {
        if (!configured) onSetup() else if (biometricEnabled) onBiometric()
    }

    fun onKey(label: String) {
        when (label) {
            "C" -> expr = ""
            "⌫" -> if (expr.isNotEmpty()) expr = expr.dropLast(1)
            "%" -> {
                val r = Evaluator.eval(expr).toDoubleOrNull()
                if (r != null) {
                    val d = r / 100.0
                    expr = if (d == floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()
                }
            }
            "±" -> expr = if (expr.startsWith("−")) expr.drop(1) else "−$expr"
            "÷" -> expr += "÷"
            "×" -> expr += "×"
            "−" -> expr += "−"
            "+" -> expr += "+"
            else -> expr += label
        }
    }

    val rows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .systemBarsPadding()
            .padding(14.dp)
    ) {
        Text(
            text = if (expr.isEmpty()) "0" else expr,
            color = Color.White,
            fontSize = 60.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        )

        for (row in rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (label in row) {
                    val isOrange = label in setOf("÷", "×", "−", "+", "=")
                    val isTop = label in setOf("C", "⌫", "%")
                    val bg = when {
                        isOrange -> Color(0xFFFF9500)
                        isTop -> Color(0xFFA5A5A5)
                        else -> Color(0xFF333333)
                    }
                    val fg = if (isTop) Color.Black else Color.White

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .then(
                                if (label == "=")
                                    Modifier.combinedClickable(
                                        onClick = { equals() },
                                        onLongClick = { longEquals() }
                                    )
                                else
                                    Modifier.combinedClickable(onClick = { onKey(label) })
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = fg, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

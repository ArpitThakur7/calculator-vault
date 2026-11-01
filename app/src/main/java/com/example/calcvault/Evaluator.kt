package com.example.calcvault

import kotlin.math.abs
import kotlin.math.floor

/**
 * Tiny, safe expression evaluator so the calculator actually works.
 * Supports + - * / ( ) and decimals, with the display symbols x and divide.
 */
object Evaluator {

    fun eval(input: String): String {
        return try {
            val s = input.replace('×', '*').replace('÷', '/').replace('−', '-')
            if (s.isBlank()) return ""
            val p = Parser(s)
            val v = p.parseExpr()
            p.skipWs()
            if (!p.atEnd()) return "Error"
            format(v)
        } catch (e: Exception) {
            "Error"
        }
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Error"
        return if (v == floor(v) && abs(v) < 1e15) {
            v.toLong().toString()
        } else {
            String.format("%.8f", v).trimEnd('0').trimEnd('.')
        }
    }

    private class Parser(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun skipWs() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skipWs(); return if (i < s.length) s[i] else null }

        fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += parseTerm() }
                    '-' -> { i++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= parseFactor() }
                    '/' -> { i++; v /= parseFactor() }
                    else -> return v
                }
            }
        }

        private fun parseFactor(): Double {
            when (peek()) {
                '-' -> { i++; return -parseFactor() }
                '+' -> { i++; return parseFactor() }
                '(' -> {
                    i++
                    val v = parseExpr()
                    if (peek() == ')') i++ else throw RuntimeException("paren")
                    return v
                }
                else -> return parseNumber()
            }
        }

        private fun parseNumber(): Double {
            skipWs()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i == start) throw RuntimeException("number")
            return s.substring(start, i).toDouble()
        }
    }
}

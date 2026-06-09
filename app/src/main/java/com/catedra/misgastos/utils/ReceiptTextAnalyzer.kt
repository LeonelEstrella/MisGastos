package com.catedra.misgastos.utils

object ReceiptTextAnalyzer {

    fun analyze(text: String): ExpenseSuggestion {
        val cleanText = text
            .replace("\r", "\n")
            .replace("\t", " ")
            .trim()

        val lines = cleanText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val amount = extractAmount(lines)
        val description = extractDescription(lines)
        val category = inferCategory(cleanText)

        return ExpenseSuggestion(
            amount = amount,
            category = category,
            description = description
        )
    }

    private fun extractAmount(lines: List<String>): Double? {
        val priorityKeywords = listOf(
            "total",
            "importe total",
            "monto total",
            "a pagar",
            "pago",
            "subtotal"
        )

        val priorityAmounts = lines
            .filter { line ->
                priorityKeywords.any { keyword ->
                    line.lowercase().contains(keyword)
                }
            }
            .flatMap { line -> extractAmountsFromLine(line) }

        if (priorityAmounts.isNotEmpty()) {
            return priorityAmounts.maxOrNull()
        }

        val allAmounts = lines.flatMap { line ->
            extractAmountsFromLine(line)
        }

        return allAmounts.maxOrNull()
    }

    private fun extractAmountsFromLine(line: String): List<Double> {
        /*
            Soporta cosas como:
            $ 8.500,50
            8500.50
            8,500.50
            8500
        */
        val regex = Regex("""\$?\s*\d{1,3}(?:[.,]\d{3})(?:[.,]\d{2})?|\$?\s\d+(?:[.,]\d{2})?""")

        return regex.findAll(line)
            .mapNotNull { match ->
                normalizeAmount(match.value)
            }
            .filter { it > 0 }
            .toList()
    }

    private fun normalizeAmount(rawValue: String): Double? {
        var value = rawValue
            .replace("$", "")
            .replace("ARS", "", ignoreCase = true)
            .replace("S/", "", ignoreCase = true)
            .trim()

        value = value.replace(" ", "")

        if (value.isBlank()) return null

        val hasComma = value.contains(",")
        val hasDot = value.contains(".")

        value = when {
            hasComma && hasDot -> {
                /*
                    Si tiene ambos, asumimos que el último separador es decimal.
                    Ej:
                    8.500,50 -> 8500.50
                    8,500.50 -> 8500.50
                */
                val lastComma = value.lastIndexOf(",")
                val lastDot = value.lastIndexOf(".")

                if (lastComma > lastDot) {
                    value.replace(".", "").replace(",", ".")
                } else {
                    value.replace(",", "")
                }
            }

            hasComma -> {
                val digitsAfterComma = value.substringAfterLast(",").length

                if (digitsAfterComma == 2) {
                    value.replace(".", "").replace(",", ".")
                } else {
                    value.replace(",", "")
                }
            }

            hasDot -> {
                val digitsAfterDot = value.substringAfterLast(".").length

                if (digitsAfterDot == 2) {
                    value.replace(",", "")
                } else {
                    value.replace(".", "")
                }
            }

            else -> value
        }

        return value.toDoubleOrNull()
    }

    private fun extractDescription(lines: List<String>): String? {
        if (lines.isEmpty()) return null

        val ignoredWords = listOf(
            "total",
            "subtotal",
            "iva",
            "cuit",
            "dni",
            "fecha",
            "hora",
            "factura",
            "ticket",
            "importe",
            "consumidor final",
            "responsable inscripto"
        )

        val candidate = lines.firstOrNull { line ->
            val lower = line.lowercase()

            line.length in 3..40 &&
                    ignoredWords.none { lower.contains(it) } &&
                    !line.any { it.isDigit() }
        }

        return candidate ?: lines.firstOrNull()?.take(40)
    }

    private fun inferCategory(text: String): String {
        val lower = text.lowercase()

        return when {
            containsAny(lower, "supermercado", "mercado", "carrefour", "dia", "coto", "jumbo", "verduleria", "almacen") ->
                "Comida"

            containsAny(lower, "farmacia", "medicamento", "salud", "doctor", "clinica") ->
                "Salud"

            containsAny(lower, "uber", "cabify", "sube", "tren", "subte", "colectivo", "ypf", "shell", "axion", "nafta") ->
                "Transporte"

            containsAny(lower, "netflix", "spotify", "cine", "teatro", "juego", "steam") ->
                "Entretenimiento"

            containsAny(lower, "ropa", "camiseta", "zapatilla", "remera", "pantalon", "indumentaria") ->
                "Ropa"

            else -> "Otros"
        }
    }

    private fun containsAny(text: String, vararg words: String): Boolean {
        return words.any { word -> text.contains(word) }
    }
}
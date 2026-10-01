package com.budgeting.me.util

import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import com.budgeting.me.util.formatRpInput

/**
 * Live Rupiah formatter: "20000" -> "20.000" as the user types.
 * Only digits are kept (amounts are whole Rupiah); cursor stays at the end.
 *
 * Anti-"5000.000" (terbukti di Redmi Note 13/Gboard): tulis balik via
 * [EditText.setText] penuh, BUKAN Editable.replace — replace mempertahankan
 * composing-span IME sehingga Gboard menimpa balik buffer stale-nya.
 * Selama IME masih composing, watcher diam (return awal).
 */
class RupiahTextWatcher(private val et: EditText) : TextWatcher {

    init {
        // inputType="numberDecimal" memasang DigitsKeyListener yang hanya mengizinkan
        // SATU titik desimal. Titik ribuan kedua ("1.000.000") akan dimakan saat
        // replace -> teks lebih pendek dari perkiraan -> setSelection crash (FC).
        // Lepas filter itu; sanitasi input tetap dijamin digitsOnly() di bawah.
        // Keyboard tetap numerik karena inputType tidak diubah.
        val kept: List<InputFilter> =
            et.filters.filterNot { it is DigitsKeyListener }
        if (kept.size != et.filters.size) {
            et.filters = kept.toTypedArray()
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        val editable = s ?: return
        // 1. IME (Gboard) masih composing -> jangan lawan, tunggu selesai.
        if (hasComposingSpan(editable)) return
        val raw = editable.toString()
        // 2. Bersihkan: hanya digit, buang nol depan tapi sisakan satu.
        val allDigits = raw.filter { it in '0'..'9' }
        if (allDigits.isEmpty()) {
            if (raw.isNotEmpty()) setFormatted("")
            return
        }
        val digits = allDigits.trimStart('0').ifEmpty { "0" }
        // 3. Konversi aman ke Long (anti overflow, anti koma).
        val value = digits.toLongOrNull() ?: return
        val formatted = formatRpInput(value)
        // Sudah rapi -> jangan sentuh teks (kursor otomatis aman).
        if (formatted == raw) return
        // 4. LEPAS watcher, tulis penuh via setText (reset composing IME),
        //    lalu pasang lagi dan KUNCI kursor di ujung.
        setFormatted(formatted)
    }

    private fun setFormatted(formatted: String) {
        et.removeTextChangedListener(this)
        et.setText(formatted)
        // Kursor mengikuti panjang TEKS AKTUAL (bukan asumsi), anti FC
        // IndexOutOfBounds bila ada filter lain yang memangkas teks.
        val cursorPos = minOf(formatted.length, et.text.length)
        try {
            et.setSelection(cursorPos)
        } catch (_: Exception) {
        }
        et.addTextChangedListener(this)
    }

    private fun hasComposingSpan(e: Editable): Boolean {
        for (span in e.getSpans(0, e.length, Any::class.java)) {
            if (e.getSpanFlags(span) and Spanned.SPAN_COMPOSING == Spanned.SPAN_COMPOSING) {
                return true
            }
        }
        return false
    }

    companion object {

        /** Strip everything except digits, e.g. "Rp 20.000" -> "20000". */
        fun digitsOnly(s: String?): String {
            return s?.replace("[^0-9]".toRegex(), "") ?: ""
        }

        /** Parse a possibly-formatted amount string safely. */
        fun parseAmount(s: String?): Double {
            val d = digitsOnly(s).replaceFirst("^0+(?!$)".toRegex(), "")
            if (d.isEmpty()) return 0.0
            return d.toLongOrNull()?.toDouble() ?: 0.0
        }
    }
}

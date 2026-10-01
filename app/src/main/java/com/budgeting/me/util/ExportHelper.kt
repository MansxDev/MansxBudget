package com.budgeting.me.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.budgeting.me.model.Transaction
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV export + JSON backup/restore into Documents (MediaStore on API 29+, direct file below). */
object ExportHelper {

    @Throws(Exception::class)
    fun exportCsv(context: Context, data: List<Transaction>): String {
        if (data.isEmpty()) throw Exception("empty")
        val sb = StringBuilder("id,title,amount,type,category,date\n")
        for (t in data) {
            sb.append(t.id).append(',')
                .append(csv(t.title)).append(',')
                .append(t.amount).append(',')
                .append(csv(t.type)).append(',')
                .append(csv(t.category)).append(',')
                .append(csv(t.date)).append('\n')
        }
        return writeText(context, fileName("budgeting", "csv"), "text/csv", sb.toString())
    }

    @Throws(Exception::class)
    fun exportJson(context: Context, json: String): String {
        return writeText(context, fileName("budgeting-backup", "json"), "application/json", json)
    }

    @Throws(Exception::class)
    fun readUri(context: Context, uri: Uri): String {
        val cr: ContentResolver = context.contentResolver
        val sb = StringBuilder()
        val input = cr.openInputStream(uri) ?: throw Exception("cannot open file")
        val r = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var line: String?
        while (r.readLine().also { line = it } != null) sb.append(line).append('\n')
        r.close()
        return sb.toString()
    }

    private fun fileName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$ts.$ext"
    }

    private fun csv(s: String?): String {
        if (s == null) return ""
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }

    @Throws(Exception::class)
    private fun writeText(context: Context, name: String, mime: String, text: String): String {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val v = ContentValues()
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            val uri = context.contentResolver
                .insert(MediaStore.Files.getContentUri("external"), v)
                ?: throw Exception("media store unavailable")
            val out: OutputStream = context.contentResolver.openOutputStream(uri)
                ?: throw Exception("cannot write")
            out.write(bytes)
            out.close()
            return "Documents/$name"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!dir.exists() && !dir.mkdirs()) throw Exception("cannot create dir")
            val f = File(dir, name)
            val out = FileOutputStream(f)
            out.write(bytes)
            out.close()
            return f.absolutePath
        }
    }
}

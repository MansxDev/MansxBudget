package com.budgeting.me.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whitelist sumber bank/e-wallet: bawaan (hard-coded) + kustom (auto-learn).
 *
 * Package vendor bisa berubah kapan saja; kalau user report tidak kedeteksi,
 * verify entry di [BUILTIN] lalu tambah varian bila perlu. Untuk package yang
 * benar-benar baru, user bisa daftarkan sendiri lewat auto-learn (tanpa update app).
 */
object BankWhitelist {

    /** package aplikasi -> nama tampilan. */
    val BUILTIN: Map<String, String> = mapOf(
        // Bank konvensional
        "com.bca" to "BCA",
        "com.bankmandiri.mandirionline" to "Mandiri",
        "id.co.bankmandiri.mandirionline" to "Mandiri", // varian, verify kalau tidak kedeteksi
        "src.com.bni" to "BNI",
        "id.co.bni.mobile" to "BNI", // varian, verify kalau tidak kedeteksi
        "id.co.bri.brimo" to "BRI",
        "id.co.btn.mobilebanking" to "BTN",
        "com.cimbniaga.mobile" to "CIMB Niaga",
        "id.co.cimbniaga.mobile" to "CIMB Niaga", // varian, verify kalau tidak kedeteksi
        "net.permatabank.mobile" to "Permata",
        "com.danamon.danamonku" to "Danamon",
        "com.maybank2u.android" to "Maybank",
        "com.paninbank.paninmobile" to "Panin",
        "com.ocbc.id" to "OCBC",
        "id.co.ocbc.mobile" to "OCBC", // varian, verify kalau tidak kedeteksi
        "com.btpn.dmobile.jenius" to "Jenius",
        "com.jenius.android" to "Jenius", // varian, verify kalau tidak kedeteksi
        "com.jago.android" to "Jago",
        "id.co.bankjago" to "Jago", // varian, verify kalau tidak kedeteksi
        "com.bcadigital.blu" to "Blu BCA Digital",
        "id.co.bankblu" to "Blu", // varian, verify kalau tidak kedeteksi
        "id.co.bankseabank" to "SeaBank",
        "com.seabank.id" to "SeaBank", // varian, verify kalau tidak kedeteksi
        "com.neobank.android" to "Neo Commerce",
        "id.co.bankneocommerce" to "Neo Commerce", // varian, verify kalau tidak kedeteksi
        "com.allobank.android" to "Allo Bank",
        "com.superbank.android" to "Superbank",
        "com.banksaqu.android" to "Bank Saqu",
        "com.linebank.android" to "LINE Bank",
        "id.co.bankraya" to "Bank Raya",
        "id.co.bankmega.mobilebanking" to "Bank Mega",
        "com.bankaceh.mobile" to "Bank Aceh",
        // E-wallet & dompet digital
        "com.gojek.gopay" to "GoPay",
        "com.gojek.gopay.consumer" to "GoPay", // varian, verify kalau tidak kedeteksi
        "ovo.id" to "OVO",
        "id.dana" to "DANA",
        "id.dana.consumer" to "DANA", // varian, verify kalau tidak kedeteksi
        "com.shopee.id" to "ShopeePay",
        "com.telkomsel.linkaja" to "LinkAja",
        "com.linkaja.wallet" to "LinkAja", // varian, verify kalau tidak kedeteksi
        "com.indosatooredoo.isaku" to "iSaku",
        "id.flip.android" to "Flip",
        "com.flip.android" to "Flip" // varian, verify kalau tidak kedeteksi
    )

    private const val PREFS = "bank_whitelist_prefs"
    private const val KEY_CUSTOM = "custom_sources"
    private const val KEY_DISMISSED = "bank_learn_dismissed"
    private const val DISMISS_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    fun builtinCount(): Int = BUILTIN.size

    /** Custom whitelist user (package -> nama), urutan pendaftaran. */
    fun getCustom(ctx: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM, "[]").orEmpty()
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("pkg", "")
                val name = o.optString("name", "")
                if (pkg.isNotEmpty() && name.isNotEmpty()) out[pkg] = name
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun customCount(ctx: Context): Int = getCustom(ctx).size

    /** Nama bank untuk package (bawaan dulu, lalu kustom). Null kalau tidak dikenal. */
    fun findName(ctx: Context, pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return BUILTIN[pkg] ?: getCustom(ctx)[pkg]
    }

    fun isKnown(ctx: Context, pkg: String?): Boolean = findName(ctx, pkg) != null

    /**
     * Judul notif yang PERSIS sama dengan nama bank -> anggap sumber dikenal.
     * Jalur ini untuk simulasi `adb shell cmd notification post` (package-nya
     * com.android.shell). Sengaja sempit (exact match), bukan chat yang
     * kebetulan menyebut nama bank.
     */
    fun findNameByTitle(title: String?): String? {
        val t = title?.trim()?.lowercase().orEmpty()
        if (t.isEmpty()) return null
        val all = BUILTIN.values.toSet()
        return all.firstOrNull { it.lowercase() == t }
    }

    fun addCustom(ctx: Context, pkg: String, name: String) {
        if (pkg.isBlank() || name.isBlank()) return
        val map = LinkedHashMap(getCustom(ctx))
        map[pkg] = name.trim()
        saveCustom(ctx, map)
    }

    fun removeCustom(ctx: Context, pkg: String) {
        val map = LinkedHashMap(getCustom(ctx))
        if (map.remove(pkg) == null) return
        saveCustom(ctx, map)
    }

    private fun saveCustom(ctx: Context, map: Map<String, String>) {
        try {
            val arr = JSONArray()
            for ((pkg, name) in map) {
                arr.put(JSONObject().put("pkg", pkg).put("name", name))
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM, arr.toString()).apply()
        } catch (e: Exception) {
        }
    }

    /** Boleh tawarkan learn-sheet? Tidak kalau user dismiss package ini < 7 hari. */
    fun shouldOfferLearn(ctx: Context, pkg: String): Boolean {
        return try {
            val ts = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DISMISSED, "{}").orEmpty()
            val last = JSONObject(ts).optLong(pkg, 0L)
            System.currentTimeMillis() - last > DISMISS_WINDOW_MS
        } catch (e: Exception) {
            true
        }
    }

    fun markDismissed(ctx: Context, pkg: String) {
        try {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val obj = try {
                JSONObject(p.getString(KEY_DISMISSED, "{}").orEmpty())
            } catch (e: Exception) {
                JSONObject()
            }
            obj.put(pkg, System.currentTimeMillis())
            p.edit().putString(KEY_DISMISSED, obj.toString()).apply()
        } catch (e: Exception) {
        }
    }
}
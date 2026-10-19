package com.budgeting.me.util

/**
 * Map merchant -> kategori OTOMATIS (auto-kategorisasi notifikasi).
 *
 * WAJIB hanya memakai nama kategori yang sudah ada di DB
 * (TransactionSheet.DEFAULT_CATEGORIES): "Makanan", "Transportasi",
 * "Gaji", "Belanja", "Tagihan", "Lainnya". Merchant tak dikenal ->
 * null (form memakai kategori default), JANGAN bikin kategori baru.
 *
 * Pencocokan:
 * - key <= 4 huruf pakai word-boundary, supaya "tri" tidak match
 *   di dalam "listrik" dan "kai" tidak match di dalam "santai".
 * - key >= 5 huruf / mengandung spasi cukup pakai contains.
 * - Pencocokan text -> merchant memakai LONGEST match, supaya
 *   "GrabFood" menang atas "Grab".
 */
object CategoryMatcher {

    /** Satu entri merchant: key pencocokan + nama tampilan + kategori existing. */
    private class Rule(val key: String, val display: String, val category: String) {

        /** Regex word-boundary untuk key pendek, null kalau contains sudah cukup. */
        private val wordBound: Regex? =
            if (key.length >= 5 || key.contains(' ')) null
            else Regex("\\b" + Regex.escape(key) + "\\b")

        fun matches(haystack: String): Boolean {
            val re = wordBound
            return if (re == null) haystack.contains(key) else re.containsMatchIn(haystack)
        }

        fun isLongerThan(other: Rule?): Boolean {
            return other == null || key.length > other.key.length
        }
    }

    private fun rule(key: String, display: String, category: String): Rule =
        Rule(key, display, category)

    private val RULES: List<Rule> = listOf(
        // ---------- Makanan: food delivery ----------
        rule("gofood", "GoFood", "Makanan"),
        rule("grabfood", "GrabFood", "Makanan"),
        rule("shopeefood", "ShopeeFood", "Makanan"),
        rule("mcdelivery", "McDelivery", "Makanan"),
        rule("kebuk", "Kebuk", "Makanan"),

        // ---------- Makanan: kopi / restoran cepat ----------
        rule("kopi kenangan", "Kopi Kenangan", "Makanan"),
        rule("janji jiwa", "Janji Jiwa", "Makanan"),
        rule("starbucks", "Starbucks", "Makanan"),
        rule("fore", "Fore", "Makanan"),
        rule("tomoro", "Tomoro", "Makanan"),
        rule("mcdonald", "McDonald's", "Makanan"),
        rule("mcd", "McD", "Makanan"),
        rule("kfc", "KFC", "Makanan"),
        rule("burger king", "Burger King", "Makanan"),
        rule("domino", "Domino's", "Makanan"),
        rule("pizza hut", "Pizza Hut", "Makanan"),
        rule("richeese", "Richeese", "Makanan"),
        rule("hokben", "HokBen", "Makanan"),
        rule("solaria", "Solaria", "Makanan"),
        rule("chatime", "Chatime", "Makanan"),
        rule("mixue", "Mixue", "Makanan"),
        rule("gacoan", "Gacoan", "Makanan"),
        rule("bakmi gm", "Bakmi GM", "Makanan"),

        // ---------- Transportasi ----------
        rule("gojek", "Gojek", "Transportasi"),
        rule("grab", "Grab", "Transportasi"),
        rule("maxim", "Maxim", "Transportasi"),
        rule("indrive", "inDrive", "Transportasi"),
        rule("blue bird", "Blue Bird", "Transportasi"),
        rule("transjakarta", "TransJakarta", "Transportasi"),
        rule("kai", "KAI", "Transportasi"),
        rule("mrt jakarta", "MRT Jakarta", "Transportasi"),

        // ---------- Belanja: minimarket / supermarket ----------
        rule("alfamart", "Alfamart", "Belanja"),
        rule("indomaret", "Indomaret", "Belanja"),
        rule("super indo", "Super Indo", "Belanja"),
        rule("hero", "Hero", "Belanja"),
        rule("giant", "Giant", "Belanja"),
        rule("hypermart", "Hypermart", "Belanja"),
        rule("transmart", "Transmart", "Belanja"),
        rule("lotte", "Lotte", "Belanja"),
        rule("ranch market", "Ranch Market", "Belanja"),
        rule("farmers market", "Farmers Market", "Belanja"),
        rule("carrefour", "Carrefour", "Belanja"),

        // ---------- Belanja: marketplace ----------
        rule("tokopedia", "Tokopedia", "Belanja"),
        rule("shopee", "Shopee", "Belanja"),
        rule("lazada", "Lazada", "Belanja"),
        rule("blibli", "Blibli", "Belanja"),
        rule("bukalapak", "Bukalapak", "Belanja"),

        // ---------- Tagihan: listrik / air / internet / telpon ----------
        rule("pln", "PLN", "Tagihan"),
        rule("listrik", "Listrik", "Tagihan"),
        rule("pdam", "PDAM", "Tagihan"),
        rule("telkom", "Telkom", "Tagihan"),
        rule("indihome", "IndiHome", "Tagihan"),
        rule("first media", "First Media", "Tagihan"),
        rule("myrepertory", "MyRepublic", "Tagihan"),

        // ---------- Tagihan: pulsa / data ----------
        rule("telkomsel", "Telkomsel", "Tagihan"),
        rule("simpati", "SimPATI", "Tagihan"),
        rule("indosat", "Indosat", "Tagihan"),
        rule("xl", "XL", "Tagihan"),
        rule("tri", "Tri", "Tagihan"),
        rule("smartfren", "Smartfren", "Tagihan"),

        // ---------- Tagihan: BPJS ----------
        rule("bpjs", "BPJS", "Tagihan"),

        // ---------- Lainnya: hiburan / streaming ----------
        rule("netflix", "Netflix", "Lainnya"),
        rule("spotify", "Spotify", "Lainnya"),
        rule("youtube premium", "YouTube Premium", "Lainnya"),
        rule("disney", "Disney+", "Lainnya"),
        rule("viu", "Viu", "Lainnya"),
        rule("vidio", "Vidio", "Lainnya"),

        // ---------- Lainnya: investasi ----------
        rule("bibit", "Bibit", "Lainnya"),
        rule("bareksa", "Bareksa", "Lainnya"),
        rule("pintu", "Pintu", "Lainnya"),
        rule("tokocrypto", "Tokocrypto", "Lainnya"),
        rule("indodax", "Indodax", "Lainnya"),

        // ---------- Lainnya: kesehatan ----------
        rule("apotek", "Apotek", "Lainnya"),
        rule("k24", "K24", "Lainnya"),
        rule("kimia farma", "Kimia Farma", "Lainnya"),
        rule("guardian", "Guardian", "Lainnya"),
        rule("halodoc", "Halodoc", "Lainnya"),

        // ---------- Lainnya: paylater ----------
        rule("kredivo", "Kredivo", "Lainnya"),
        rule("akulaku", "Akulaku", "Lainnya"),
        rule("spaylater", "SPayLater", "Lainnya"),
        rule("home credit", "Home Credit", "Lainnya")
    )

    /** Cache hasil compile regex ada di dalam [Rule] (dibuat sekali saat init). */

    /** Kategori existing untuk merchant (case-insensitive, contains aman). Null kalau asing. */
    fun matchCategory(merchant: String?): String? {
        val m = merchant?.trim()?.lowercase().orEmpty()
        if (m.isEmpty()) return null
        // 1) Cocok persis dengan nama merchant yang dikenal.
        for (r in RULES) {
            if (m == r.key || m == r.display.lowercase()) return r.category
        }
        // 2) Cocok longest-substring (mis. merchant "Tokopedia Official" -> Belanja).
        var best: Rule? = null
        for (r in RULES) {
            if (r.matches(m) && r.isLongerThan(best)) best = r
        }
        return best?.category
    }

    /**
     * Cari nama merchant yang dikenal di dalam teks notif (longest match).
     * Ini jalur utama untuk ekstraksi merchant + suggested category.
     */
    fun findMerchant(text: String): String? {
        val lower = text.lowercase()
        var best: Rule? = null
        for (r in RULES) {
            if (r.matches(lower) && r.isLongerThan(best)) best = r
        }
        return best?.display
    }

    /** Nama merchant yang terdeteksi di teks (alias dari [findMerchant]). */
    fun detectMerchant(text: String): String? = findMerchant(text)

    /** Kategori existing yang disarankan untuk teks notif ini (merchant based). */
    fun suggestCategory(text: String): String? {
        val m = findMerchant(text) ?: return null
        return matchCategory(m)
    }

    /** Daftar nama merchant yang dikenal (untuk scoring & debug). */
    fun getKnownMerchants(): List<String> = RULES.map { it.display }

    /** Jumlah entri merchant dalam map (untuk ringkasan rilis). */
    fun ruleCount(): Int = RULES.size
}
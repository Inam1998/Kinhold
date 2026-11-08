package com.inamibrahim.kinhold.model

/** A country with its phone code, for the country picker next to a phone number. [dial] is written with the plus, such as "+92". */
data class Country(val iso: String, val name: String, val dial: String) {
    /** The flag as an emoji built from the two-letter country code. */
    val flag: String get() = iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/**
 * Phone numbers are kept as one text such as "+92 3001234567": the country code, a space, and the national number without its
 * leading zero. That form is what the call, text and WhatsApp buttons need, so they work without the person adding anything.
 * Numbers saved earlier without a code are left as they are until the person edits them. Pure Kotlin.
 */
object Countries {
    val all: List<Country> = listOf(
        Country("PK", "Pakistan", "+92"), Country("IN", "India", "+91"), Country("BD", "Bangladesh", "+880"), Country("AF", "Afghanistan", "+93"),
        Country("LK", "Sri Lanka", "+94"), Country("NP", "Nepal", "+977"), Country("MV", "Maldives", "+960"),
        Country("AE", "United Arab Emirates", "+971"), Country("SA", "Saudi Arabia", "+966"), Country("QA", "Qatar", "+974"),
        Country("KW", "Kuwait", "+965"), Country("OM", "Oman", "+968"), Country("BH", "Bahrain", "+973"), Country("YE", "Yemen", "+967"),
        Country("IR", "Iran", "+98"), Country("IQ", "Iraq", "+964"), Country("JO", "Jordan", "+962"), Country("LB", "Lebanon", "+961"),
        Country("SY", "Syria", "+963"), Country("TR", "Turkey", "+90"), Country("EG", "Egypt", "+20"), Country("AZ", "Azerbaijan", "+994"),
        Country("UZ", "Uzbekistan", "+998"),
        Country("GB", "United Kingdom", "+44"), Country("IE", "Ireland", "+353"), Country("US", "United States", "+1"), Country("CA", "Canada", "+1"),
        Country("AU", "Australia", "+61"), Country("NZ", "New Zealand", "+64"),
        Country("DE", "Germany", "+49"), Country("FR", "France", "+33"), Country("IT", "Italy", "+39"), Country("ES", "Spain", "+34"),
        Country("PT", "Portugal", "+351"), Country("NL", "Netherlands", "+31"), Country("BE", "Belgium", "+32"), Country("CH", "Switzerland", "+41"),
        Country("AT", "Austria", "+43"), Country("SE", "Sweden", "+46"), Country("NO", "Norway", "+47"), Country("DK", "Denmark", "+45"),
        Country("FI", "Finland", "+358"), Country("PL", "Poland", "+48"), Country("GR", "Greece", "+30"), Country("RU", "Russia", "+7"),
        Country("UA", "Ukraine", "+380"),
        Country("CN", "China", "+86"), Country("HK", "Hong Kong", "+852"), Country("JP", "Japan", "+81"), Country("KR", "South Korea", "+82"),
        Country("MY", "Malaysia", "+60"), Country("SG", "Singapore", "+65"), Country("ID", "Indonesia", "+62"), Country("TH", "Thailand", "+66"),
        Country("PH", "Philippines", "+63"), Country("VN", "Vietnam", "+84"),
        Country("ZA", "South Africa", "+27"), Country("NG", "Nigeria", "+234"), Country("KE", "Kenya", "+254"), Country("ET", "Ethiopia", "+251"),
        Country("GH", "Ghana", "+233"), Country("TZ", "Tanzania", "+255"), Country("UG", "Uganda", "+256"), Country("MU", "Mauritius", "+230"),
        Country("MA", "Morocco", "+212"), Country("DZ", "Algeria", "+213"), Country("TN", "Tunisia", "+216"), Country("LY", "Libya", "+218"),
        Country("SD", "Sudan", "+249"),
        Country("BR", "Brazil", "+55"), Country("MX", "Mexico", "+52"), Country("AR", "Argentina", "+54"), Country("CO", "Colombia", "+57"),
        Country("CL", "Chile", "+56"), Country("PE", "Peru", "+51"),
    )

    private val byIso = all.associateBy { it.iso }

    /** The country for a two-letter code such as "PK", or Pakistan when it is not in the list. */
    fun forIso(iso: String?): Country = byIso[iso?.uppercase()] ?: all.first()

    /** Countries sorted by name, with [first] at the top. */
    fun sorted(first: Country): List<Country> = listOf(first) + all.filter { it != first }.sortedBy { it.name }

    /** The country whose code begins [digits] (a number written with its code and no plus), preferring the longest code; null if none. */
    private fun matchCode(digits: String): Country? {
        for (len in 3 downTo 1) {
            if (digits.length <= len) continue
            val code = "+" + digits.take(len)
            all.firstOrNull { it.dial == code }?.let { return it }
        }
        return null
    }

    /** Splits a stored number into its country and the national part. A number with no code gives [default] and the text as it is. */
    fun split(phone: String, default: Country): Pair<Country?, String> {
        val t = phone.trim()
        if (t.isEmpty()) return default to ""
        val international = t.startsWith("+") || t.startsWith("00")
        if (!international) return default to t
        var digits = t.filter { it.isDigit() }
        if (t.startsWith("00")) digits = digits.drop(2)
        val country = matchCode(digits) ?: return null to t
        return country to digits.drop(country.dial.length - 1)
    }

    /** Joins a country and what was typed into the stored form "+92 3001234567". An empty number gives an empty text. */
    fun join(country: Country?, national: String): String {
        val typed = national.trim()
        if (typed.isEmpty()) return ""
        if (country == null) return typed
        var digits = typed.filter { it.isDigit() }
        // A leading 0 is how the number is dialled inside the country; it is not part of the international number (Italy keeps it).
        if (country.dial != "+39") digits = digits.trimStart('0')
        return if (digits.isEmpty()) "" else "${country.dial} $digits"
    }
}

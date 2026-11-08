package com.inamibrahim.kinhold.model

/**
 * The family relationships that have their own word in many languages, told apart by which side of the family they are on and by sex.
 * [Relationships] works out which one applies; [KinWords] says the word for it in a chosen language.
 */
enum class Kin {
    Father, Mother, Son, Daughter, Brother, Sister,
    PatGrandfather, PatGrandmother, MatGrandfather, MatGrandmother,
    FathersElderBrother, FathersYoungerBrother, FathersBrother, FathersSister, MothersBrother, MothersSister,
    BrothersSon, BrothersDaughter, SistersSon, SistersDaughter,
    SonsSon, SonsDaughter, DaughtersSon, DaughtersDaughter,
    /** Cousins, named by the parent they come through (father's brother, father's sister, mother's brother, mother's sister) and their own sex. */
    CousinPatUncleSon, CousinPatUncleDaughter, CousinPatAuntSon, CousinPatAuntDaughter,
    CousinMatUncleSon, CousinMatUncleDaughter, CousinMatAuntSon, CousinMatAuntDaughter,
    Husband, Wife,
}

/** A language whose family words Kinhold can show beside the English relationship. The choice belongs to the phone (a setting), not the tree. */
enum class KinLanguage(val key: String, val label: String) {
    UrduPunjabi("urdu", "Urdu and Punjabi"),
    Hindi("hindi", "Hindi"),
    Bengali("bengali", "Bengali"),
    Arabic("arabic", "Arabic"),
    Persian("persian", "Persian"),
    Turkish("turkish", "Turkish"),
    Spanish("spanish", "Spanish");

    companion object {
        fun fromKey(key: String?): KinLanguage? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The family words of each language, written in Latin letters. Urdu and Punjabi are handled by [Relationships] itself (they also have words
 * for in-laws and for relatives by marriage); the other languages cover blood relatives to the cousins, and husband and wife.
 * Where a language has one word for several kinds of relative, the same word is listed for each.
 */
object KinWords {
    fun of(language: KinLanguage, kin: Kin): String? = when (language) {
        KinLanguage.UrduPunjabi -> null
        KinLanguage.Hindi -> hindi[kin]
        KinLanguage.Bengali -> bengali[kin]
        KinLanguage.Arabic -> arabic[kin]
        KinLanguage.Persian -> persian[kin]
        KinLanguage.Turkish -> turkish[kin]
        KinLanguage.Spanish -> spanish[kin]
    }

    private val hindi = mapOf(
        Kin.Father to "Pita", Kin.Mother to "Mata", Kin.Son to "Beta", Kin.Daughter to "Beti", Kin.Brother to "Bhai", Kin.Sister to "Behen",
        Kin.PatGrandfather to "Dada", Kin.PatGrandmother to "Dadi", Kin.MatGrandfather to "Nana", Kin.MatGrandmother to "Nani",
        Kin.FathersElderBrother to "Tau", Kin.FathersYoungerBrother to "Chacha", Kin.FathersBrother to "Tau or Chacha", Kin.FathersSister to "Bua",
        Kin.MothersBrother to "Mama", Kin.MothersSister to "Mausi",
        Kin.BrothersSon to "Bhatija", Kin.BrothersDaughter to "Bhatiji", Kin.SistersSon to "Bhanja", Kin.SistersDaughter to "Bhanji",
        Kin.SonsSon to "Pota", Kin.SonsDaughter to "Poti", Kin.DaughtersSon to "Nati", Kin.DaughtersDaughter to "Natin",
        Kin.CousinPatUncleSon to "Chachera bhai", Kin.CousinPatUncleDaughter to "Chacheri behen",
        Kin.CousinPatAuntSon to "Phuphera bhai", Kin.CousinPatAuntDaughter to "Phupheri behen",
        Kin.CousinMatUncleSon to "Mamera bhai", Kin.CousinMatUncleDaughter to "Mameri behen",
        Kin.CousinMatAuntSon to "Mausera bhai", Kin.CousinMatAuntDaughter to "Mauseri behen",
        Kin.Husband to "Pati", Kin.Wife to "Patni",
    )

    private val bengali = mapOf(
        Kin.Father to "Baba", Kin.Mother to "Ma", Kin.Son to "Chhele", Kin.Daughter to "Meye", Kin.Brother to "Bhai", Kin.Sister to "Bon",
        Kin.PatGrandfather to "Thakurda", Kin.PatGrandmother to "Thakuma", Kin.MatGrandfather to "Dadu", Kin.MatGrandmother to "Didima",
        Kin.FathersElderBrother to "Jethu", Kin.FathersYoungerBrother to "Kaka", Kin.FathersBrother to "Jethu or Kaka", Kin.FathersSister to "Pishi",
        Kin.MothersBrother to "Mama", Kin.MothersSister to "Mashi",
        Kin.BrothersSon to "Bhaipo", Kin.BrothersDaughter to "Bhaijhi", Kin.SistersSon to "Bhagne", Kin.SistersDaughter to "Bhagni",
        Kin.SonsSon to "Nati", Kin.SonsDaughter to "Natni", Kin.DaughtersSon to "Nati", Kin.DaughtersDaughter to "Natni",
        Kin.CousinPatUncleSon to "Tuto bhai", Kin.CousinPatUncleDaughter to "Tuto bon",
        Kin.CousinPatAuntSon to "Pishuto bhai", Kin.CousinPatAuntDaughter to "Pishuto bon",
        Kin.CousinMatUncleSon to "Mamoto bhai", Kin.CousinMatUncleDaughter to "Mamoto bon",
        Kin.CousinMatAuntSon to "Mashtuto bhai", Kin.CousinMatAuntDaughter to "Mashtuto bon",
        Kin.Husband to "Shami", Kin.Wife to "Stri",
    )

    private val arabic = mapOf(
        Kin.Father to "Ab", Kin.Mother to "Umm", Kin.Son to "Ibn", Kin.Daughter to "Bint", Kin.Brother to "Akh", Kin.Sister to "Ukht",
        Kin.PatGrandfather to "Jadd", Kin.PatGrandmother to "Jadda", Kin.MatGrandfather to "Jadd", Kin.MatGrandmother to "Jadda",
        Kin.FathersElderBrother to "Amm", Kin.FathersYoungerBrother to "Amm", Kin.FathersBrother to "Amm", Kin.FathersSister to "Amma",
        Kin.MothersBrother to "Khal", Kin.MothersSister to "Khala",
        Kin.BrothersSon to "Ibn akh", Kin.BrothersDaughter to "Bint akh", Kin.SistersSon to "Ibn ukht", Kin.SistersDaughter to "Bint ukht",
        Kin.SonsSon to "Hafid", Kin.SonsDaughter to "Hafida", Kin.DaughtersSon to "Hafid", Kin.DaughtersDaughter to "Hafida",
        Kin.CousinPatUncleSon to "Ibn amm", Kin.CousinPatUncleDaughter to "Bint amm",
        Kin.CousinPatAuntSon to "Ibn amma", Kin.CousinPatAuntDaughter to "Bint amma",
        Kin.CousinMatUncleSon to "Ibn khal", Kin.CousinMatUncleDaughter to "Bint khal",
        Kin.CousinMatAuntSon to "Ibn khala", Kin.CousinMatAuntDaughter to "Bint khala",
        Kin.Husband to "Zawj", Kin.Wife to "Zawja",
    )

    private val persian = mapOf(
        Kin.Father to "Pedar", Kin.Mother to "Madar", Kin.Son to "Pesar", Kin.Daughter to "Dokhtar", Kin.Brother to "Baradar", Kin.Sister to "Khahar",
        Kin.PatGrandfather to "Pedarbozorg", Kin.PatGrandmother to "Madarbozorg", Kin.MatGrandfather to "Pedarbozorg", Kin.MatGrandmother to "Madarbozorg",
        Kin.FathersElderBrother to "Amu", Kin.FathersYoungerBrother to "Amu", Kin.FathersBrother to "Amu", Kin.FathersSister to "Ammeh",
        Kin.MothersBrother to "Dayi", Kin.MothersSister to "Khaleh",
        Kin.BrothersSon to "Baradarzadeh", Kin.BrothersDaughter to "Baradarzadeh", Kin.SistersSon to "Khaharzadeh", Kin.SistersDaughter to "Khaharzadeh",
        Kin.SonsSon to "Nave", Kin.SonsDaughter to "Nave", Kin.DaughtersSon to "Nave", Kin.DaughtersDaughter to "Nave",
        Kin.CousinPatUncleSon to "Pesar amu", Kin.CousinPatUncleDaughter to "Dokhtar amu",
        Kin.CousinPatAuntSon to "Pesar ammeh", Kin.CousinPatAuntDaughter to "Dokhtar ammeh",
        Kin.CousinMatUncleSon to "Pesar dayi", Kin.CousinMatUncleDaughter to "Dokhtar dayi",
        Kin.CousinMatAuntSon to "Pesar khaleh", Kin.CousinMatAuntDaughter to "Dokhtar khaleh",
        Kin.Husband to "Shohar", Kin.Wife to "Zan",
    )

    private val turkish = mapOf(
        Kin.Father to "Baba", Kin.Mother to "Anne", Kin.Son to "Oğul", Kin.Daughter to "Kız", Kin.Brother to "Erkek kardeş", Kin.Sister to "Kız kardeş",
        Kin.PatGrandfather to "Dede", Kin.PatGrandmother to "Babaanne", Kin.MatGrandfather to "Dede", Kin.MatGrandmother to "Anneanne",
        Kin.FathersElderBrother to "Amca", Kin.FathersYoungerBrother to "Amca", Kin.FathersBrother to "Amca", Kin.FathersSister to "Hala",
        Kin.MothersBrother to "Dayı", Kin.MothersSister to "Teyze",
        Kin.BrothersSon to "Yeğen", Kin.BrothersDaughter to "Yeğen", Kin.SistersSon to "Yeğen", Kin.SistersDaughter to "Yeğen",
        Kin.SonsSon to "Torun", Kin.SonsDaughter to "Torun", Kin.DaughtersSon to "Torun", Kin.DaughtersDaughter to "Torun",
        Kin.CousinPatUncleSon to "Amca oğlu", Kin.CousinPatUncleDaughter to "Amca kızı",
        Kin.CousinPatAuntSon to "Hala oğlu", Kin.CousinPatAuntDaughter to "Hala kızı",
        Kin.CousinMatUncleSon to "Dayı oğlu", Kin.CousinMatUncleDaughter to "Dayı kızı",
        Kin.CousinMatAuntSon to "Teyze oğlu", Kin.CousinMatAuntDaughter to "Teyze kızı",
        Kin.Husband to "Koca", Kin.Wife to "Eş",
    )

    private val spanish = mapOf(
        Kin.Father to "Padre", Kin.Mother to "Madre", Kin.Son to "Hijo", Kin.Daughter to "Hija", Kin.Brother to "Hermano", Kin.Sister to "Hermana",
        Kin.PatGrandfather to "Abuelo", Kin.PatGrandmother to "Abuela", Kin.MatGrandfather to "Abuelo", Kin.MatGrandmother to "Abuela",
        Kin.FathersElderBrother to "Tío", Kin.FathersYoungerBrother to "Tío", Kin.FathersBrother to "Tío", Kin.FathersSister to "Tía",
        Kin.MothersBrother to "Tío", Kin.MothersSister to "Tía",
        Kin.BrothersSon to "Sobrino", Kin.BrothersDaughter to "Sobrina", Kin.SistersSon to "Sobrino", Kin.SistersDaughter to "Sobrina",
        Kin.SonsSon to "Nieto", Kin.SonsDaughter to "Nieta", Kin.DaughtersSon to "Nieto", Kin.DaughtersDaughter to "Nieta",
        Kin.CousinPatUncleSon to "Primo", Kin.CousinPatUncleDaughter to "Prima", Kin.CousinPatAuntSon to "Primo", Kin.CousinPatAuntDaughter to "Prima",
        Kin.CousinMatUncleSon to "Primo", Kin.CousinMatUncleDaughter to "Prima", Kin.CousinMatAuntSon to "Primo", Kin.CousinMatAuntDaughter to "Prima",
        Kin.Husband to "Esposo", Kin.Wife to "Esposa",
    )
}

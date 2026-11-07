package com.inamibrahim.kinhold.model

import java.util.Random

/**
 * Makes an invented family of any size, so the app can be tried and measured without anyone's real data.
 * The same seed always gives the same family.
 */
object TestFamily {
    private const val NOW = 2026

    private val maleNames = listOf(
        "Ahmed", "Hassan", "Omar", "Bilal", "Ali", "James", "Daniel", "Samuel", "Yusuf", "Imran",
        "Tariq", "Zain", "Adam", "Noah", "Hamza", "Ibrahim", "Usman", "Farhan", "Henry", "Oliver",
    )
    private val femaleNames = listOf(
        "Ayesha", "Fatima", "Sara", "Maryam", "Zainab", "Emma", "Hannah", "Noor", "Amina", "Layla",
        "Hira", "Sana", "Iqra", "Mehwish", "Lucy", "Grace", "Rabia", "Saima", "Alice", "Nadia",
    )
    private val surnames = listOf(
        "Khan", "Malik", "Ibrahim", "Sheikh", "Butt", "Qureshi", "Ahmed", "Raza", "Siddiqui", "Mirza",
        "Chaudhry", "Hussain", "Baig", "Rana", "Javed",
    )

    /** The same invented family as a [Family], which knows each person's parents, spouse and children. */
    fun generateFamily(target: Int, seed: Long = 7L): Family = generate(target, seed).toFamily()

    /** A family of exactly [target] people (at least 2), grown one generation at a time. */
    fun generate(target: Int, seed: Long = 7L): FamilyUnit {
        require(target >= 2) { "A family needs at least two people" }
        val rnd = Random(seed)
        var nextId = 1

        fun person(female: Boolean, born: Int, surname: String): Person {
            val first = (if (female) femaleNames else maleNames).let { it[rnd.nextInt(it.size)] }
            val death = born + 50 + rnd.nextInt(40)
            return Person(
                id = nextId++,
                given = first,
                surname = surname,
                sex = if (female) Sex.Female else Sex.Male,
                born = born,
                died = if (death < NOW) death else null,
            )
        }

        val rootSurname = surnames[rnd.nextInt(surnames.size)]
        val root = FamilyUnit(
            listOf(
                person(false, 1770 + rnd.nextInt(8), rootSurname),
                person(true, 1772 + rnd.nextInt(8), surnames[rnd.nextInt(surnames.size)]),
            ),
        )
        var count = 2
        var level = listOf(root)
        var generation = 0
        while (count < target && level.isNotEmpty() && generation < 14) {
            val next = ArrayList<FamilyUnit>()
            for (unit in level) {
                if (count >= target) break
                val head = unit.partners[0]
                val headBorn = head.born ?: 1800
                repeat(childCount(rnd)) {
                    if (count >= target) return@repeat
                    val born = minOf(headBorn + 22 + rnd.nextInt(10), NOW - rnd.nextInt(3))
                    val child = person(rnd.nextBoolean(), born, rootSurname)
                    count++
                    val partners = arrayListOf(child)
                    if (count < target && born + 18 < NOW && rnd.nextInt(100) < 70) {
                        partners += person(!child.female(), born - 3 + rnd.nextInt(7), surnames[rnd.nextInt(surnames.size)])
                        count++
                    }
                    val childUnit = FamilyUnit(partners)
                    unit.children += childUnit
                    next += childUnit
                }
            }
            level = next
            generation++
        }
        return root
    }

    private fun Person.female(): Boolean = sex == Sex.Female

    private fun childCount(rnd: Random): Int {
        val r = rnd.nextInt(100)
        return when {
            r < 12 -> 1
            r < 45 -> 2
            r < 78 -> 3
            else -> 4
        }
    }
}

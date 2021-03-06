@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

package ide.language.kotlin

object OneLine {
    val oneTypo = "It is <warning descr="BEEN_PART_AGREEMENT">friend</warning> of human"
    val oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human"
    val fewTypos = "It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings"
    val ignoreTemplate = "It is ${1} friend"
    val notIgnoreOtherMistakes = "It is friend. <warning descr="And">But</warning> I have a ${1} here"

    val ignoreCasing = "it is a friend"
}

object MultiLine {
    val oneTypo = """It is <warning descr="BEEN_PART_AGREEMENT">friend</warning> of human"""
    val oneSpellcheckTypo = """It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human"""
    val fewTypos = """It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings"""
    val ignoreTemplate = """It is ${1} friend"""
    val notIgnoreOtherMistakes = """It is friend. <warning descr="And">But</warning> I have a ${1} here"""

    val marginPrefixAsPrefix = """It is 
        |<warning descr="BEEN_PART_AGREEMENT">friend</warning> of human"""

    val marginPrefixInTheMiddle = """It is|friend of human"""

    val ignoreCasing = """it is a friend"""
}

object InFunc {
    fun a(b: String) {
        a("It is <warning descr="BEEN_PART_AGREEMENT">friend</warning> of human")
        a("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human")
        a("It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings")
        a("It is ${1} friend")
        a("It is friend. <warning descr="And">But</warning> I have a ${1} here")

        a("it is a friend")
    }
}


package ru.urfu.imkn.ll


sealed class Symbol<T> {
    abstract val value: T

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as Symbol<*>
        return other.value == value
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "@$value"
    }


}


class Relation(val eq: Boolean, val lt: Boolean, val gt: Boolean) : Symbol<String>() {
    constructor(a: Int, b: Int, c: Int) : this(a != 0, b != 0, c != 0)

    val size = +lt + +gt + +eq
    val ge = gt and eq
    val le = lt and eq
    val none = !(eq || lt || gt)
    override fun toString() = ((lt `@` "<") + (gt `@` ">") + (eq `@` "=")) ifEmptyThen "."
    override val value = toString()
}

sealed class GrammarToken<T> : Symbol<T>()

class EndOfLine(override val value: String = "$") : Symbol<String>()
class StartOfLine(override val value: String = "^") : Symbol<String>()

open class Terminal(override val value: String) : GrammarToken<String>()

class NonTerminal(override val value: String) : GrammarToken<String>()


typealias Rule = Array<GrammarToken<String>>

fun asGrammarToken(c: Char): GrammarToken<String> {
    if (c.isUpperCase()) return NonTerminal(c.toString())
    if (c.isLowerCase()) return Terminal(c.toString())
    throw IllegalArgumentException(c.toString())
}

enum class PrecursiveGrammarType {
    STRONG,
    WEAK,
    NONE
}

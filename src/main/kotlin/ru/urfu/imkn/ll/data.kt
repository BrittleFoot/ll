package ru.urfu.imkn.ll


sealed class Symbol<T> {
    abstract val value: T

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as Symbol<*>
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "@$value"
    }


}

sealed class GrammarToken<T> : Symbol<T>()

class EndOfLine(override val value: String = "$") : Symbol<String>()
class StartOfLine(override val value: String = "^") : Symbol<String>()

open class Terminal(override val value: String) : GrammarToken<String>()
class Empty : Terminal("λ")

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

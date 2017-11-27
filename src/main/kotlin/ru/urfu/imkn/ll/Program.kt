package ru.urfu.imkn.ll

import java.io.File
import java.nio.file.Files


class Relation(val eq: Boolean, val lt: Boolean, val gt: Boolean) {
    constructor(a: Int, b: Int, c: Int) : this(a != 0, b != 0, c != 0)

    val size = +lt + +gt + +eq
    val ge = gt and eq
    override fun toString() = ((lt `@` "<") + (gt `@` ">") + (eq `@` "=")) ifEmptyThen "."
}

class PrecursorTable(private val grammar: LLGrammar) {
    private val equalities = grammar.rules.flatMap { it.value }.flatMap { it.asIterable().bigrams() }.toSet()
    // FIXIT
    private val first = grammar.rules.mapValues { it.value.map { it.first() }.toSet() }
    private val last = grammar.rules.mapValues { it.value.map { it.last() }.toSet() }

    private val ltList = grammar.items.map { it as Symbol<String> to mutableSetOf<Symbol<String>>() }
            .toMap().toMutableMap().also {
        equalities.forEach { pair -> it[pair.first]!!.addAll(first(pair.second)) }
        it[StartOfLine()] = mutableSetOf<Symbol<String>>().also { it.addAll(first(grammar.axiom)) }
    }

    private val gtList =
            grammar.items.map { it as Symbol<String> to mutableSetOf<Symbol<String>>() }
                    .toMap().toMutableMap().also { gtList ->
                equalities.filter { (_, z2) -> z2 is Terminal }.forEach { (z1, z2) ->
                    last(z1).forEach { zEnding ->
                        gtList.getOrPut(zEnding) { mutableSetOf() }.apply { add(z2); addAll(first(z2)) }
                    }
                }
                grammar[grammar.axiom]?.map { it.last() }?.let {
                    gtList.getOrPut(EndOfLine()) { mutableSetOf() }.addAll(it)
                }
            }

    fun first(symbol: Symbol<String>) = first[symbol] ?: mutableSetOf()
    fun last(symbol: Symbol<String>) = last[symbol] ?: mutableSetOf()

    fun compare(a: Symbol<String>, b: Symbol<String>) = Relation((a to b) in equalities, isLt(a to b), isGt(a to b))

    private fun isLt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.first in ltList) && (pair.second in ltList[pair.first]!!)

    private fun isGt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.second is Terminal) && (pair.first in gtList) && (pair.second in gtList[pair.first]!!)

    private val table = grammar.items
            .let { (it + listOf(StartOfLine())) * (it + listOf(EndOfLine())) }
            .map { it to compare(it.first, it.second) }.toMap()

    override fun toString(): String {
        val rows = grammar.items.let { it + listOf(StartOfLine()) }
        val cols = grammar.items.let { it + listOf(EndOfLine()) }
        val sb = StringBuilder(" ${cols.joinToString(" ") { it.value }}\n")

        for (r in rows) {
            sb.append(r.value)
            for (c in cols) {
                sb.append(" " + table[r to c])
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun toString(alignNonTerminals: List<NonTerminal>, alignTerminals: List<Terminal>): String {
        val rows = alignNonTerminals + alignTerminals + listOf(StartOfLine())
        val cols = alignNonTerminals + alignTerminals + listOf(EndOfLine())
        val sb = StringBuilder("  ${cols.joinToString(" ") { it.value }}\n")

        for (r in rows) {
            sb.append(r.value)
            for (c in cols) {
                sb.append(" " + table[r to c])
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}


class LLGrammar(lRules: List<Pair<NonTerminal, Rule>>) {
    val rules: Map<NonTerminal, List<Rule>> = lRules.groupBy { it.first }.mapValues { it.value.map { it.second } }
    val items: List<GrammarToken<String>> = rules.values.flatMap { it }.flatMap { it.asList() }.distinct()
    var axiom: NonTerminal = lRules.first().first
        set(value) = if (value in rules) field = value else
            throw IllegalArgumentException("$value not in rules = [$rules]")

    operator fun get(nt: NonTerminal) = rules[nt]
}


fun main(args: Array<String>) {
    val (inFname, outFname) = parseArgs(args)
    val inputLines = Files.readAllLines(File(inFname).toPath())
    val forGrammar = inputLines.takeWhile(String::isNotEmpty)
            .map { it.split(' ', limit = 2) }
            .map { if (it.size == 2) it[0] to it[1] else throw IllegalArgumentException("unexpected input: $it") }
            .map { (a, bs) -> NonTerminal(a) to bs.map(::asGrammarToken).toTypedArray() }
    val tests = inputLines.drop(forGrammar.size + 1).takeWhile(String::isNotEmpty)

    val terminalsInAppearanceOrder = mutableListOf<Terminal>()
    val nonTerminalsInAppearanceOrder = mutableListOf<NonTerminal>()
    forGrammar.forEach { (nt, ts) ->
        (listOf(nt) + ts).forEach {
            when (it) {
                is NonTerminal -> nonTerminalsInAppearanceOrder.addIfNever(it)
                is Terminal    -> terminalsInAppearanceOrder.addIfNever(it)
            }
        }
    }

    // TODO: Sort output, algoritm itself

    val grammar = LLGrammar(forGrammar)
    val table = PrecursorTable(grammar)

    println(table.toString(nonTerminalsInAppearanceOrder, terminalsInAppearanceOrder))
}

private fun <E> MutableList<E>.addIfNever(it: E) {
    if (!contains(it)) add(it)
}

fun parseArgs(args: Array<String>): Pair<String, String> {
    if (args.size == 2)
        return args[0] to args[1]

    printUsage()
    System.exit(1)
    return "" to ""
}

fun printUsage() {
    println("Usage: .${File.separator}ll <input_file> <output_file>")
}
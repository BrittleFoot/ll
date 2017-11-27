package ru.urfu.imkn.ll

import java.io.File
import java.nio.file.Files


class Relation(val eq: Boolean, val lt: Boolean, val gt: Boolean) {
    constructor(a: Int, b: Int, c: Int) : this(a != 0, b != 0, c != 0)

    val size = +lt + +gt + +eq
    val ge = gt and eq
    val le = lt and eq
    override fun toString() = ((lt `@` "<") + (gt `@` ">") + (eq `@` "=")) ifEmptyThen "."
}

class PrecursorTable(private val grammar: LLGrammar) {
    private val equalities = grammar.rules.flatMap { it.value }.flatMap { it.asIterable().bigrams() }.toSet()
    // FIXIT
    private val first = grammar.rules.mapValues {
        deep(grammar, it.key) { it.first() }
    }
    private val last = grammar.rules.mapValues {
        deep(grammar, it.key) { it.last() }
    }

    private fun deep(grammar: LLGrammar, sym: GrammarToken<String>, selector: (Rule) -> GrammarToken<String>)
            : Set<GrammarToken<String>> {

        if (sym !is NonTerminal) return setOf(sym)

        var firstGen = gen(grammar, sym, selector).toSet()

        while (true) {

            val nextGen = firstGen.flatMap {
                when (it) {
                    is Terminal -> listOf(it)
                    is NonTerminal -> gen(grammar, sym, selector)
                }
            }.toSet()

            if (nextGen == firstGen)
                break
            firstGen = nextGen
        }
        return firstGen

    }

    private fun gen(grammar: LLGrammar, sym: NonTerminal, selector: (Rule) -> GrammarToken<String>)
            = grammar.rules.getOrDefault(sym, listOf()).map(selector)


    private val ltList = grammar.items
            .map { it as Symbol<String> to mutableSetOf<Symbol<String>>() }
            .toMap().toMutableMap().also {
        equalities.forEach { pair -> it[pair.first]!!.addAll(first(pair.second)) }
        it[StartOfLine()] = mutableSetOf<Symbol<String>>().also { it.addAll(first(grammar.axiom)) }
    }

    private val gtList = grammar.items
            .map { it as Symbol<String> to mutableSetOf<Symbol<String>>() }
            .toMap().toMutableMap().also { gtList ->

        equalities.forEach { (z1, z2) ->
            last(z1).forEach { zEnding ->
                gtList.getOrPut(zEnding) { mutableSetOf() }
                        .apply {
                            add(z2)
                            addAll(first(z2).filter { it is Terminal })
                        }
            }
        }
        last(grammar.axiom).forEach { gtList.getOrPut(it) { mutableSetOf() }.add(EndOfLine()) }
    }

    fun first(symbol: Symbol<String>) = first[symbol] ?: mutableSetOf()
    fun last(symbol: Symbol<String>) = last[symbol] ?: mutableSetOf()

    fun compare(a: Symbol<String>, b: Symbol<String>) = Relation((a to b) in equalities, isLt(a to b), isGt(a to b))

    private fun isLt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.first in ltList) && (pair.second in ltList[pair.first]!!)

    private fun isGt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.first in gtList) && (pair.second in gtList[pair.first]!!)

    private val table = grammar.items
            .let { (it + listOf(StartOfLine())) * (it + listOf(EndOfLine())) }
            .map { it to compare(it.first, it.second) }.toMap()

    val tableType =
            when {
                table.values.map(Relation::toString).map(String::length).max()!! <= 1 -> PrecursiveGrammarType.STRONG
                table.values.any { v -> v.toString().length > 1 && !v.le } -> PrecursiveGrammarType.NONE
                else -> PrecursiveGrammarType.WEAK
            }

    override fun toString(): String {
        return toString(
                grammar.items.filter { it is NonTerminal }.map { it as NonTerminal },
                grammar.items.filter { it is Terminal }.map { it as Terminal })
    }

    fun toString(alignNonTerminals: List<NonTerminal>, alignTerminals: List<Terminal>): String {
        val rows = alignNonTerminals + alignTerminals + listOf(StartOfLine())
        val cols = alignNonTerminals + alignTerminals + listOf(EndOfLine())

        val head = listOf(" ") + rows.map { it.value }
        val grid = mutableListOf(head)
        val zrel = Relation(0, 0, 0)

        grid.addAll(cols.map { listOf(it.value) + rows.map { h -> table.getOrDefault(h to it, zrel).toString() } })
        val colLens = grid.map { it.map(String::length).max()!! }

        val sb = StringBuilder()

        for (j in 0 until grid.first().size) {
            for ((i, len) in colLens.withIndex()) sb.append(grid[i][j].padEnd(len + 1))
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
                is Terminal -> terminalsInAppearanceOrder.addIfNever(it)
            }
        }
    }

    // TODO: algoritm itself

    val grammar = LLGrammar(forGrammar)
    val table = PrecursorTable(grammar)

    print(table.toString(nonTerminalsInAppearanceOrder, terminalsInAppearanceOrder))
    println(table.tableType.name.first())
    println()
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

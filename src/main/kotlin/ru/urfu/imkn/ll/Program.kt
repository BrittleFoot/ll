package ru.urfu.imkn.ll

import java.io.File
import java.nio.file.Files


class Relation(val eq: Boolean, val lt: Boolean, val gt: Boolean) {
    constructor(a: Int, b: Int, c: Int) : this(a != 0, b != 0, c != 0)

    val size = +lt + +gt + +eq
    val ge = gt and eq
    override fun toString() = ((lt `@` "<") + (gt `@` ">") + (eq `@` "=")) ifEmptyThen "."
}

class PrecursorTable(val grammar: LLGrammar) {
    val equalities = grammar.rules.flatMap { it.value }.flatMap { it.asIterable().bigrams() }.toSet()
    val table = grammar.items
            .let { (it + listOf(StartOfLine())) * (it + listOf(EndOfLine())) }
            .map { it to compare(it.first, it.second) }.toMap()

    fun compare(a: Symbol<String>, b: Symbol<String>) = Relation((a to b) in equalities, isLt(a to b), isGt(a to b))


    private fun isLt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean {
        return false
    }

    private fun isGt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean {
        return false
    }

    override fun toString(): String {
        val rows = grammar.items.let { (it + listOf(StartOfLine())) }
        val cols = grammar.items.let { (it + listOf(EndOfLine())) }
        val sb = StringBuilder("  " + cols.map { it.value }.joinToString(" ") + "\n")

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
        get() = field
        set(value) = if (value in rules) field = value else
            throw IllegalArgumentException("$value not in rules = [$rules]")

    operator fun get(nt: NonTerminal) = rules[nt]
}


fun main(args: Array<String>) {
    val (inFname, outFname) = parseArgs(args);
    val inputLines = Files.readAllLines(File(inFname).toPath())
    val forGrammar = inputLines.takeWhile(String::isNotEmpty)
            .map { it.split(' ', limit = 2) }
            .map { if (it.size == 2) it[0] to it[1] else throw IllegalArgumentException("unexpected input: $it") }
            .map { (a, bs) -> NonTerminal(a) to bs.map(::asGrammarToken).toTypedArray() }
    val tests = inputLines.drop(forGrammar.size + 1).takeWhile(String::isNotEmpty)

    // TODO: Sort output, algorythm itself

    val grammar = LLGrammar(forGrammar)
    val table = PrecursorTable(grammar)

    println(table)
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
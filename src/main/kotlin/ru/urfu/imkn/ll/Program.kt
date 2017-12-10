package ru.urfu.imkn.ll

import java.io.File
import java.nio.file.Files
import java.util.*

class PrecedenceTable(private val grammar: LLGrammar) {
    private val equalities = grammar.rules.flatMap { it.value }.flatMap { it.asIterable().bigrams() }.toSet()

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
            val nextGen = firstGen + firstGen.flatMap {
                when (it) {
                    is Terminal    -> listOf(it)
                    is NonTerminal -> gen(grammar, it, selector)
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
        it[StartOfLine()]?.add(grammar.axiom)
    }

    private val gtList = grammar.items
            .map { it as Symbol<String> to mutableSetOf<Symbol<String>>() }
            .toMap().toMutableMap().also { gtList ->

        equalities.forEach { (z1, z2) ->
            last(z1).forEach { zEnding ->
                gtList.getOrPut(zEnding) { mutableSetOf() }
                        .apply {
                            if (z2 is Terminal) add(z2)
                            addAll(first(z2).filter { it is Terminal })
                        }
            }
        }
        last(grammar.axiom).forEach { gtList.getOrPut(it) { mutableSetOf() }.add(EndOfLine()) }
        gtList[grammar.axiom]?.add(EndOfLine())
    }

    private fun first(symbol: Symbol<String>) = first[symbol] ?: mutableSetOf()
    private fun last(symbol: Symbol<String>) = last[symbol] ?: mutableSetOf()

    private fun cmp(a: Symbol<String>, b: Symbol<String>) = Relation((a to b) in equalities, isLt(a to b), isGt(a to b))

    private fun isLt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.first in ltList) && (pair.second in ltList[pair.first]!!)

    private fun isGt(pair: Pair<Symbol<String>, Symbol<String>>): Boolean =
            (pair.first in gtList) && (pair.second in gtList[pair.first]!!)

    private val table = grammar.items
            .let { (it + listOf(StartOfLine())) * (it + listOf(EndOfLine())) }
            .map { it to cmp(it.first, it.second) }.toMap()

    private val isGrammarWeak = table.values.all { v -> !((v.lt || v.eq) && v.gt) } && secondCond()

    private val isGrammarSimple = table.values.map(Relation::toString).map(String::length).max()!! <= 1

    val tableType =
            when {
                isGrammarSimple -> PrecedenceGrammarType.SIMPLE
                isGrammarWeak   -> PrecedenceGrammarType.WEAK
                else            -> PrecedenceGrammarType.NONE
            }

    private fun secondCond(): Boolean {

        return table.filter { (x, rel) -> (rel.lt || rel.eq) && x.second is NonTerminal }.entries.all { (pair, _) ->
            val (x, B: NonTerminal) = pair.first to pair.second as NonTerminal
            grammar[B].none { bRule ->
                grammar
                        .rules.entries
                        .flatMap { it.value }
                        .any { bRule.isSuffixOf(it) && it.size > bRule.size && it[it.size - bRule.size - 1] == x }
            }
        }
    }


    operator fun get(a: Symbol<String>, b: Symbol<String>): Relation = table.getOrDefault(a to b, Relation(0, 0, 0))

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
            sb.append("\r\n")
        }

        return sb.toString()
    }
}

private fun <T> Array<T>.isSuffixOf(x: Array<T>) =
        size <= x.size && (1..size).none { x[x.size - it] != this[size - it] }


class LLGrammar(lRules: List<Pair<NonTerminal, Rule>>) {
    val rules = lRules.groupBy { it.first }.mapValues { it.value.map { it.second } }

    val items = (rules.values.flatMap { it }.flatMap { it.asList() } + rules.keys).distinct()

    var axiom = lRules.first().first
        set(value) = if (value in rules) field = value else
            throw IllegalArgumentException("$value not in rules = [$rules]")

    operator fun get(nt: NonTerminal) = rules.getOrDefault(nt, arrayListOf())

    private fun repr(l: Collection<Symbol<String>>, r: Collection<Symbol<String>>): String {
        return listOf(l.joinToString("") { it.value }, r.joinToString("") { it.value }).joinToString(" ")
    }

    private fun ntByRule(rule: Rule) = this.rules
            .mapValues { (_, v) -> v.filter { it.contentEquals(rule) } }
            .filter { (_, v) -> v.isNotEmpty() }
            .mapValues { it.value.first().asList() }

    private fun getAvailableBaises(stack: Stack<Symbol<String>>)
            : MutableMap<Int, Pair<NonTerminal, List<Symbol<String>>>> {
        val maxBaises = mutableMapOf<Int, Pair<NonTerminal, List<Symbol<String>>>>()
        val baisesContainer = mutableListOf<Symbol<String>>()

        for (it in stack.reversed()) {

            baisesContainer.add(it)

            if (it is Relation && it.lt) {
                val basisCondidate = baisesContainer.reversed()
                val ntByRule = ntByRule(basisCondidate
                        .filter { it is GrammarToken<String> }
                        .map { it as GrammarToken<String> }
                        .toTypedArray())

                if (ntByRule.isNotEmpty()) {
                    val (nonTerminal, _) = ntByRule.entries.first()
                    maxBaises[basisCondidate.size] = nonTerminal to basisCondidate
                }

                if (!it.le) break
            }
        }
        return maxBaises
    }

    fun parse(table: PrecedenceTable, string: String): String {
        val stack = Stack<Symbol<String>>().apply { add(StartOfLine()) }
        val input = ArrayDeque<Symbol<String>>().apply {
            addAll(string.map { Terminal(it.toString()) })
            add(EndOfLine())
        }

        val output = mutableListOf<String>()
        val putln: (String) -> Unit = { s: String -> output.add(s) }

        repr(stack.filter { it !is Relation }, input).also(putln)

        while (input.isNotEmpty()) {
            val next = input.peek()
            val top = stack.peek()

            val rel = table[top, next]
            if (rel.none) {
                putln("error")
                break
            }

            if (rel.gt) {

                val maxBaises = getAvailableBaises(stack)

                if (maxBaises.isEmpty()) {
                    if (input.peek() == EndOfLine() && stack.size == 3 && stack.peek() == axiom) {
                        stack.push(input.poll())
                        stack.filter { it !is Relation }.joinToString("") { it.value }.also(putln)
                        break
                    }
                    putln("error: Cant find basis in $stack")
                    break
                }
                val (ruleLength, resultRule) = maxBaises.entries.maxBy { it.key }!!

                stack.pop(ruleLength)

                val ntop = stack.peek()

                val relation = table[ntop, resultRule.first]
                if (relation.none) {
                    putln("error")
                    break
                }

                stack.push(relation)
                stack.push(resultRule.first)

            } else {
                stack.push(rel)
                stack.push(next)
                input.poll()
            }
            //println("$stack ${table[stack.peek(), input.peek()]} $input")
            repr(stack.filter { it !is Relation }, input).also(putln)
        }

        return output.joinToString("\r\n")
    }
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

    terminalsInAppearanceOrder.sortWith(kotlin.Comparator { a, b ->
        val aSym = a.value[0]
        val bSym = b.value[0]
        when {
            aSym.isDigit() && bSym.isDigit() -> aSym.compareTo(bSym)
            aSym.isLetter() && bSym.isLetter() -> aSym.compareTo(bSym)
            aSym.isDigit() && bSym.isLetter() -> 1
            aSym.isLetter() && bSym.isDigit() -> -1
            aSym.isLetter() -> -1
            aSym.isDigit() -> -1
            else -> aSym.compareTo(bSym)
        }
    })

    val output = mutableListOf<String>()
    val put: (String) -> Unit = { output.add(it) }
    val putln: (String) -> Unit = { output.add("$it\r\n") }

    val grammar = LLGrammar(forGrammar)
    val table = PrecedenceTable(grammar)

    put(table.toString(nonTerminalsInAppearanceOrder, terminalsInAppearanceOrder))
    putln(table.tableType.name.first().toString())
    putln("")

    if (table.tableType != PrecedenceGrammarType.NONE)
        tests.joinToString("\r\n\r\n") { grammar.parse(table, it) }.also(putln)

//    print(output.joinToString(""))
    Files.write(File(outFname).toPath(), output.joinToString("").toByteArray())
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

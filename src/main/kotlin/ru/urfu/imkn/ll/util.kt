package ru.urfu.imkn.ll

import java.util.*


operator fun <T, U> Iterable<T>.times(that: Iterable<U>) = this.flatMap { a -> that.map { b -> a to b } }

fun <T> Iterable<T>.bigrams(): Iterable<Pair<T, T>> {
    var last: T? = null
    val result = ArrayList<Pair<T, T>>()
    for (e: T in this) {
        if (last != null) result.add(last to e)
        last = e
    }
    return result
}

operator fun Boolean.unaryPlus() = when (this) {
    true  -> 1
    false -> 0
}

infix fun Boolean.`@`(s: String) = if (this) s else ""
infix fun String.ifEmptyThen(s: String) = if (isNotEmpty()) this else s


fun <T> Stack<T>.pop(n: Int) = (1..n).map { this.pop() }.reversed()
fun <T> Stack<T>.popWhile(cond: (T) -> Boolean): List<T> {
    val popped: MutableList<T> = mutableListOf()
    while (cond(peek())) popped.add(pop())
    return popped.reversed();
}
fun <T> Stack<T>.includedPopWhile(cond: (T) -> Boolean): List<T> {
    val popped: MutableList<T> = mutableListOf()
    while (cond(peek())) popped.add(pop())
    popped.add(pop())
    return popped.reversed();
}
fun <T> Stack<T>.includedPopWhileNot(cond: (T) -> Boolean): List<T> {
    val popped: MutableList<T> = mutableListOf()
    while (!cond(peek())) popped.add(pop())
    popped.add(pop())
    return popped.reversed()
}
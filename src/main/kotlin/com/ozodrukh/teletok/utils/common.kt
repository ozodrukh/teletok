package com.ozodrukh.teletok.utils


public fun humanReadableCounter(count: Long): String {
    var top = count
    var d = 0
    val t = arrayOf("", "K", "M", "B")
    while (top / 1000 > 0) {
        top /= 1000
        d++
    }
    d = d.coerceAtMost(t.size)
    return "$top${t[d]}"
}
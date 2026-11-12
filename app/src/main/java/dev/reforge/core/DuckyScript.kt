package dev.reforge.core

object DuckyScript {

    fun compile(text: String): String = uniffi.skyla_core.compileDucky(text)
}

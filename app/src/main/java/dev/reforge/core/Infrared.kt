package dev.reforge.core

/**
 * IR code generators. Phones transmit only (no receiver), so this powers a
 * universal-remote / TV-B-Gone style feature. Patterns are alternating
 * on/off durations in microseconds, as ConsumerIrManager.transmit() expects.
 *
 * The generators and catalog now live in the Rust native core (`skyla_core`)
 * and are reached through UniFFI; this keeps the original Kotlin API so callers
 * (IrViewModel) don't change. See rust/src/infrared.rs.
 */
object Infrared {
    val NEC_HZ: Int = uniffi.skyla_core.irNecHz()
    val SONY_HZ: Int = uniffi.skyla_core.irSonyHz()

    fun nec(address: Int, command: Int): IntArray =
        uniffi.skyla_core.irNec(address, command).toIntArray()

    fun samsung(address16: Int, command: Int): IntArray =
        uniffi.skyla_core.irSamsung(address16, command).toIntArray()

    fun sony12(command: Int, device: Int): IntArray =
        uniffi.skyla_core.irSony12(command, device).toIntArray()
}

/** A labelled IR command: carrier frequency + pattern. */
data class IrCode(val name: String, val freq: Int, val pattern: IntArray) {
    override fun equals(other: Any?) = other is IrCode && other.name == name
    override fun hashCode() = name.hashCode()
}

/** Starter multi-brand catalog (TV power + a few controls), sourced from the core. */
object IrCatalog {
    val tvPower: List<IrCode> =
        uniffi.skyla_core.irTvPower().map { IrCode(it.name, it.freq, it.pattern.toIntArray()) }
    val samsungControls: List<IrCode> =
        uniffi.skyla_core.irSamsungControls().map { IrCode(it.name, it.freq, it.pattern.toIntArray()) }
}

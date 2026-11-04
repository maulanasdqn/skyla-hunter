package dev.reforge.core

/**
 * IR code generators. Phones transmit only (no receiver), so this powers a
 * universal-remote / TV-B-Gone style feature. Patterns are alternating
 * on/off durations in microseconds, as ConsumerIrManager.transmit() expects.
 */
object Infrared {
    const val NEC_HZ = 38_000
    const val SONY_HZ = 40_000

    /** Standard NEC: 8-bit address + inverse, 8-bit command + inverse, LSB-first. */
    fun nec(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(68)
        out.add(9000); out.add(4500)
        fun byte(b: Int) {
            for (i in 0 until 8) {
                out.add(560)
                out.add(if ((b shr i) and 1 == 1) 1690 else 560)
            }
        }
        byte(address and 0xFF)
        byte(address.inv() and 0xFF)
        byte(command and 0xFF)
        byte(command.inv() and 0xFF)
        out.add(560)
        return out.toIntArray()
    }

    /** Samsung: 4500/4500 lead, 16-bit address, 8-bit command + inverse, LSB-first. */
    fun samsung(address16: Int, command: Int): IntArray {
        val out = ArrayList<Int>(76)
        out.add(4500); out.add(4500)
        fun bits(value: Int, n: Int) {
            for (i in 0 until n) {
                out.add(560)
                out.add(if ((value shr i) and 1 == 1) 1690 else 560)
            }
        }
        bits(address16, 16)
        bits(command and 0xFF, 8)
        bits(command.inv() and 0xFF, 8)
        out.add(560)
        return out.toIntArray()
    }

    /** Sony SIRC 12-bit: 7-bit command + 5-bit device, LSB-first, 40 kHz. */
    fun sony12(command: Int, device: Int): IntArray {
        val out = ArrayList<Int>(28)
        out.add(2400); out.add(600) // header
        fun bits(value: Int, n: Int) {
            for (i in 0 until n) {
                out.add(if ((value shr i) and 1 == 1) 1200 else 600)
                out.add(600)
            }
        }
        bits(command and 0x7F, 7)
        bits(device and 0x1F, 5)
        return out.toIntArray()
    }
}

/** A labelled IR command: carrier frequency + pattern. */
data class IrCode(val name: String, val freq: Int, val pattern: IntArray) {
    override fun equals(other: Any?) = other is IrCode && other.name == name
    override fun hashCode() = name.hashCode()
}

/** Starter multi-brand catalog (TV power + a few controls). Needs testing vs a real set. */
object IrCatalog {
    val tvPower = listOf(
        IrCode("Samsung — Power", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x40)),
        IrCode("LG — Power", Infrared.NEC_HZ, Infrared.nec(0x04, 0x08)),
        IrCode("Sony — Power", Infrared.SONY_HZ, Infrared.sony12(0x15, 0x01)),
        IrCode("Panasonic — Power", Infrared.NEC_HZ, Infrared.nec(0x40, 0x3D)),
        IrCode("NEC generic — Power", Infrared.NEC_HZ, Infrared.nec(0x00, 0x02)),
        IrCode("Toshiba — Power", Infrared.NEC_HZ, Infrared.nec(0x40, 0x12)),
    )
    val samsungControls = listOf(
        IrCode("Vol +", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x07)),
        IrCode("Vol −", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x0B)),
        IrCode("Ch +", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x12)),
        IrCode("Ch −", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x10)),
        IrCode("Mute", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x0F)),
        IrCode("Source", Infrared.NEC_HZ, Infrared.samsung(0xE0E0, 0x01)),
    )
}

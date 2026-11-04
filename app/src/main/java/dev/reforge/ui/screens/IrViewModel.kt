package dev.reforge.ui.screens

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.Infrared
import dev.reforge.core.IrCatalog
import dev.reforge.core.IrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Infrared blaster control via Android's ConsumerIrManager (transmit only —
 * phones have no IR receiver). Powers a universal remote / TV-B-Gone sweep.
 */
class IrViewModel(app: Application) : AndroidViewModel(app) {
    private val ir = app.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    val hasEmitter: Boolean = ir?.hasIrEmitter() == true

    val tvPower = IrCatalog.tvPower
    val controls = IrCatalog.samsungControls

    var lastSent by mutableStateOf<String?>(null)
        private set
    var sweeping by mutableStateOf(false)
        private set

    fun send(code: IrCode) {
        if (!hasEmitter) { lastSent = "no IR emitter"; return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { transmit(code) }
            lastSent = if (ok) "sent: ${code.name}" else "transmit failed"
        }
    }

    /** TV-B-Gone: fire every TV power code in sequence. */
    fun sweep() {
        if (!hasEmitter || sweeping) return
        sweeping = true
        viewModelScope.launch {
            for (c in tvPower) {
                lastSent = "sweep → ${c.name}"
                withContext(Dispatchers.IO) { transmit(c) }
                delay(220)
            }
            lastSent = "sweep complete"
            sweeping = false
        }
    }

    fun sendCustomNec(addrHex: String, cmdHex: String) {
        val a = addrHex.trim().removePrefix("0x").toIntOrNull(16)
        val c = cmdHex.trim().removePrefix("0x").toIntOrNull(16)
        if (a == null || c == null) { lastSent = "bad hex (e.g. 00 / 02)"; return }
        send(IrCode("NEC $addrHex/$cmdHex", Infrared.NEC_HZ, Infrared.nec(a, c)))
    }

    private fun transmit(code: IrCode): Boolean = try {
        ir?.transmit(code.freq, code.pattern); true
    } catch (e: Exception) { false }
}

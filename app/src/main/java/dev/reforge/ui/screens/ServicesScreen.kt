package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.core.KaliService
import dev.reforge.core.KaliServices
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServiceRow(val svc: KaliService, val running: Boolean?, val busy: Boolean = false)

class ServicesViewModel(app: Application) : AndroidViewModel(app) {
    private val _rows = mutableStateOf(KaliServices.all.map { ServiceRow(it, null) })
    val rows: State<List<ServiceRow>> = _rows

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                KaliServices.all.map { ServiceRow(it, KaliServices.isRunning(it)) }
            }
            _rows.value = updated
        }
    }

    fun toggle(id: String) {
        setBusy(id, true)
        viewModelScope.launch {
            val svc = KaliServices.all.first { it.id == id }
            val running = withContext(Dispatchers.IO) { KaliServices.toggle(svc) }
            _rows.value = _rows.value.map {
                if (it.svc.id == id) it.copy(running = running, busy = false) else it
            }
        }
    }

    private fun setBusy(id: String, busy: Boolean) {
        _rows.value = _rows.value.map { if (it.svc.id == id) it.copy(busy = busy) else it }
    }
}

@Composable
fun ServicesScreen(onBack: () -> Unit, vm: ServicesViewModel = viewModel()) {
    val rows by vm.rows
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Kali Services", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Start / stop services in the chroot",
                    style = MaterialTheme.typography.bodyMedium, color = MutedDark,
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows, key = { it.svc.id }) { row ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(if (row.running == true) Accent else MutedDark)
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.svc.name, style = MaterialTheme.typography.titleMedium)
                            Text(row.svc.detail, style = MaterialTheme.typography.bodyMedium, color = MutedDark)
                        }
                        if (row.busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                        } else {
                            Switch(
                                checked = row.running == true,
                                onCheckedChange = { vm.toggle(row.svc.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Accent,
                                    checkedTrackColor = Accent.copy(alpha = 0.4f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

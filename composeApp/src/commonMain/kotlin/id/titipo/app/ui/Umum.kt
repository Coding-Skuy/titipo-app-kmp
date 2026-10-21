package id.titipo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Kerangka layar bersama. [tag] = testTag selector Maestro (siklus 2).
 * [spanduk] tampil di atas konten (mis. banner luring vendor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KerangkaLayar(
    judul: String,
    tag: String,
    onKeluar: (() -> Unit)? = null,
    spanduk: @Composable (() -> Unit)? = null,
    konten: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(judul) },
                actions = {
                    if (onKeluar != null) {
                        TextButton(onClick = onKeluar, modifier = Modifier.testTag("tombol_keluar")) { Text("Keluar") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            spanduk?.invoke()
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = konten,
            )
        }
    }
}

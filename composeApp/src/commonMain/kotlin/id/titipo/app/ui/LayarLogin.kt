package id.titipo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.titipo.shared.auth.AlurLogin
import id.titipo.shared.auth.HasilAlurLogin
import kotlinx.coroutines.launch

/** Teks galat login untuk ditampilkan; pemetaan dipisah agar bisa diuji. */
fun pesanGalatLogin(hasil: HasilAlurLogin): String? = when (hasil) {
    is HasilAlurLogin.Sukses -> null
    HasilAlurLogin.KredensialSalah -> "Email atau kata sandi salah."
    HasilAlurLogin.AudiensSalah -> "Akun ini tidak berlaku untuk TitipO."
    HasilAlurLogin.PeranDitolak -> "Peran akun tidak didukung di aplikasi ini (admin memakai web)."
    HasilAlurLogin.TokenKedaluwarsa -> "Sesi sudah kedaluwarsa, coba masuk lagi."
    is HasilAlurLogin.Gagal -> "Tidak bisa masuk: ${hasil.pesan}"
}

@Composable
fun LayarLogin(alur: AlurLogin) {
    var email by remember { mutableStateOf("") }
    var sandi by remember { mutableStateOf("") }
    var galat by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("layar_login"),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("TitipO", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("login_email"),
        )
        OutlinedTextField(
            value = sandi, onValueChange = { sandi = it }, label = { Text("Kata sandi") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("login_sandi"),
        )
        galat?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("login_galat")) }
        Button(
            onClick = {
                sibuk = true; galat = null
                scope.launch {
                    galat = pesanGalatLogin(alur.jalankan(email, sandi))
                    sibuk = false
                }
            },
            enabled = !sibuk,
            modifier = Modifier.fillMaxWidth().testTag("login_masuk"),
        ) { Text(if (sibuk) "Memproses..." else "Masuk") }
    }
}

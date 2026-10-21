package id.titipo.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import id.titipo.app.di.KonfigEndpoint
import id.titipo.shared.api.PembacaBerkas
import id.titipo.shared.auth.PenyimpanToken
import id.titipo.shared.db.TitipoDb
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

fun modulAndroid(context: Context): Module = module {
    single<PenyimpanToken> { PenyimpanTokenAndroid(context) }
    single<SqlDriver> { AndroidSqliteDriver(TitipoDb.Schema, context, "titipo.db") }
    single { PembacaBerkas { path -> File(path).readBytes() } }
    single { KonfigEndpoint(BuildConfig.URL_API, BuildConfig.URL_AUTH) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = modulAndroid(applicationContext)
        setContent { App(platform) }
    }
}

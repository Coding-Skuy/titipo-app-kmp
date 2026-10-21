package id.titipo.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.titipo.shared.db.TitipoDb

actual fun buatDbUji(): TitipoDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    TitipoDb.Schema.create(driver)
    return TitipoDb(driver)
}

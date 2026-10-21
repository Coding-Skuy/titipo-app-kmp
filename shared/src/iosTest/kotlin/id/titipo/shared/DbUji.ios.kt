package id.titipo.shared

import app.cash.sqldelight.driver.native.inMemoryDriver
import id.titipo.shared.db.TitipoDb

actual fun buatDbUji(): TitipoDb = TitipoDb(inMemoryDriver(TitipoDb.Schema))

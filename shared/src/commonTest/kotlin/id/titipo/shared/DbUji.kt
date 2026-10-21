package id.titipo.shared

import id.titipo.shared.db.TitipoDb

/** DB SQLDelight in-memory untuk uji (driver per platform). */
expect fun buatDbUji(): TitipoDb

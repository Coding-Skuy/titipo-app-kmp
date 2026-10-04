# composeApp — Shared UI TitipO (KMP)

Modul bersama untuk Android + iOS. 1 codebase, 2 peran.

## Navigasi (Navigation3 1.0.0)

```kotlin
// Rute peran — wajib diawali prefix berikut:
sealed interface TitipRoute {
    // Rumah tangga
    data object HomeList : TitipRoute        // route/home/list
    data object HomeSubscription : TitipRoute // route/home/subscription
    // Vendor
    data object VendorCatalog : TitipRoute   // route/vendor/catalog
    data object VendorOrders : TitipRoute    // route/vendor/orders
}
```

Gunakan `navigation3-runtime` + `navigation3-ui` dari `gradle/libs.versions.toml`.
Backstack per peran dipisahkan; ganti peran = reset backstack + muat ulang JWT claim `role`.

## Paket

- `ui.home/` — layar rumah tangga (titip harian, langganan)
- `ui.vendor/` — layar vendor (katalog, jadwal, pesanan masuk)
- `data/auth/` — penyimpan JWT Lumbung (`aud=titipo`)
- `data/api/` — klien HTTP ke `titipo-backend-service`

Lihat README utama untuk kontrak auth lengkap.

# TitipO App KMP — Divisi TitipO (Community Commerce)

> 1 codebase, 2 peran: **vendor** (penitip keliling) + **rumah tangga** (penitip harian).
> Bagian dari TownHall: [TitipO-TownHall](https://github.com/Coding-Skuy/TitipO-TownHall).
> Kontrak auth mengikuti pola Lumbung: JWT dengan `aud=titipo`, verifikasi via JWKS Lumbung — lihat [Lumbung-TownHall](https://github.com/Coding-Skuy/Lumbung-TownHall) dan `titipo-backend-service/docs/auth.md`.

## Status

Template awal Opsi A — siap dikembangkan.

## Teknologi (pin standar)

- Kotlin `2.2.20`
- Compose Multiplatform `1.8.2`
- Navigation3 `1.0.0` (`org.jetbrains.androidx.navigation3:navigation3-runtime`)
- Gradle wrapper 8.10+, JDK 17

Versi lengkap: lihat `gradle/libs.versions.toml`.

## Struktur

```text
titipo-app-kmp/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── composeApp/            # shared UI KMP (vendor + rumah tangga)
│   ├── src/commonMain/   #导航 Navigation3 + 2 role graph
│   ├── src/androidMain/
│   └── src/iosMain/
└── README.md
```

## Peran

| Peran | Rute Navigation3 | Fitur awal |
|-------|------------------|------------|
| Vendor | `route/vendor/...` | katalog titipan, jadwal keliling, terima pesanan |
| Rumah tangga | `route/home/...` | titip harian, langganan, lacak vendor |

Satu binary, pemilihan peran setelah login. Role dikirim sebagai claim `role` dalam JWT Lumbung (`vendor` / `household` / `admin`).

## Auth

1. Login via Lumbung (email/OTP).
2. Terima JWT `aud=titipo`.
3. Simpan di DataStore (Android) / Keychain (iOS), kirim sebagai `Authorization: Bearer <token>` ke `titipo-backend-service`.
4. Refresh mengikuti masa berlaku token Lumbung.

## Jalankan

```bash
# Android
./gradlew :composeApp:assembleDebug

# Desktop (uji cepat shared UI)
./gradlew :composeApp:run
```

Build CI: lihat [titipo-infra-devops](https://github.com/Coding-Skuy/titipo-infra-devops/blob/main/ci/build-kmp.md).

## Tautan

- TownHall: https://github.com/Coding-Skuy/TitipO-TownHall
- Backend: https://github.com/Coding-Skuy/titipo-backend-service
- Desain: https://github.com/Coding-Skuy/titipo-design
- Kontrak auth Lumbung: https://github.com/Coding-Skuy/Lumbung-TownHall

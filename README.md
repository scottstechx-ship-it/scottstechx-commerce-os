<div align="center">

# 🛒 ScottsTechX Commerce OS

### *Trust-gated marketplace for Uganda — Fastify + Postgres backend with a Kotlin/Compose Android client.*

![Flagship](https://img.shields.io/badge/Status-Flagship-ffb547?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-1adba9?style=for-the-badge)
![Backend](https://img.shields.io/badge/Backend-Fastify_5-202020?style=for-the-badge&logo=fastify&logoColor=white)
![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![DB](https://img.shields.io/badge/Database-Postgres_16-336791?style=for-the-badge&logo=postgresql&logoColor=white)
![Tests](https://img.shields.io/badge/Tests-113_passing-22c55e?style=for-the-badge&logo=vitest&logoColor=white)

</div>

---

## ✨ What is this?

A **production-ready marketplace platform** built for Uganda-style mass-market use:

- 🛡️ **Trust-gated** — every action gated by JWT + role + trust score
- 🏪 **Three-sided** — buyer / seller / driver flows in one codebase
- 🤖 **AI assist** — NVIDIA Nemotron (via NIM), grounded in the live product catalog
- 📍 **Location-ranked nearby sellers** — geo queries on Postgres
- 💸 **Nylon Pay mobile-money** — MTN/Airtel via the official SDK + webhooks
- 🔐 **Firebase Auth** — email/password signup + Google One-Tap
- 🧱 **Modular monolith** — 12 feature modules, easy to extract later

---

## 🧱 Backend (12_Backend/)

**Stack:** Fastify 5 · TypeScript · PostgreSQL 16 · Zod · `jose` (JWT) · `embedded-postgres` · Vitest

```
12_Backend/
├── src/
│   ├── server.ts
│   └── modules/
│       ├── auth/      # JWT + Firebase Auth + phone login
│       ├── sellers/   # seller onboarding, listings
│       ├── orders/    # cart, checkout, order state machine
│       ├── payments/  # mobile-money adapter
│       ├── reviews/   # ratings + abuse signals
│       ├── trust/     # trust-score + gate enforcement
│       ├── chat/      # buyer↔seller messaging
│       ├── logistics/ # driver matching, route hints
│       ├── fx/        # UGX/USD/EUR rate snapshots
│       ├── ai/        # NVIDIA Nemotron assist + catalog grounding
│       └── audit/     # tamper-evident event log
├── migrations/
├── test/              # 113 vitest tests
├── Dockerfile
├── render.yaml        # one-click Render deploy
└── openapi.json
```

**Run:**
```bash
cd 12_Backend
npm install
cp .env.example .env     # fill JWT_SECRET, DATABASE_URL
npm test                 # 113 tests pass
npm run dev              # listens on :3001
```

**Deploy (one click):** Render Blueprint in `render.yaml` provisions API + managed Postgres automatically.

---

## 📱 Android (android-app/)

**Stack:** Kotlin · Jetpack Compose · Hilt · KSP · Material 3 · Coroutines · DataStore

```
android-app/app/src/main/java/com/scottstechx/commerceos/
├── data/
│   ├── auth/         # Google One-Tap + JWT storage
│   ├── cache/        # DataStore + Room
│   ├── capture/      # camera + QR
│   ├── location/     # FusedLocationProvider
│   └── remote/       # Retrofit + DTOs
├── di/               # Hilt modules
├── security/         # keystore + cert pinning
└── ui/
    ├── buyer/        # browse, cart, checkout
    ├── seller/       # listings, orders, dashboard
    ├── driver/       # route, deliveries, earnings
    ├── ai/           # chat-style assist UI
    ├── login/        # Google One-Tap
    ├── nearby/       # geo-ranked seller list
    ├── animation/    # hero, splash
    ├── brand/        # ScottsTechX theme tokens
    └── common/       # reusable composables
```

**Build:**
```bash
cd android-app
./gradlew :app:assembleDebug \
  -PapiBaseUrl=http://10.0.2.2:3001/        # emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Release:**
```bash
./gradlew :app:assembleRelease \
  -PapiBaseUrl=https://api.your-domain.example/
```

---

## 🎯 Why modular monolith?

- 🚀 **Ship faster** than microservices — single deploy, single DB transaction
- 🔌 **Extract later** — each module is bounded, can be split when needed
- 🧪 **Test in isolation** — modules communicate via injected interfaces

---

## 📊 By the numbers

| Metric | Value |
|---|---|
| Backend modules | **12** |
| Backend tests | **113 passing** |
| Compose screens | **20+** |
| Android min/target SDK | **26 / 34** |
| Postgres version | **16** |
| First deploy | **3-5 min** via Render Blueprint |

---

## 🛣️ Roadmap

- [x] Modular monolith with 12 bounded modules
- [x] Auth (JWT + Google One-Tap)
- [x] Listings, orders, payments, reviews
- [x] Trust-score + gate enforcement
- [x] AI assist (Gemini)
- [ ] Driver app v2 with offline route cache
- [ ] M-Pesa adapter
- [ ] iOS client (Kotlin Multiplatform)

---

## 📬 Contact

- 📧 **scottsstechx@gmail.com**
- 🐙 **[@scottstechx-ship-it](https://github.com/scottstechx-ship-it)**
- 🏢 ScottsTechX Enterprise (U) Ltd · Kampala 🇺🇬

<sub>© 2026 ScottsTechX Enterprise (U) Ltd · Made with ❤️ in Kampala 🇺🇬</sub>
# GameNexa Release Audit Status

Date: 2026-09-29

## Backend release gate
- npm run check: PASS
- npm test / test:release: PASS
- Reservation tests: 9/9 PASS
- Security isolation E2E: PASS
- Financial engine: 5/5 PASS
- Manual subscription workflow: 3/3 PASS
- Super Manager auth/routes: PASS
- Database integrity: 7/7 PASS
- Live API time: 200
- Unauthenticated Manager endpoint: 401
- Docker API/PostgreSQL/Nginx: healthy/running

## Android
- Gradle wrapper present locally: PASS
- JDK 17 installed: PASS
- AGP 9.1.1 / Gradle 9.3.1 compatibility: PASS by official compatibility matrix
- KSP 2.3.10 retained for AGP 9 migration compatibility
- Local Android compile: NOT PASS on this 1GB VPS because dependency resolution is too slow/unreliable; no APK was produced.
- GitHub Actions workflow prepared locally for main/master, JDK 17, wrapper build.
- GitHub remote currently lacks the local wrapper JAR and the updated workflow/source because GitHub write access returned HTTP 403 in this session.

## Release declaration rule
The project must not be labeled 100% release-ready until:
1. The exact current source is pushed to GitHub.
2. GitHub Actions executes the current source.
3. JVM tests pass.
4. Debug APK builds successfully.
5. Release AAB builds successfully with release signing secrets.
6. A real-device/emulator E2E run covers installation, Manager auth, Customer auth, station lifecycle, buffet, reservation, settlement, invoice/history, offline/reconnect, trial, subscription, and logout/expiry.

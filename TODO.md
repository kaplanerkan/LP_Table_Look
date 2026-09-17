# TODO

Kod incelemesinden (2026-09-17) cikan, henuz kapatilmamis maddeler. Onem sirasina gore.

## Kapatilanlar (version2)

- [x] Sync, masa/platform tablolarini `deleteAll + insert` ile kuruyordu; kullanicinin surukle-birak yerlesimi ve masa gorunumu her senkronda siliniyordu — `upsertFromServer` (2fe0579)
- [x] Masa siparisi acilirken CMD 32 iki kez gonderiliyordu — tek istek (ab1f0d1)
- [x] Senkronda dolu masa basina sirali soket aciliyordu — `Semaphore(4)` ile paralel (ab1f0d1)
- [x] `tableScaleDefault` ve `autoConnect` kaydedilip hic okunmuyordu; zoom artik kalici (304e375)
- [x] Room yikici gecisi tum surumler icin acikti — 1-9 ile sinirlandi, sema export edildi (b7cdbcc)

## Yuksek

- [ ] **Release loglarinda is verisi.** `Log.d` her katmanda kosulsuz; garson adi, masa tutarlari, urun adlari, cihaz id'si ve sunucu IP'si release build'de de logcat'e dusuyor. Timber + release'te no-op tree.
- [ ] **Main thread'de bitmap decode.** `TableFloorView.loadBackgroundImage` ve `SettingsActivity.loadFloorPlanPreview` UI thread'de `BitmapFactory.decodeFile` cagiriyor; `onResume` her donuste tekrar decode ediyor. `inSampleSize` ile view boyutuna indirge, IO dispatcher'a tasi.

## Orta

- [ ] **`restaurantName` hicbir yerde gosterilmiyor.** Once karar gerekiyor: ust bar mi, siparis dialogu basligi mi, yoksa alan tamamen kaldirilsin mi?
- [ ] **Olu kod.** `SyncService.getTableStatus` + `parseTableStatus` (ikincisi `data.contains("1")` gibi cok gevsek bir tahmin yapiyor), `MainActivity.showTableDetailsDialog`, `ServerCommand` icindeki kullanilmayan opcode'lar (23, 19, 27, 0).
- [ ] **Flow kullanilmiyor.** `MainActivity.loadData` her emit'te platform basina `getTablesByPlatformSync` cagiriyor (N+1) ve masa degisiklikleri Flow'u tetiklemedigi icin UI elle `invalidate` ediliyor. ViewModel + tek Flow kaynagi.
- [ ] **Surukleme sinirlari tutarsiz.** `onTouchEvent` `coerceIn(0f, width - table.width*scale)`, `constrainTablePositions` ise `chairMargin` payi birakiyor; kenardaki masanin sandalyeleri ekran disinda kaliyor.
- [ ] **Edit disi modda kaydirma tiklama sayiliyor.** `ACTION_MOVE` yalniz edit modda isleniyor; parmak masadan kayip kalksa bile siparis sorgusu (ve TCP istegi) tetikleniyor. `touchSlop` kontrolunu normal moda da uygula.
- [ ] **Dosya boyutlari.** `TableFloorView` 680, `MainActivity` ~590, `SyncService` ~540, `SettingsActivity` 488 satir. Cizim mantigi ayri bir renderer'a cikabilir.

## Dusuk

- [ ] **Test yok.** `ExampleUnitTest` / `ExampleInstrumentedTest` sablon halinde. En karli yer: `parseTableOrders`, `parseFullTablesAndUpdate` ve "sync sonrasi masa pozisyonu korunuyor" testi (Room in-memory).
- [ ] **Duz metin TCP.** Kasa trafigi (tutar, garson adi) port 1453'te sifresiz. Yerel ag varsayimi bilincli bir karar olarak yazilmali.
- [ ] `DeviceUtils.getDeviceSerial` — `Build.SERIAL` API 26+ her zaman `"UNKNOWN"` donuyor; device id fiilen ANDROID_ID + sabit metin. Kasa tarafi bunu bekliyorsa dokunma.
- [ ] `registerReceiver(batteryReceiver, ...)` flag'siz; `ACTION_BATTERY_CHANGED` korumali broadcast oldugu icin muaf, yine de `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` daha net.
- [ ] **README komut tablosu yanlis.** 01/02/27/28/32/39 yaziyor; `ServerCommand` enum'u 56/22/39/28/32/27/40 kullaniyor.
- [ ] Release build: `isMinifyEnabled = false`, signing config yok.
- [ ] `app/build.gradle.kts` derleme sirasinda kendiliginden `compileSdk/targetSdk 36 -> 37` oldu (Android Studio/AGP). Geri alindi; bilincli yukseltme ayri ele alinacak.

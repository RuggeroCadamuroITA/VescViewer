# Dichiarazioni permessi — Google Play Console

Documento di supporto per la compilazione della sezione **App content / Permissions declaration** e del Data Safety form. Le giustificazioni descrivono l'uso effettivo nel codice attuale.

## Italiano

| Permesso | Uso dichiarato per Google Play |
|---|---|
| `android.permission.INTERNET` | Necessario per caricare le tile OpenStreetMap quando l'utente visualizza una mappa online. L'app non usa Internet per account, analytics, sincronizzazione cloud o upload della cronologia. |
| `android.permission.ACCESS_NETWORK_STATE` | Permette di sapere se la rete è disponibile e di mostrare lo stato offline della mappa; non raccoglie né invia dati personali. |
| `android.permission.BLUETOOTH_SCAN` | Necessario su Android 12+ per cercare nelle vicinanze il modulo BLE UART del controller VESC. Lo scan è usato per trovare il dispositivo scelto dall'utente e il manifest dichiara `neverForLocation`: l'app non usa lo scan per derivare la posizione. |
| `android.permission.BLUETOOTH_CONNECT` | Necessario su Android 12+ per collegarsi al modulo BLE VESC selezionato, leggere la telemetria, ricevere notifiche GATT e leggere l'RSSI per lo stato della connessione/allarme. |
| `android.permission.BLUETOOTH` | Permesso di compatibilità per Android 11 e precedenti, dove serve per operare con il Bluetooth durante scansione e connessione BLE. |
| `android.permission.BLUETOOTH_ADMIN` | Permesso di compatibilità per Android 11 e precedenti, usato insieme al Bluetooth legacy per la scansione BLE. È limitato a `maxSdkVersion=30`. |
| `android.permission.ACCESS_FINE_LOCATION` | Necessario quando l'utente avvia la registrazione di un'uscita per ricevere punti GPS precisi e costruire la traccia del percorso, anche con app in background tramite foreground service. Non viene usato per pubblicità o profilazione. |
| `android.permission.ACCESS_COARSE_LOCATION` | Consente la registrazione della posizione approssimata quando l'utente non concede la posizione precisa. Serve per la funzione opzionale di registrazione uscita. |
| `android.permission.POST_NOTIFICATIONS` | Necessario su Android 13+ per mostrare la notifica persistente della registrazione/monitoraggio e gli avvisi di telemetria e allarme, quando l'utente concede il permesso. |
| `android.permission.FOREGROUND_SERVICE` | Permesso base richiesto per mantenere attivi in modo trasparente i servizi foreground di registrazione GPS e monitoraggio BLE/allarme mentre l'app è in background. |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | Dichiara il tipo di foreground service che registra la posizione GPS durante un'uscita avviata dall'utente. |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | Dichiara il tipo di foreground service che mantiene il monitoraggio della connessione BLE al VESC e dell'allarme RSSI mentre l'app è in background. |
| `android.permission.WAKE_LOCK` | Consente al servizio opzionale anti-allontanamento di mantenere la CPU attiva per i controlli RSSI a schermo spento. Il wake lock è limitato al funzionamento del monitoraggio e viene rilasciato alla chiusura del servizio. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Permette di aprire le impostazioni Android relative all'ottimizzazione batteria, così l'utente può valutare l'esclusione dell'app per migliorare l'affidabilità dei servizi BLE/GPS in background. La scelta resta dell'utente e l'app non forza l'esclusione. |

### Storage

L'app **non richiede** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` né permessi equivalenti di accesso a file condivisi. Database, impostazioni, cache mappe e file audio temporanei restano nello spazio privato dell'app.

### Nota sulla posizione in background

La posizione viene usata solo quando l'utente avvia la registrazione di un'uscita. Il servizio foreground mantiene la registrazione a schermo spento o mentre l'app è in background; non viene eseguito un tracciamento continuo indipendente dall'azione dell'utente.

## English

| Permission | Google Play justification |
|---|---|
| `android.permission.INTERNET` | Required to load OpenStreetMap tiles when the user views an online map. The app does not use the Internet for accounts, analytics, cloud synchronization, or ride-history uploads. |
| `android.permission.ACCESS_NETWORK_STATE` | Used to determine whether network connectivity is available and show the map's offline state; it does not collect or transmit personal data. |
| `android.permission.BLUETOOTH_SCAN` | Required on Android 12+ to discover the VESC controller's BLE UART module nearby. Scanning is used to find the device selected by the user, and the manifest declares `neverForLocation`: the app does not use Bluetooth scanning to derive location. |
| `android.permission.BLUETOOTH_CONNECT` | Required on Android 12+ to connect to the selected VESC BLE module, read telemetry, receive GATT notifications, and read RSSI for connection status and the anti-distance alarm. |
| `android.permission.BLUETOOTH` | Compatibility permission for Android 11 and older, where it is needed for Bluetooth BLE scanning and connection operations. |
| `android.permission.BLUETOOTH_ADMIN` | Compatibility permission for Android 11 and older, used with the legacy Bluetooth BLE scan flow. It is limited to `maxSdkVersion=30`. |
| `android.permission.ACCESS_FINE_LOCATION` | Required when the user starts ride recording to receive precise GPS points and build the route, including while the app is in the background through a foreground service. It is not used for advertising or profiling. |
| `android.permission.ACCESS_COARSE_LOCATION` | Allows approximate location recording when the user does not grant precise location. It supports the optional ride-recording feature. |
| `android.permission.POST_NOTIFICATIONS` | Required on Android 13+ to show the persistent recording/monitoring notification and telemetry/alarm alerts when the user grants notification access. |
| `android.permission.FOREGROUND_SERVICE` | Base permission required to keep the user-visible foreground services for GPS recording and BLE/alarm monitoring active while the app is in the background. |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | Declares the foreground-service type used to record GPS location during a ride started by the user. |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | Declares the foreground-service type used to monitor the VESC BLE connection and RSSI alarm while the app is in the background. |
| `android.permission.WAKE_LOCK` | Allows the optional anti-distance service to keep the CPU awake for RSSI checks while the screen is off. The wake lock is limited to monitoring and is released when the service stops. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Allows the app to open Android battery-optimization settings so the user can consider exempting the app to improve BLE/GPS background-service reliability. The user makes the decision and the app does not force the exemption. |

### Storage

The app does **not** request `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, or equivalent shared-file access permissions. The database, settings, map cache, and temporary audio file remain in the app-private storage area.

### Background location note

Location is used only after the user starts ride recording. The foreground service keeps that user-started recording alive while the screen is off or the app is in the background; the app does not perform continuous tracking independently of the user's action.

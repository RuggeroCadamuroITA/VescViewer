# Informativa privacy — VescViewer

**Ultimo aggiornamento:** 15 settembre 2026  
**Applicazione:** VescViewer  
**Sviluppatore:** Ruggero Cadamuro ITA  
**Contatto:** [repository GitHub di VescViewer](https://github.com/RuggeroCadamuroITA/VescViewer)

## 1. In sintesi

VescViewer è un'applicazione Android di sola lettura per visualizzare la telemetria di controller VESC tramite Bluetooth Low Energy (BLE) e, facoltativamente, registrare uscite con posizione GPS.

- Non richiede un account.
- Non gestisce un server applicativo o un servizio cloud proprietario.
- Non contiene pubblicità, analytics o tracciamento comportamentale.
- Non vende né condivide intenzionalmente con terzi la telemetria, le uscite registrate o il PIN.
- I dati delle uscite e il verificatore del PIN sono esclusi dal backup cloud Android e dal trasferimento del dispositivo secondo le regole configurate dall'app.

L'app è **read-only**: non invia comandi di controllo del motore o setpoint al VESC.

## 2. Chi tratta i dati

Il titolare del trattamento per questa applicazione è lo sviluppatore indicato sopra. Per richieste relative alla privacy è possibile utilizzare il repository GitHub pubblico di VescViewer: [github.com/RuggeroCadamuroITA/VescViewer](https://github.com/RuggeroCadamuroITA/VescViewer).

## 3. Dati trattati

### 3.1 Telemetria VESC

Quando l'app è collegata a un dispositivo VESC BLE, legge e visualizza localmente valori quali:

- tensione;
- corrente batteria e corrente motore;
- potenza e duty cycle;
- temperatura MOSFET;
- ERPM/RPM;
- contatori di Ah e Wh;
- tachimetro/distanza e altri valori inclusi nel pacchetto telemetrico.

Questi dati vengono elaborati sul dispositivo. Se l'utente avvia una registrazione, gli snapshot disponibili possono essere salvati nella cronologia locale dell'uscita.

### 3.2 Posizione e dati dell'uscita

Quando l'utente avvia la registrazione di un'uscita, l'app può salvare localmente:

- latitudine e longitudine;
- altitudine e accuratezza;
- velocità GPS;
- data e ora dei punti;
- telemetria VESC associata al punto;
- statistiche aggregate dell'uscita, come durata, distanza, velocità massima, potenza, Ah e Wh.

La posizione viene usata per costruire la traccia e mostrare la mappa dell'uscita. Non viene caricata su un server VescViewer.

### 3.3 Bluetooth e dispositivo VESC

L'app tratta localmente i dati necessari per la connessione BLE, inclusi indirizzo Bluetooth, nome del dispositivo, stato della connessione e RSSI. L'ultimo dispositivo usato può essere memorizzato nelle impostazioni per facilitare la riconnessione.

Il Bluetooth Classic/SPP non è supportato da questa versione.

### 3.4 Impostazioni e autenticazione locale

L'app salva localmente le preferenze necessarie al funzionamento, tra cui lingua, tema, unità di misura, parametri del veicolo, soglie dell'allarme, soglie degli avvisi telemetrici e ultimo dispositivo usato.

Se l'utente configura un PIN:

- il PIN in chiaro non viene salvato;
- viene salvato un verificatore derivato con PBKDF2-HMAC-SHA256 e sale casuale;
- vengono salvati anche metadati tecnici per il backoff e il blocco temporaneo dopo tentativi errati;
- il verificatore e i relativi metadati sono esclusi dal backup cloud e dal trasferimento del dispositivo.

L'app non accede né raccoglie dati biometrici: l'eventuale verifica biometrica è gestita dalle API di sistema Android.

### 3.5 Log diagnostici locali

Durante la connessione BLE l'app può scrivere messaggi tecnici nei log di sistema Android per diagnosticare connessione, pacchetti e stato GATT. Questi log non vengono inviati automaticamente a VescViewer o a un server. Un utente, un produttore del dispositivo o uno strumento di diagnostica che abbia accesso ai log di sistema potrebbe leggerli; evitare di condividere log contenenti informazioni della propria uscita.

## 4. Dove sono salvati i dati

I dati sono salvati nello spazio privato dell'app sul dispositivo:

- la cronologia delle uscite e i punti GPS sono nel database locale Room;
- le impostazioni sono in Android DataStore;
- il verificatore del PIN e i metadati di rate limiting sono in preferenze private dell'app;
- la cache delle mappe è gestita localmente da OSMDroid.

L'app non offre attualmente un servizio cloud, un account online, una sincronizzazione proprietaria o un'esportazione automatica.

## 5. Mappe e richieste di rete

Per visualizzare le mappe online, l'app usa OSMDroid e tile OpenStreetMap/il relativo servizio di tile configurato. Quando la mappa richiede tile non presenti nella cache, il dispositivo effettua richieste di rete al servizio cartografico. Come per ogni richiesta Internet, il provider di rete e il servizio remoto possono vedere dati tecnici della richiesta, come l'indirizzo IP; le tile richieste possono rivelare l'area della mappa visualizzata.

VescViewer non invia al proprio server la cronologia completa, il database, il PIN o un profilo utente. Per l'uso delle tile si applicano anche le condizioni e le informative del servizio cartografico e della rete utilizzata.

## 6. Condivisione e destinatari

VescViewer non condivide intenzionalmente con terzi:

- telemetria VESC;
- cronologia delle uscite;
- punti GPS;
- verificatore o metadati del PIN;
- impostazioni personali.

L'unico traffico verso un servizio terzo previsto dal funzionamento è il caricamento delle tile cartografiche quando l'utente visualizza una mappa online. L'app non contiene analytics, advertising SDK, account backend o strumenti di profilazione.

## 7. Backup e trasferimento del dispositivo

Le regole Android dell'app escludono esplicitamente dal backup cloud e dal trasferimento del dispositivo:

- il database locale delle uscite, inclusi punti GPS e telemetria;
- il file di preferenze contenente il verificatore e i metadati del PIN.

Le impostazioni non sensibili, come preferenze di visualizzazione e configurazione, possono restare trasferibili secondo il comportamento Android e le impostazioni del dispositivo. Il comportamento effettivo può dipendere dalla versione Android e dal produttore; deve essere verificato prima della pubblicazione sui dispositivi supportati.

## 8. Conservazione e cancellazione

I dati locali restano sul dispositivo finché l'utente non cancella i dati dell'app, disinstalla l'app o utilizza un'eventuale funzione di cancellazione resa disponibile in una versione futura. Questa versione non offre ancora un flusso completo di esportazione o cancellazione delle singole uscite dall'interfaccia utente.

I dati presenti nella cache cartografica possono essere eliminati tramite la gestione dati/cache del sistema o dell'app, secondo il comportamento di Android e OSMDroid.

## 9. Sicurezza

L'app usa lo spazio privato Android, non salva il PIN in chiaro e applica PBKDF2 con sale casuale e limitazione dei tentativi. Il verificatore attuale non è dichiarato come device-bound tramite Android Keystore.

Nessuna misura software può garantire sicurezza assoluta. L'utente deve proteggere il dispositivo, usare un blocco schermo affidabile e non condividere il PIN o log diagnostici.

## 10. Minori

VescViewer non è rivolto specificamente a minori e non raccoglie consapevolmente dati personali tramite un servizio online. L'applicazione è destinata all'uso da parte del proprietario o dell'utilizzatore autorizzato del dispositivo e del veicolo.

## 11. Modifiche a questa informativa

Questa informativa può essere aggiornata quando cambiano il funzionamento dell'app, le richieste di rete, le regole di backup o i requisiti normativi. La data all'inizio del documento indica l'ultima revisione.

## 12. Contatti

Per domande o richieste relative ai dati trattati, contatta lo sviluppatore tramite il repository GitHub di VescViewer:

[https://github.com/RuggeroCadamuroITA/VescViewer](https://github.com/RuggeroCadamuroITA/VescViewer)

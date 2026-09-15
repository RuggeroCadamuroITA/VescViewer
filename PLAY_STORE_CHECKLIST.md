# Checklist pubblicazione Google Play — attività manuali

Questa checklist contiene solo attività da completare manualmente in Google Play Console o con materiali di pubblicazione. Non sostituisce `RELEASE_VALIDATION_CHECKLIST.md`, che riguarda la validazione tecnica su dispositivo reale.

## Account e app

- [ ] Avere un account Google Play Developer verificato e in regola.
- [ ] Completare la verifica dell'identità e dei dati di contatto dello sviluppatore.
- [ ] Creare l'app in Play Console con il nome pubblico **VescViewer**.
- [ ] Confermare il package name `com.ruggerocadamuro.myapplication`.
- [ ] Verificare che l'account e il profilo sviluppatore mostrino i dati di contatto richiesti da Google Play.
- [ ] Accettare gli accordi e le dichiarazioni richieste per la distribuzione.

## Build e firma

- [ ] Configurare un keystore di release custodito fuori dal repository.
- [ ] Conservare in modo sicuro keystore, alias e password.
- [ ] Generare un APK o, preferibilmente, un Android App Bundle (`.aab`) firmato per la release.
- [ ] Verificare che la firma sia stabile tra gli aggiornamenti futuri.
- [ ] Incrementare `versionCode` per ogni nuova pubblicazione.
- [ ] Impostare e verificare `versionName`/numero versione mostrato agli utenti.
- [ ] Conservare il mapping R8 e gli artefatti di build per eventuali crash report.
- [ ] Caricare il bundle nel track corretto senza pubblicarlo accidentalmente prima della revisione.

## Scheda dello Store

- [ ] Inserire il nome breve dell'app.
- [ ] Scrivere una descrizione breve entro il limite imposto da Play Console.
- [ ] Scrivere la descrizione completa, spiegando BLE-only, telemetria read-only, registrazione GPS e allarme RSSI.
- [ ] Dichiarare chiaramente che Bluetooth Classic/SPP non è supportato.
- [ ] Indicare il dispositivo principalmente supportato: Flipsky FSESC 75200 Pro con modulo BLE.
- [ ] Descrivere le limitazioni: l'allarme RSSI non è un sistema antifurto garantito e la percentuale batteria è una stima basata sulla tensione.
- [ ] Scegliere la categoria più adatta, ad esempio strumenti/utilità, in base alle opzioni disponibili.
- [ ] Inserire eventuali tag o parole chiave consentiti senza fare affermazioni non dimostrate.
- [ ] Inserire l'email di contatto per gli utenti.
- [ ] Inserire l'URL della privacy policy pubblicata su HTTPS, ad esempio una pagina GitHub Pages o una pagina del repository.
- [ ] Inserire eventuale URL del sito web/supporto.
- [ ] Aggiungere il link al repository o alla pagina di supporto, se desiderato.

## Asset grafici

- [ ] Preparare l'icona applicazione richiesta da Play Console nel formato e nelle dimensioni richieste.
- [ ] Verificare icona adattiva, icona rotonda e resa su sfondo chiaro/scuro.
- [ ] Preparare almeno due screenshot reali dell'app su dispositivi supportati.
- [ ] Preparare screenshot che mostrino dashboard telemetria, scanner BLE, registrazione/mappe e impostazioni.
- [ ] Verificare che gli screenshot non contengano indirizzi Bluetooth, coordinate GPS, PIN, dati personali o hardware non autorizzato.
- [ ] Preparare eventuale feature graphic richiesta da Play Console.
- [ ] Verificare che immagini, loghi, font e testi abbiano diritti di utilizzo compatibili con la pubblicazione.
- [ ] Localizzare gli asset e la descrizione per le lingue che si intendono supportare.

## App content e dichiarazioni

- [ ] Compilare la sezione **Data safety** usando `PRIVACY_POLICY.md` come fonte.
- [ ] Dichiarare che la telemetria VESC e i dati delle uscite restano sul dispositivo e non sono raccolti da un server VescViewer.
- [ ] Dichiarare correttamente che i dati di posizione e attività possono essere salvati localmente quando l'utente avvia una registrazione.
- [ ] Dichiarare l'uso delle tile cartografiche online e il relativo trasferimento tecnico al servizio di mappe quando necessario.
- [ ] Dichiarare che non sono presenti advertising SDK, analytics o account obbligatori.
- [ ] Completare la dichiarazione dei permessi Bluetooth, posizione, notifiche e foreground service usando `PLAY_PERMISSIONS.md`.
- [ ] Verificare se Play Console richiede una dichiarazione aggiuntiva per l'uso della posizione in background.
- [ ] Spiegare che la posizione in background è usata solo durante una registrazione iniziata dall'utente.
- [ ] Completare il questionario **Content rating**.
- [ ] Indicare correttamente assenza di contenuti violenti, sessuali, gioco d'azzardo, droga o contenuti generati dagli utenti, se applicabile al questionario.
- [ ] Completare il questionario **Target audience and content** e la fascia d'età.
- [ ] Dichiarare se l'app è rivolta a minori o no secondo il pubblico effettivo desiderato.
- [ ] Verificare la sezione **Ads** e indicare correttamente che l'app non contiene annunci, se confermato.
- [ ] Compilare eventuali moduli su funzionalità sensibili richiesti da Play Console.
- [ ] Accettare le dichiarazioni su privacy, sicurezza e contenuti solo dopo averle confrontate con il comportamento reale dell'app.

## Distribuzione e test su Play Console

- [ ] Scegliere i paesi/regioni di distribuzione.
- [ ] Scegliere i dispositivi e i fattori di forma supportati.
- [ ] Controllare che il requisito target API sia accettato da Play Console: il progetto usa target API 36.
- [ ] Pubblicare prima su internal testing.
- [ ] Aggiungere tester autorizzati e distribuire il bundle di test.
- [ ] Raccogliere feedback e crash report dal test interno.
- [ ] Verificare installazione, aggiornamento e disinstallazione dal track interno.
- [ ] Passare eventualmente a closed testing secondo i requisiti dell'account e della policy vigente.
- [ ] Compilare eventuali requisiti di testing obbligatori mostrati da Play Console per nuovi account personali.
- [ ] Controllare la pagina **Pre-launch report** e risolvere gli errori bloccanti o spiegare i falsi positivi.
- [ ] Controllare Android vitals dopo il test interno.
- [ ] Impostare rollout graduale se appropriato.
- [ ] Inviare la release alla revisione solo dopo aver completato tutte le dichiarazioni.
- [ ] Verificare che lo stato finale sia **Published** dopo l'approvazione, non solo **Draft** o **In review**.

## Dopo la pubblicazione

- [ ] Monitorare crash, ANR, Android vitals e recensioni.
- [ ] Pubblicare una risposta/supporto per i problemi di connessione BLE più comuni.
- [ ] Aggiornare privacy policy e Data safety se cambiano raccolta, rete, backup o condivisione dati.
- [ ] Conservare una copia della release pubblicata, del bundle e del mapping R8.
- [ ] Annotare versione, data di pubblicazione e note di rilascio.
- [ ] Preparare una procedura per correggere rapidamente una release problematica.

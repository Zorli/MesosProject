# Mesos Project
Progetto prova finale per l'esame di Ingegneria del Software.

## Workflow Git 
In questo repository usiamo 3 tipi di branch:

- `main`: contiene la versione stabile.
- `develop`: branch di integrazione, dove finiscono le feature completate.
- `feat/<nome-feature>`: branch temporanei per sviluppo di una singola funzionalita (anche per bug-fix).

Regole base:

- Non si lavora direttamente su `main`.
- Le feature vengono mergeate su `develop` tramite Merge Request.
- `main` si aggiorna solo quando vogliamo rilasciare una versione stabile.

## Flusso di lavoro 
1. Aggiorna `develop` e crea una feature branch

2. Sviluppa, fai commit piccoli e chiari, poi push

3. Apri una Pull Request da `feature/nome-feature` verso `develop`.

4. Dopo review e merge su `develop`


## Documentazione tecnica (`docs/`)
La cartella `docs/` e` dedicata ai design doc e alla documentazione delle decisioni tecniche.

## Avvio MySQL locale
Per sviluppo locale il repository include un `docker-compose.yml` con MySQL.
Il database viene usato per ranking e storico risultati; il salvataggio dello stato match non e'
gestito da MySQL.

1. Avvia il database:
```bash
docker compose up -d mysql
```

2. Configura le variabili ambiente del server. Puoi partire da `.env.server.example`.

Variabili supportate:
- `MESOS_DB_URL`
- `MESOS_DB_ADMIN_URL`
- `MESOS_DB_USER`
- `MESOS_DB_PASSWORD`
- `MESOS_DB_MAX_BUSY_RETRIES`
- `MESOS_NO_DB`

Default locali:
- `jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false`
- admin DB `jdbc:mysql://localhost:3306/mysql?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false`
- utente `root`
- password `root`

3. Avvia il server:
```bash
MESOS_DB_URL='jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
MESOS_DB_ADMIN_URL='jdbc:mysql://localhost:3306/mysql?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
MESOS_DB_USER=root \
MESOS_DB_PASSWORD=root \
./mvnw -q -Dexec.mainClass=it.polimi.ingsoft.mesos.server.LaunchServer exec:java
```

Per avvio naive senza MySQL:
```bash
MESOS_NO_DB=true ./mvnw -q -Dexec.mainClass=it.polimi.ingsoft.mesos.server.LaunchServer exec:java
```

All'avvio il server crea il database se manca, inizializza lo schema ranking, verifica la connettivita' verso MySQL e fallisce con un errore esplicito se il DB e' irraggiungibile o non healthy.

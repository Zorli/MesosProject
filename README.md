# Mesos Project
Progetto prova finale per l'esame di Ingegneria del Software.

## Workflow Git 
In questo repository usiamo 3 tipi di branch:

- `main`: contiene la versione stabile.
- `develop`: branch di integrazione, dove finiscono le feature completate.
- `feature/<nome-feature>`: branch temporanei per sviluppo di una singola funzionalita (anche per bug-fix).

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


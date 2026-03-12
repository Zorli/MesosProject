# Design Doc - Game State

## Contesto

### Package di riferimento
`it.polimi.ingsoft.mesos.server.model.game`

### Obiettivo del design doc
Descrivere il package `game` come contenitore dello stato della partita, chiarendo:
- quali classi contiene
- quali responsabilita' assegna a ciascuna classe
- con quali componenti si interfaccia
- quali decisioni di design porta a casa

## Problema che il package risolve

### Quale parte del dominio modella

### Quale stato della partita deve rappresentare

### Perche' serve un package dedicato

## Responsabilita' del package

### Di cosa si occupa

### Di cosa non si occupa

### Confini del package

## Vista d'insieme

### Classi presenti nel package
- `Game`
- Altre classi del package da aggiungere qui

### Relazioni principali tra le classi

### Collaboratori esterni principali
- `Player`
- `Era`
- `GameStatus`
- Orchestratori / controller
- Carte, effetti, building o altri componenti del modello

## Analisi classe per classe

### `Game`

#### Responsabilita' principali

#### Stato che possiede

#### Operazioni che espone

#### Collaboratori con cui si interfaccia

#### Invarianti o regole che deve mantenere

#### Cosa non dovrebbe fare

### `NomeClasse`

#### Responsabilita' principali

#### Stato che possiede

#### Operazioni che espone

#### Collaboratori con cui si interfaccia

#### Invarianti o regole che deve mantenere

#### Cosa non dovrebbe fare

## Interazioni con il resto del sistema

### Rapporto con game orchestrator / controller

### Rapporto con i player

### Rapporto con carte evento, effetti e building

### Rapporto con enum e value object di supporto

## Responsabilita' distribuite

### Cosa decide il package `game`

### Cosa delega ad altri package

### Perche' questa suddivisione e' utile

## Flussi principali supportati

### Creazione dello stato iniziale della partita

### Aggiornamento dello stato durante il turno

### Lettura dello stato da parte di componenti esterni

### Transizioni di stato rilevanti

## Decisioni di design

### Scelte strutturali adottate

### Trade-off accettati

### Alternative possibili o scartate

## Dubbi aperti

### Classi mancanti o da spostare

### Responsabilita' ancora poco chiare

### Evoluzioni future del package

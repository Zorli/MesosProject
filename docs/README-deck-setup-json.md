# Deck Setup JSON

## Obiettivo

`deckSetup.json` e' la sorgente dati statica usata dal server per costruire i mazzi del gioco.

Il file descrive:
- character cards
- event cards
- building cards

L'obiettivo non e' rappresentare la logica di gioco nel JSON, ma fornire un formato stabile e verificabile che possa essere tradotto nelle classi del model tramite `ParserJson` e conservato in memoria tramite `DeckTemplate`.

## Perche' questa struttura

La struttura precedente mischiava:
- dati di dominio necessari al server
- dettagli visuali utili per validare il contenuto rispetto agli asset

Questo rendeva il parsing piu' fragile e i test meno chiari.

Per questo il file e' stato riorganizzato in due livelli:
- `model`: dati necessari per istanziare le classi Java
- `presentation`: metadati descrittivi presi dalle carte, utili per review, debugging e validazione futura

In questo modo:
- il parser legge solo il contratto di dominio
- i dettagli grafici restano disponibili senza condizionare la logica server

## Struttura ad alto livello

Il file si trova in:
- `src/main/resources/it/polimi/ingsoft/mesos/server/model/Factory/deckSetup.json`

Schema top-level:

```json
{
  "schemaVersion": 2,
  "source": { ... },
  "cards": {
    "characters": { ... },
    "events": { ... },
    "buildings": { ... }
  }
}
```

### `source`

Contiene metadati sulla provenienza dei dati:
- PDF front/back usati come riferimento
- numero di carte realmente incluse nel deck
- asset esclusi dal deck ma presenti negli export

Esempio:

```json
{
  "includedCardCount": 117,
  "excludedAssetIds": [118, 119, 120, 121, 122]
}
```

Gli asset `118..122` non sono building cards del mazzo: sono carte reference duplicate e per questo non vengono lette dal parser.

### `cards.characters`

`cards.characters` non e' un array piatto. E' un oggetto raggruppato per `TribeCardType`, ad esempio:
- `HUNTER`
- `BUILDER`
- `GATHERER`
- `ARTIST`
- `INVENTOR`
- `SHAMAN`

Ogni chiave contiene l'array delle sole carte di quel tipo.

Ogni character card contiene:
- `id`
- `era`
- `minPlayers` opzionale
- `model`
- `presentation`

Esempio:

```json
{
  "HUNTER": [
    {
      "id": 1,
      "era": "first",
      "minPlayers": null,
      "model": {
        "hasEffect": true
      },
      "presentation": {
        "front": { ... },
        "back": { ... }
      }
    }
  ]
}
```

Il tipo non viene ripetuto nel record, perche' e' gia' espresso dalla chiave del gruppo. Questo evita duplicazione di dati e riduce il lavoro del parser.

`minPlayers` viene omesso quando la carta e' valida per qualsiasi partita da `2` a `5` giocatori.

Nel blocco `model` ci sono solo i dati che servono davvero alle classi:
- `Hunter -> hasEffect`
- `Builder -> priceDiscount`, `prestigePoints`
- `Inventor -> inventionType`
- `Shaman -> stars`

Per `GATHERER` e `ARTIST`, che non hanno payload specifico nel model attuale, il blocco `model` viene omesso del tutto.

### `cards.events`

`cards.events` e' raggruppato per famiglia evento:
- `HUNT`
- `SUSTENANCE`
- `CAVE_PAINTING`
- `SHAMAN_RITUAL`

Ogni event card contiene:
- `id`
- `era`
- `model`
- `presentation`

Esempio:

```json
{
  "HUNT": [
    {
      "id": 85,
      "era": "first",
      "model": {
        "foodReward": 1,
        "prestigePointsReward": 1
      }
    }
  ]
}
```

Anche qui la famiglia non viene ripetuta in `model`, perche' e' gia' nota dal gruppo.

### `cards.buildings`

`cards.buildings` e' raggruppato per `BenefitType`, ad esempio:
- `POST_DRAW_SET_FOR_FOOD`
- `EVENT_BONUS_HUNTER`
- `FINAL_BONUS_BIG_PP_REWARD`

Ogni building card contiene:
- `id`
- `era`
- `model`
- `presentation`

Nel blocco `model` i campi comuni sono:
- `foodCost`
- `prestigePoints`
- `timing`

`timing` serve a rappresentare quando il benefit si applica:
- `POST_DRAW`
- `EVENT`
- `END_ROUND`
- `END_GAME`

Il marker grafico `>|` in basso a sinistra e' stato normalizzato come `timing: "END_GAME"`.

Esempio:

```json
{
  "POST_DRAW_SET_FOR_FOOD": [
    {
      "id": 97,
      "era": "first",
      "model": {
        "timing": "POST_DRAW",
        "requiredDistinctTypes": 6,
        "rewardFood": 5,
        "foodCost": 4,
        "prestigePoints": 3
      }
    }
  }
}
```

Anche qui `benefitType` non e' ripetuto nel record, perche' coincide con la chiave del gruppo.

## Come viene letto dal server

La lettura avviene tramite:
- `it.polimi.ingsoft.mesos.server.Factory.ParserJson`
- `it.polimi.ingsoft.mesos.server.Factory.DeckTemplate`

`ParserJson`:
- carica il file da `resources`
- lo legge una sola volta per processo
- costruisce una singola istanza di `DeckTemplate`
- usa la chiave del gruppo JSON come discriminante di mapping
- espone il template tramite `getDeckTemplate()`

`DeckTemplate`:
- contiene blueprint immutabili delle carte
- sa costruire il `TribeDeck` corretto per `2`, `3`, `4`, `5` giocatori
- sa costruire un nuovo `BuildingDeck` per ogni partita
- crea sempre nuove istanze delle carte, cosi' nessuna partita condivide stato mutabile

Questo e' importante:
- il file JSON e' statico, quindi ha senso leggerlo una sola volta
- i deck invece sono mutabili e vengono pescati/shufflati, quindi non vanno condivisi come singleton
- alcune carte building hanno stato interno mutabile, quindi e' meglio generare nuove istanze invece di riusare le stesse

Per questo il singleton/cache va applicato al template immutabile, non alle istanze di `TribeDeck` e `BuildingDeck`.

## Perche' raggruppare per sottotipo

La scelta di usare gruppi come:
- `cards.characters.HUNTER`
- `cards.events.HUNT`
- `cards.buildings.EVENT_BONUS_HUNTER`

serve a togliere ridondanza dal file e complessita' dal parser.

Senza questa struttura ogni record dovrebbe ripetere il suo discriminante:
- `type`
- `family`
- `benefitType`

Questo crea due problemi:
- il file puo' diventare internamente incoerente
- il parser deve validare e riconciliare informazioni duplicate

Con i gruppi:
- la fonte del discriminante e' unica
- i record diventano piu' piccoli e meno rumorosi
- `ParserJson` puo' iterare gruppo per gruppo e istanziare direttamente la classe corretta
- i test possono verificare piu' facilmente che non ci siano campi ridondanti

## Scelta progettuale sul singleton

Non conviene tenere in singleton:
- `TribeDeck`
- `BuildingDeck`

perche':
- vengono mescolati (`shuffle`)
- vengono consumati (`draw`)
- appartengono a una singola partita

Conviene invece tenere in cache:
- il template immutabile costruito dal JSON

ed e' quello che fa `ParserJson` con lazy initialization del `DeckTemplate`.

## Perche' non fare una shallow copy delle carte

Una semplice copia superficiale delle istanze non basta, perche' alcune carte hanno stato interno mutabile.

Esempi:
- `SetForFood` mantiene una mappa dei tipi gia' visti
- `PairOfInventor` mantiene una mappa degli accoppiamenti gia' accumulati

Per questo il template non conserva oggetti carta da riusare, ma blueprint/factory che costruiscono una nuova carta ogni volta.

## Limiti attuali

Non tutti i `BenefitType` hanno oggi una sottoclasse dedicata nel model.

Per questo il parser:
- usa la sottoclasse concreta quando esiste
- altrimenti crea un `BuildingCard` base con `benefitType`, `foodCost` e `prestigePoints`

Questo mantiene il deck consistente senza inventare logica che il model non supporta ancora.

## Test

La suite isolata si trova in:
- `tests/tribe_building`

Copre:
- validita' JSON
- struttura del file
- copertura degli asset
- mapping reale `ParserJson/DeckTemplate -> classi model`
- deck corretto per `2/3/4/5` giocatori
- creazione di istanze fresche a ogni nuova partita
- rifiuto dei player count non validi

Comando:

```bash
cd tests/tribe_building
mvn -q test
```

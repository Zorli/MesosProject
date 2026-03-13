# Design doc

Nota : In fase di setup e waiting in lobby il player parla con il game controller, dopodiche durante l'intero flusso di gioco
il player deve parlare con il solo game orchestrator, altrimenti diventa un bottleneck il game controller.
Game Orchestrator come entry point di tutto il gioco.

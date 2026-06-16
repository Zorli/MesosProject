#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

CONTAINER_NAME="${MESOS_DB_CONTAINER:-mesos-db}"
DATABASE_NAME="${MESOS_DB_NAME:-mesos}"
DATABASE_USER="${MESOS_DB_USER:-mesos}"
DATABASE_PASSWORD="${MESOS_DB_PASSWORD:-mesos}"
MATCHES_PER_PLAYER_COUNT="${1:-8}"

if [[ "$MATCHES_PER_PLAYER_COUNT" == "-h" || "$MATCHES_PER_PLAYER_COUNT" == "--help" ]]; then
    echo "Usage: $0 [matches-per-player-count]"
    echo
    echo "Inserts deterministic mock ranking data for 2, 3, 4 and 5 player games."
    echo "Default matches-per-player-count: 8"
    echo
    echo "Environment overrides:"
    echo "  MESOS_DB_CONTAINER  default: mesos-db"
    echo "  MESOS_DB_NAME       default: mesos"
    echo "  MESOS_DB_USER       default: mesos"
    echo "  MESOS_DB_PASSWORD   default: mesos"
    exit 0
fi

if ! [[ "$MATCHES_PER_PLAYER_COUNT" =~ ^[0-9]+$ ]] || [[ "$MATCHES_PER_PLAYER_COUNT" -lt 1 ]]; then
    echo "matches-per-player-count must be a positive integer." >&2
    exit 1
fi

SQL_FILE="$(mktemp)"
trap 'rm -f "$SQL_FILE"' EXIT

{
    echo "START TRANSACTION;"
    echo

    for player_count in 2 3 4 5; do
        for match_number in $(seq 1 "$MATCHES_PER_PLAYER_COUNT"); do
            day=$(printf "%02d" "$match_number")
            minute=$(printf "%02d" "$((match_number % 60))")

            echo "INSERT INTO partite (data, num_giocatori)"
            echo "VALUES ('2026-06-${day} 20:${minute}:00.000', ${player_count});"
            echo "SET @mock_game_id = LAST_INSERT_ID();"
            echo

            for player_index in $(seq 1 "$player_count"); do
                nickname=$(printf "Mock%dP%02d_%d" "$player_count" "$match_number" "$player_index")
                score=$((50 + player_count * 4 + (MATCHES_PER_PLAYER_COUNT - match_number) * 3 + player_index * 2))

                echo "INSERT INTO utenti (nickname)"
                echo "VALUES ('${nickname}')"
                echo "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);"
                echo "SET @mock_user_id = LAST_INSERT_ID();"
                echo "INSERT INTO partecipazioni (id_partita, id_utente, punteggio)"
                echo "VALUES (@mock_game_id, @mock_user_id, ${score});"
                echo
            done
        done
    done

    echo "COMMIT;"
} > "$SQL_FILE"

docker exec -i "$CONTAINER_NAME" mysql \
    "-u${DATABASE_USER}" \
    "-p${DATABASE_PASSWORD}" \
    "$DATABASE_NAME" < "$SQL_FILE"

echo "Inserted mock ranking data: ${MATCHES_PER_PLAYER_COUNT} matches for each player count from 2 to 5."

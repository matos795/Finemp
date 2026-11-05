#!/bin/sh
set -eu

reject() {
    echo "Frontend staging configuration rejected: $1" >&2
    exit 1
}

case "${PORT:-}" in
    ""|*[!0-9]*) reject "PORT" ;;
esac

case "${FINEMP_API_UPSTREAM:-}" in
    http://*) ;;
    *) reject "FINEMP_API_UPSTREAM" ;;
esac

upstream="${FINEMP_API_UPSTREAM#http://}"
host="${upstream%:*}"
port="${upstream##*:}"

case "$host" in
    *.railway.internal) ;;
    *) reject "FINEMP_API_UPSTREAM" ;;
esac

case "$host" in
    *"@"*) reject "FINEMP_API_UPSTREAM" ;;
esac

case "$port" in
    ""|*[!0-9]*) reject "FINEMP_API_UPSTREAM" ;;
esac

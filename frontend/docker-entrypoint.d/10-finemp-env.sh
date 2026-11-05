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

resolver="$(awk '$1 == "nameserver" { print $2; exit }' /etc/resolv.conf)"

case "$resolver" in
    "") reject "DNS_RESOLVER" ;;
    *[!0-9A-Fa-f:.%]*) reject "DNS_RESOLVER" ;;
esac

case "$resolver" in
    *:*) resolver="[$resolver]" ;;
esac

# O entrypoint oficial executa este script antes do envsubst. Como scripts
# executáveis rodam em subprocesso, gravamos host/porta/resolver diretamente no
# template. Todos já foram validados para não conter sintaxe arbitrária.
sed -i \
    -e "s|__FINEMP_API_HOST__|$host|g" \
    -e "s|__FINEMP_API_PORT__|$port|g" \
    -e "s|__FINEMP_DNS_RESOLVER__|$resolver|g" \
    /etc/nginx/templates/default.conf.template

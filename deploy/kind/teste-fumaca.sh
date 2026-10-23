#!/usr/bin/env bash
# Teste de fumaça no cluster: consulta GraphQL pelo Service e confere a métrica do outbox.
set -euo pipefail
kubectl port-forward svc/ci-agendamento 18080:80 >/tmp/pf.log 2>&1 &
trap 'kill %1' EXIT
for i in $(seq 1 30); do curl -sf localhost:18080/actuator/health/readiness >/dev/null && break; sleep 2; done

resposta=$(curl -sf localhost:18080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{ unidades { nome servicos { nome } } }"}')
echo "$resposta"
grep -q "Central de Atendimento Centro" <<<"$resposta"
metricas=$(curl -sf localhost:18080/actuator/prometheus)
grep -q "outbox_pendentes" <<<"$metricas"
echo "OK: API respondendo no cluster"

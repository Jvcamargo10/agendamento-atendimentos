import { Component, computed, inject } from '@angular/core';
import { I18n } from '../i18n';

const CONTEUDO = {
  pt: {
    intro: 'A demo roda no navegador. O sistema completo, no repositório, tem estas peças:',
    blocos: [
      ['API GraphQL (Java 21 + Spring Boot)', 'Spring for GraphQL com @BatchMapping para evitar o problema N+1. Threads virtuais, validação e erros de negócio como BAD_REQUEST.'],
      ['PostgreSQL + Flyway', 'A lotação é garantida por um UPDATE condicional atômico; "um agendamento por dia" por um índice único parcial. O CPF é guardado como HMAC.'],
      ['Kafka', 'Outbox transacional publica os eventos; o consumidor do painel é idempotente e mensagens com erro vão para um tópico .dlt.'],
      ['Kubernetes + Helm', 'Chart com probes de startup/liveness/readiness, HPA, PodDisruptionBudget e rolling update sem indisponibilidade. O CI sobe tudo num cluster kind.'],
      ['Observabilidade', 'Actuator + Micrometer (Prometheus), métrica de eventos pendentes na outbox e logs estruturados em JSON (ECS).'],
      ['Testes', 'Testcontainers com PostgreSQL e Kafka reais, incluindo 10 pedidos simultâneos para um horário de 2 vagas.'],
    ],
  },
  en: {
    intro: 'This demo runs in the browser. The full system in the repository is made of:',
    blocos: [
      ['GraphQL API (Java 21 + Spring Boot)', 'Spring for GraphQL with @BatchMapping to avoid N+1 queries. Virtual threads, validation, and business errors as BAD_REQUEST.'],
      ['PostgreSQL + Flyway', 'Capacity is enforced by an atomic conditional UPDATE; "one booking per day" by a partial unique index. CPF is stored as an HMAC.'],
      ['Kafka', 'A transactional outbox publishes events; the dashboard consumer is idempotent and failing messages go to a .dlt topic.'],
      ['Kubernetes + Helm', 'Chart with startup/liveness/readiness probes, HPA, PodDisruptionBudget and zero-downtime rolling updates. CI deploys it to a kind cluster.'],
      ['Observability', 'Actuator + Micrometer (Prometheus), an outbox backlog metric and structured JSON logs (ECS).'],
      ['Tests', 'Testcontainers with real PostgreSQL and Kafka, including 10 concurrent requests for a 2-seat slot.'],
    ],
  },
};

@Component({
  selector: 'app-arquitetura',
  template: `
    <p class="dica">{{ c().intro }}</p>
    <div class="blocos">
      @for (b of c().blocos; track b[0]) { <section class="cartao"><h3>{{ b[0] }}</h3><p>{{ b[1] }}</p></section> }
    </div>
  `,
})
export class Arquitetura {
  private readonly i18n = inject(I18n);
  protected readonly c = computed(() => CONTEUDO[this.i18n.idioma()]);
}

-- Esquema do agendamento de atendimentos. As decisões estão explicadas no README.

CREATE TABLE unidades (
  id       int PRIMARY KEY,
  nome     text NOT NULL,
  endereco text NOT NULL,
  cidade   text NOT NULL,
  uf       char(2) NOT NULL
);

CREATE TABLE servicos (
  id               int PRIMARY KEY,
  nome             text NOT NULL,
  duracao_minutos  int  NOT NULL CHECK (duracao_minutos > 0)
);

-- Quantos guichês de cada serviço cada unidade tem: é a capacidade de cada horário.
CREATE TABLE unidade_servicos (
  unidade_id int NOT NULL REFERENCES unidades (id),
  servico_id int NOT NULL REFERENCES servicos (id),
  guiches    int NOT NULL CHECK (guiches > 0),
  PRIMARY KEY (unidade_id, servico_id)
);

-- Um horário de atendimento. A regra de lotação fica no banco: ocupadas nunca passa da capacidade.
CREATE TABLE vagas (
  id          bigserial PRIMARY KEY,
  unidade_id  int         NOT NULL,
  servico_id  int         NOT NULL,
  inicio      timestamptz NOT NULL,
  capacidade  int         NOT NULL,
  ocupadas    int         NOT NULL DEFAULT 0,
  CONSTRAINT vagas_lotacao CHECK (ocupadas BETWEEN 0 AND capacidade),
  CONSTRAINT vagas_unicas UNIQUE (unidade_id, servico_id, inicio),
  FOREIGN KEY (unidade_id, servico_id) REFERENCES unidade_servicos (unidade_id, servico_id)
);

CREATE TABLE agendamentos (
  id            bigserial PRIMARY KEY,
  codigo        text        NOT NULL UNIQUE,
  vaga_id       bigint      NOT NULL REFERENCES vagas (id),
  dia           date        NOT NULL,
  cidadao_nome  text        NOT NULL,
  -- O CPF não é guardado: só um hash (para a regra de um agendamento por dia) e os 3 últimos dígitos.
  cpf_hash      text        NOT NULL,
  cpf_final     char(3)     NOT NULL,
  email         text        NOT NULL,
  status        text        NOT NULL CHECK (status IN ('AGENDADO', 'CANCELADO', 'ATENDIDO', 'FALTOU')),
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now()
);

-- Um cidadão só pode ter um agendamento ativo por dia. Índice único parcial: cancelados não contam.
CREATE UNIQUE INDEX agendamentos_um_por_dia ON agendamentos (cpf_hash, dia) WHERE status = 'AGENDADO';
CREATE INDEX agendamentos_vaga ON agendamentos (vaga_id);

-- Outbox: o evento é gravado na mesma transação da mudança e publicado no Kafka depois.
CREATE TABLE outbox (
  id           uuid PRIMARY KEY,
  topico       text        NOT NULL,
  chave        text        NOT NULL,
  tipo         text        NOT NULL,
  payload      jsonb       NOT NULL,
  criado_em    timestamptz NOT NULL DEFAULT now(),
  publicado_em timestamptz
);
CREATE INDEX outbox_pendentes ON outbox (criado_em) WHERE publicado_em IS NULL;

-- Modelo de leitura do painel, mantido pelo consumidor Kafka.
CREATE TABLE painel_diario (
  unidade_id int  NOT NULL,
  dia        date NOT NULL,
  agendados  int  NOT NULL DEFAULT 0,
  cancelados int  NOT NULL DEFAULT 0,
  atendidos  int  NOT NULL DEFAULT 0,
  faltas     int  NOT NULL DEFAULT 0,
  PRIMARY KEY (unidade_id, dia)
);

-- Idempotência do consumidor: um evento só é aplicado uma vez, mesmo se o Kafka entregar de novo.
CREATE TABLE eventos_processados (
  consumidor   text        NOT NULL,
  evento_id    uuid        NOT NULL,
  processado_em timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumidor, evento_id)
);

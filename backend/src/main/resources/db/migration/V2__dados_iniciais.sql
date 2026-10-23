-- Unidades e serviços fictícios. Os horários (vagas) são criados pelo GeradorDeVagas.
INSERT INTO unidades (id, nome, endereco, cidade, uf) VALUES
  (1, 'Central de Atendimento Centro',   'Praça da Matriz, 100',     'Vale Azul', 'SP'),
  (2, 'Central de Atendimento Norte',    'Av. das Palmeiras, 2.450', 'Vale Azul', 'SP'),
  (3, 'Posto Avançado Jardim Esperança', 'Rua dos Ipês, 87',         'Vale Azul', 'SP');

INSERT INTO servicos (id, nome, duracao_minutos) VALUES
  (1, 'Emissão de RG',                30),
  (2, 'Cadastro no CadÚnico',         30),
  (3, 'Alvará de funcionamento',      30),
  (4, 'Segunda via de IPTU',          30);

INSERT INTO unidade_servicos (unidade_id, servico_id, guiches) VALUES
  (1, 1, 3), (1, 2, 2), (1, 3, 2), (1, 4, 2),
  (2, 1, 2), (2, 2, 2), (2, 4, 1),
  (3, 2, 1), (3, 4, 1);

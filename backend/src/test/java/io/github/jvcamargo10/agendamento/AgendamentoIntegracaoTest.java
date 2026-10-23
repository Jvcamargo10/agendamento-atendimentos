package io.github.jvcamargo10.agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.jvcamargo10.agendamento.agenda.AgendamentoServico;
import io.github.jvcamargo10.agendamento.agenda.Modelos.NovoAgendamento;
import io.github.jvcamargo10.agendamento.dominio.RegraDeNegocioException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Testes de ponta a ponta: GraphQL → serviço → PostgreSQL → outbox → Kafka → consumidor → painel.
 * O relógio está fixo em segunda-feira, 04/03/2030, 07h (ver ContainersDeTeste).
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(ContainersDeTeste.class)
class AgendamentoIntegracaoTest {

    private static final AtomicInteger CPFS = new AtomicInteger();

    @Autowired
    GraphQlTester graphql;

    @Autowired
    AgendamentoServico servico;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    private static String cpf() {
        return CpfTest.gerar(CPFS.incrementAndGet());
    }

    private long vaga(int unidade, int servicoId, String horario) {
        return jdbc.sql("SELECT id FROM vagas WHERE unidade_id = :u AND servico_id = :s AND inicio = CAST(:h AS timestamptz)")
                .param("u", unidade).param("s", servicoId).param("h", horario).query(Long.class).single();
    }

    private int ocupadas(long vagaId) {
        return jdbc.sql("SELECT ocupadas FROM vagas WHERE id = :id").param("id", vagaId).query(Integer.class).single();
    }

    private String agendar(long vagaId, String cpf) {
        return graphql.document("""
                        mutation($vaga: ID!, $cpf: String!) {
                          agendar(entrada: {vagaId: $vaga, nome: "Maria da Silva", cpf: $cpf, email: "Maria@Exemplo.com"}) {
                            codigo status cpfMascarado email unidade { nome } servico { nome } inicio
                          }
                        }""")
                .variable("vaga", vagaId).variable("cpf", cpf)
                .execute().path("agendar.codigo").entity(String.class).get();
    }

    private static void esperarErro(GraphQlTester.Response resposta, String inicioDaMensagem) {
        resposta.errors().satisfy(erros -> assertThat(erros)
                .anyMatch(e -> e.getErrorType() == ErrorType.BAD_REQUEST && e.getMessage().startsWith(inicioDaMensagem)));
    }

    @Test
    void geraAgendaDosProximosDiasUteis() {
        GraphQlTester.Response segunda = graphql
                .document("{ horariosDisponiveis(unidadeId: 1, servicoId: 1, dia: \"2030-03-04\") { inicio disponiveis } }")
                .execute();
        segunda.path("horariosDisponiveis").entityList(Map.class).hasSize(18);
        segunda.path("horariosDisponiveis[0].disponiveis").entity(Integer.class).isEqualTo(3);

        graphql.document("{ horariosDisponiveis(unidadeId: 1, servicoId: 1, dia: \"2030-03-09\") { inicio } }") // sábado
                .execute().path("horariosDisponiveis").entityList(Map.class).hasSize(0);
    }

    @Test
    void agendaEConsultaPeloCodigo() {
        String codigo = agendar(vaga(1, 1, "2030-03-05T09:00:00-03:00"), "529.982.247-25");

        GraphQlTester.Response resposta = graphql
                .document("query($c: String!) { agendamento(codigo: $c) { status cpfMascarado email unidade { id } } }")
                .variable("c", codigo.toLowerCase())
                .execute();
        resposta.path("agendamento.status").entity(String.class).isEqualTo("AGENDADO");
        resposta.path("agendamento.cpfMascarado").entity(String.class).isEqualTo("***.***.**7-25");
        resposta.path("agendamento.email").entity(String.class).isEqualTo("maria@exemplo.com");
        resposta.path("agendamento.unidade.id").entity(String.class).isEqualTo("1");

        // O CPF não fica no banco: só o HMAC e os 3 últimos dígitos
        String gravado = jdbc.sql("SELECT cpf_hash || cpf_final FROM agendamentos WHERE codigo = :c").param("c", codigo)
                .query(String.class).single();
        assertThat(gravado).doesNotContain("52998224725");
    }

    @Test
    void cpfInvalidoERecusado() {
        esperarErro(graphql.document(
                "mutation { agendar(entrada: {vagaId: 1, nome: \"Ana\", cpf: \"111.111.111-11\", email: \"a@b.com\"}) { codigo } }")
                .execute(), "CPF inválido");
    }

    @Test
    void umAgendamentoAtivoPorDiaPorCidadao() {
        String cpf = cpf();
        agendar(vaga(2, 1, "2030-03-06T08:00:00-03:00"), cpf);
        esperarErro(graphql.document(
                        "mutation($v: ID!, $cpf: String!) { agendar(entrada: {vagaId: $v, nome: \"Maria\", cpf: $cpf, email: \"a@b.com\"}) { codigo } }")
                .variable("v", vaga(2, 2, "2030-03-06T10:00:00-03:00")).variable("cpf", cpf)
                .execute(), "Você já tem um agendamento ativo neste dia");
    }

    @Test
    void lotacaoEhRespeitadaComPedidosSimultaneos() throws Exception {
        // Unidade 1, serviço 2: 2 guichês. 10 cidadãos tentam o mesmo horário ao mesmo tempo.
        long vagaId = vaga(1, 2, "2030-03-07T11:00:00-03:00");
        List<Callable<Boolean>> tentativas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String cpf = cpf();
            tentativas.add(() -> {
                try {
                    servico.agendar(new NovoAgendamento(vagaId, "Cidadão " + cpf, cpf, "c@exemplo.com"));
                    return true;
                } catch (RegraDeNegocioException e) {
                    return false;
                }
            });
        }
        int sucessos = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            for (Future<Boolean> f : executor.invokeAll(tentativas)) {
                if (f.get()) {
                    sucessos++;
                }
            }
        }
        assertThat(sucessos).isEqualTo(2);
        assertThat(ocupadas(vagaId)).isEqualTo(2);
    }

    @Test
    void cancelarLiberaAVagaERespeitaAntecedencia() {
        long vagaId = vaga(3, 2, "2030-03-08T14:00:00-03:00"); // 1 guichê
        String codigo = agendar(vagaId, cpf());
        assertThat(ocupadas(vagaId)).isEqualTo(1);

        graphql.document("mutation($c: String!) { cancelar(codigo: $c) { status } }").variable("c", codigo)
                .execute().path("cancelar.status").entity(String.class).isEqualTo("CANCELADO");
        assertThat(ocupadas(vagaId)).isZero();

        // Hoje às 08h: falta 1 hora (o relógio está em 07h), menos que as 2 horas exigidas
        String deHoje = agendar(vaga(3, 4, "2030-03-04T08:00:00-03:00"), cpf());
        esperarErro(graphql.document("mutation($c: String!) { cancelar(codigo: $c) { status } }").variable("c", deHoje)
                .execute(), "O cancelamento só é permitido até 2 horas");
    }

    @Test
    void eventosChegamAoPainelPeloKafkaUmaVezSo() {
        String a = agendar(vaga(2, 4, "2030-03-11T09:00:00-03:00"), cpf());
        String b = agendar(vaga(2, 4, "2030-03-11T09:30:00-03:00"), cpf());
        agendar(vaga(2, 4, "2030-03-11T10:00:00-03:00"), cpf());
        graphql.document("mutation($c: String!) { cancelar(codigo: $c) { status } }").variable("c", a).execute()
                .path("cancelar.status").entity(String.class).isEqualTo("CANCELADO");
        graphql.document("mutation($c: String!) { registrarComparecimento(codigo: $c, compareceu: true) { status } }")
                .variable("c", b).execute().path("registrarComparecimento.status").entity(String.class).isEqualTo("ATENDIDO");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(painelUnidade2())
                .containsEntry("agendados", 2).containsEntry("cancelados", 1).containsEntry("atendidos", 1));

        // Reentregar um evento já processado (o Kafka pode fazer isso) não conta de novo
        String repetido = jdbc.sql("SELECT payload::text FROM outbox WHERE tipo = 'CIDADAO_ATENDIDO' LIMIT 1")
                .query(String.class).single();
        kafka.send("agendamentos.eventos", "2", repetido).join();
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(painelUnidade2()).containsEntry("atendidos", 1));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> painelUnidade2() {
        return graphql.document("{ painel(dia: \"2030-03-11\") { unidade { id } agendados cancelados atendidos } }")
                .execute().path("painel[1]").entity(Map.class).get();
    }
}

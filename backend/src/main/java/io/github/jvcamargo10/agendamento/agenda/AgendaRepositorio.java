package io.github.jvcamargo10.agendamento.agenda;

import io.github.jvcamargo10.agendamento.agenda.Modelos.Agendamento;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Horario;
import io.github.jvcamargo10.agendamento.agenda.Modelos.PainelDia;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Servico;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Unidade;
import io.github.jvcamargo10.agendamento.agenda.Modelos.VagaReservada;
import io.github.jvcamargo10.agendamento.dominio.Cpf;
import io.github.jvcamargo10.agendamento.dominio.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso ao PostgreSQL com SQL explícito (JdbcClient). As regras de concorrência estão no SQL. */
@Repository
public class AgendaRepositorio {

    private static final String SELECT_AGENDAMENTO = """
            SELECT a.id, a.codigo, a.status, a.cidadao_nome, a.cpf_final, a.email,
                   v.unidade_id, v.servico_id, v.id AS vaga_id, v.inicio
              FROM agendamentos a JOIN vagas v ON v.id = a.vaga_id
            """;

    private final JdbcClient jdbc;

    public AgendaRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Unidade> unidades() {
        return jdbc.sql("SELECT id, nome, endereco, cidade, uf FROM unidades ORDER BY id").query(Unidade.class).list();
    }

    public List<Unidade> unidades(Collection<Integer> ids) {
        return jdbc.sql("SELECT id, nome, endereco, cidade, uf FROM unidades WHERE id IN (:ids)")
                .param("ids", ids).query(Unidade.class).list();
    }

    public List<Servico> servicos() {
        return jdbc.sql("SELECT id, nome, duracao_minutos FROM servicos ORDER BY id").query(Servico.class).list();
    }

    /** Pares (unidade, serviço) disponíveis, para montar Unidade.servicos em uma única consulta. */
    public List<int[]> servicosPorUnidade(Collection<Integer> unidades) {
        return jdbc.sql("SELECT unidade_id, servico_id FROM unidade_servicos WHERE unidade_id IN (:ids) ORDER BY servico_id")
                .param("ids", unidades)
                .query((rs, i) -> new int[] {rs.getInt("unidade_id"), rs.getInt("servico_id")})
                .list();
    }

    /** Cria os horários que ainda não existem. ON CONFLICT torna a operação segura para repetir. */
    public int gerarVagas(LocalDate hoje, int dias, String fuso) {
        return jdbc.sql("""
                INSERT INTO vagas (unidade_id, servico_id, inicio, capacidade)
                SELECT us.unidade_id, us.servico_id,
                       ((CAST(:hoje AS date) + d) + time '08:00' + h * interval '30 minutes') AT TIME ZONE :fuso,
                       us.guiches
                  FROM unidade_servicos us
                 CROSS JOIN generate_series(0, :dias) AS d
                 CROSS JOIN generate_series(0, 17) AS h
                 WHERE extract(isodow FROM CAST(:hoje AS date) + d) < 6
                ON CONFLICT (unidade_id, servico_id, inicio) DO NOTHING
                """)
                .param("hoje", hoje).param("dias", dias).param("fuso", fuso)
                .update();
    }

    public List<Horario> horariosDisponiveis(int unidadeId, int servicoId, OffsetDateTime de, OffsetDateTime ate,
                                             OffsetDateTime agora) {
        return jdbc.sql("""
                SELECT id AS vaga_id, inicio, capacidade - ocupadas AS disponiveis
                  FROM vagas
                 WHERE unidade_id = :unidade AND servico_id = :servico
                   AND inicio >= :de AND inicio < :ate AND inicio > :agora
                   AND ocupadas < capacidade
                 ORDER BY inicio
                """)
                .param("unidade", unidadeId).param("servico", servicoId)
                .param("de", de).param("ate", ate).param("agora", agora)
                .query(Horario.class).list();
    }

    /**
     * Ocupa um lugar no horário de forma atômica. Com 10 pedidos simultâneos para 2 lugares, o PostgreSQL
     * serializa os UPDATEs na mesma linha e só os 2 primeiros satisfazem "ocupadas < capacidade".
     * Não há leitura seguida de escrita, então não existe janela para corrida.
     */
    public Optional<VagaReservada> reservarVaga(long vagaId, OffsetDateTime agora) {
        return jdbc.sql("""
                UPDATE vagas SET ocupadas = ocupadas + 1
                 WHERE id = :id AND ocupadas < capacidade AND inicio > :agora
                RETURNING id, unidade_id, servico_id, inicio
                """)
                .param("id", vagaId).param("agora", agora)
                .query(VagaReservada.class).optional();
    }

    public void liberarVaga(long vagaId) {
        jdbc.sql("UPDATE vagas SET ocupadas = ocupadas - 1 WHERE id = :id").param("id", vagaId).update();
    }

    public boolean temAgendamentoAtivoNoDia(String cpfHash, LocalDate dia) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM agendamentos WHERE cpf_hash = :h AND dia = :dia AND status = 'AGENDADO')")
                .param("h", cpfHash).param("dia", dia).query(Boolean.class).single();
    }

    public long inserir(String codigo, long vagaId, LocalDate dia, String nome, String cpfHash, String cpfFinal,
                        String email) {
        return jdbc.sql("""
                INSERT INTO agendamentos (codigo, vaga_id, dia, cidadao_nome, cpf_hash, cpf_final, email, status)
                VALUES (:codigo, :vaga, :dia, :nome, :hash, :final, :email, 'AGENDADO')
                RETURNING id
                """)
                .param("codigo", codigo).param("vaga", vagaId).param("dia", dia).param("nome", nome)
                .param("hash", cpfHash).param("final", cpfFinal).param("email", email)
                .query(Long.class).single();
    }

    public Optional<Agendamento> porCodigo(String codigo) {
        return jdbc.sql(SELECT_AGENDAMENTO + " WHERE a.codigo = :codigo").param("codigo", codigo)
                .query(AgendaRepositorio::mapear).optional();
    }

    /** Trava a linha até o fim da transação: duas mudanças de status simultâneas não se sobrepõem. */
    public Optional<Agendamento> porCodigoParaAtualizar(String codigo) {
        return jdbc.sql(SELECT_AGENDAMENTO + " WHERE a.codigo = :codigo FOR UPDATE OF a").param("codigo", codigo)
                .query(AgendaRepositorio::mapear).optional();
    }

    public void mudarStatus(long id, Status status) {
        jdbc.sql("UPDATE agendamentos SET status = :status, atualizado_em = now() WHERE id = :id")
                .param("status", status.name()).param("id", id).update();
    }

    public List<Agendamento> daUnidade(int unidadeId, LocalDate dia) {
        return jdbc.sql(SELECT_AGENDAMENTO + " WHERE v.unidade_id = :unidade AND a.dia = :dia ORDER BY v.inicio, a.id")
                .param("unidade", unidadeId).param("dia", dia)
                .query(AgendaRepositorio::mapear).list();
    }

    // ------------------------------------------------------------------ outbox

    public void gravarEvento(UUID id, String topico, String chave, String tipo, String payloadJson) {
        jdbc.sql("INSERT INTO outbox (id, topico, chave, tipo, payload) VALUES (:id, :topico, :chave, :tipo, CAST(:payload AS jsonb))")
                .param("id", id).param("topico", topico).param("chave", chave).param("tipo", tipo)
                .param("payload", payloadJson).update();
    }

    public record EventoPendente(UUID id, String topico, String chave, String payload) {
    }

    /** SKIP LOCKED: várias réplicas do serviço podem publicar ao mesmo tempo sem enviar o mesmo evento. */
    public List<EventoPendente> eventosPendentes(int limite) {
        return jdbc.sql("""
                SELECT id, topico, chave, payload::text AS payload FROM outbox
                 WHERE publicado_em IS NULL ORDER BY criado_em LIMIT :limite
                   FOR UPDATE SKIP LOCKED
                """)
                .param("limite", limite).query(EventoPendente.class).list();
    }

    public void marcarPublicados(Collection<UUID> ids) {
        jdbc.sql("UPDATE outbox SET publicado_em = now() WHERE id IN (:ids)").param("ids", ids).update();
    }

    public long eventosNaoPublicados() {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE publicado_em IS NULL").query(Long.class).single();
    }

    // ------------------------------------------------------------------ painel (modelo de leitura)

    /** Devolve true só na primeira vez que o consumidor vê o evento. */
    public boolean registrarProcessamento(String consumidor, UUID eventoId) {
        return jdbc.sql("""
                INSERT INTO eventos_processados (consumidor, evento_id) VALUES (:c, :e) ON CONFLICT DO NOTHING
                """).param("c", consumidor).param("e", eventoId).update() == 1;
    }

    public void somarNoPainel(int unidadeId, LocalDate dia, int agendados, int cancelados, int atendidos, int faltas) {
        jdbc.sql("""
                INSERT INTO painel_diario (unidade_id, dia, agendados, cancelados, atendidos, faltas)
                VALUES (:u, :dia, :ag, :ca, :at, :fa)
                ON CONFLICT (unidade_id, dia) DO UPDATE SET
                  agendados  = painel_diario.agendados  + EXCLUDED.agendados,
                  cancelados = painel_diario.cancelados + EXCLUDED.cancelados,
                  atendidos  = painel_diario.atendidos  + EXCLUDED.atendidos,
                  faltas     = painel_diario.faltas     + EXCLUDED.faltas
                """)
                .param("u", unidadeId).param("dia", dia).param("ag", agendados).param("ca", cancelados)
                .param("at", atendidos).param("fa", faltas)
                .update();
    }

    public List<PainelDia> painel(LocalDate dia) {
        return jdbc.sql("""
                SELECT u.id AS unidade_id, CAST(:dia AS date) AS dia,
                       coalesce(p.agendados, 0) AS agendados, coalesce(p.cancelados, 0) AS cancelados,
                       coalesce(p.atendidos, 0) AS atendidos, coalesce(p.faltas, 0) AS faltas
                  FROM unidades u LEFT JOIN painel_diario p ON p.unidade_id = u.id AND p.dia = :dia
                 ORDER BY u.id
                """)
                .param("dia", dia).query(PainelDia.class).list();
    }

    private static Agendamento mapear(ResultSet rs, int linha) throws SQLException {
        return new Agendamento(rs.getLong("id"), rs.getString("codigo"), Status.valueOf(rs.getString("status")),
                rs.getString("cidadao_nome"), Cpf.mascarar(rs.getString("cpf_final")), rs.getString("email"),
                rs.getInt("unidade_id"), rs.getInt("servico_id"), rs.getLong("vaga_id"),
                rs.getObject("inicio", OffsetDateTime.class));
    }
}

package io.github.jvcamargo10.agendamento.agenda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Agendamento;
import io.github.jvcamargo10.agendamento.agenda.Modelos.NovoAgendamento;
import io.github.jvcamargo10.agendamento.agenda.Modelos.VagaReservada;
import io.github.jvcamargo10.agendamento.config.AgendaPropriedades;
import io.github.jvcamargo10.agendamento.dominio.Cpf;
import io.github.jvcamargo10.agendamento.dominio.RegraDeNegocioException;
import io.github.jvcamargo10.agendamento.dominio.Status;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do agendamento. Cada método é uma transação: a mudança no agendamento, a ocupação da
 * vaga e o evento na outbox são gravados juntos, ou nada é gravado.
 */
@Service
public class AgendamentoServico {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sem 0/O e 1/I, que confundem
    private final SecureRandom aleatorio = new SecureRandom();

    private final AgendaRepositorio repositorio;
    private final AgendaPropriedades propriedades;
    private final Clock relogio;
    private final ObjectMapper json;
    private final MeterRegistry metricas;

    public AgendamentoServico(AgendaRepositorio repositorio, AgendaPropriedades propriedades, Clock relogio,
                              ObjectMapper json, MeterRegistry metricas) {
        this.repositorio = repositorio;
        this.propriedades = propriedades;
        this.relogio = relogio;
        this.json = json;
        this.metricas = metricas;
    }

    @Transactional
    public Agendamento agendar(NovoAgendamento pedido) {
        if (!Cpf.valido(pedido.cpf())) {
            throw new RegraDeNegocioException("CPF inválido");
        }
        String cpfHash = Cpf.hash(pedido.cpf(), propriedades.segredoCpf());

        VagaReservada vaga = repositorio.reservarVaga(pedido.vagaId(), agora())
                .orElseThrow(() -> new RegraDeNegocioException("Este horário não está mais disponível"));
        LocalDate dia = vaga.inicio().atZoneSameInstant(fuso()).toLocalDate();

        // Verificação para dar uma mensagem clara. Quem garante a regra sob concorrência é o índice único parcial.
        if (repositorio.temAgendamentoAtivoNoDia(cpfHash, dia)) {
            throw new RegraDeNegocioException("Você já tem um agendamento ativo neste dia");
        }
        long id;
        String codigo = novoCodigo();
        try {
            id = repositorio.inserir(codigo, vaga.id(), dia, pedido.nome().trim(), cpfHash, Cpf.finais(pedido.cpf()),
                    pedido.email().trim().toLowerCase());
        } catch (DuplicateKeyException e) {
            throw new RegraDeNegocioException("Você já tem um agendamento ativo neste dia");
        }

        Agendamento agendamento = new Agendamento(id, codigo, Status.AGENDADO, pedido.nome().trim(),
                Cpf.mascarar(Cpf.finais(pedido.cpf())), pedido.email().trim().toLowerCase(), vaga.unidadeId(),
                vaga.servicoId(), vaga.id(), vaga.inicio());
        publicar("AGENDAMENTO_CRIADO", agendamento);
        metricas.counter("agendamentos.criados", "unidade", String.valueOf(vaga.unidadeId())).increment();
        return agendamento;
    }

    @Transactional
    public Agendamento cancelar(String codigo) {
        Agendamento atual = travar(codigo);
        exigirStatus(atual, Status.AGENDADO);
        if (agora().plus(propriedades.antecedenciaCancelamento()).isAfter(atual.inicio())) {
            throw new RegraDeNegocioException("O cancelamento só é permitido até "
                    + propriedades.antecedenciaCancelamento().toHours() + " horas antes do horário");
        }
        repositorio.mudarStatus(atual.id(), Status.CANCELADO);
        repositorio.liberarVaga(atual.vagaId());
        Agendamento cancelado = comStatus(atual, Status.CANCELADO);
        publicar("AGENDAMENTO_CANCELADO", cancelado);
        return cancelado;
    }

    @Transactional
    public Agendamento registrarComparecimento(String codigo, boolean compareceu) {
        Agendamento atual = travar(codigo);
        exigirStatus(atual, Status.AGENDADO);
        Status novo = compareceu ? Status.ATENDIDO : Status.FALTOU;
        repositorio.mudarStatus(atual.id(), novo);
        Agendamento atualizado = comStatus(atual, novo);
        publicar(compareceu ? "CIDADAO_ATENDIDO" : "CIDADAO_FALTOU", atualizado);
        return atualizado;
    }

    private Agendamento travar(String codigo) {
        return repositorio.porCodigoParaAtualizar(codigo.trim().toUpperCase())
                .orElseThrow(() -> new RegraDeNegocioException("Agendamento não encontrado"));
    }

    private static void exigirStatus(Agendamento a, Status esperado) {
        if (a.status() != esperado) {
            throw new RegraDeNegocioException("Agendamento está " + a.status().name().toLowerCase()
                    + " e não pode ser alterado");
        }
    }

    private static Agendamento comStatus(Agendamento a, Status s) {
        return new Agendamento(a.id(), a.codigo(), s, a.cidadaoNome(), a.cpfMascarado(), a.email(), a.unidadeId(),
                a.servicoId(), a.vagaId(), a.inicio());
    }

    /** Grava o evento na outbox, na mesma transação. O OutboxPublicador envia ao Kafka depois do commit. */
    private void publicar(String tipo, Agendamento a) {
        Map<String, Object> evento = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        evento.put("id", id);
        evento.put("tipo", tipo);
        evento.put("codigo", a.codigo());
        evento.put("unidadeId", a.unidadeId());
        evento.put("servicoId", a.servicoId());
        evento.put("inicio", a.inicio().toString());
        evento.put("dia", a.inicio().atZoneSameInstant(fuso()).toLocalDate().toString());
        evento.put("ocorridoEm", agora().toString());
        try {
            repositorio.gravarEvento(id, propriedades.topicoEventos(), String.valueOf(a.unidadeId()), tipo,
                    json.writeValueAsString(evento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String novoCodigo() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private ZoneId fuso() {
        return ZoneId.of(propriedades.fuso());
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}

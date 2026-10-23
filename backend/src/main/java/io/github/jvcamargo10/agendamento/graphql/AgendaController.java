package io.github.jvcamargo10.agendamento.graphql;

import io.github.jvcamargo10.agendamento.agenda.AgendaRepositorio;
import io.github.jvcamargo10.agendamento.agenda.AgendamentoServico;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Agendamento;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Horario;
import io.github.jvcamargo10.agendamento.agenda.Modelos.NovoAgendamento;
import io.github.jvcamargo10.agendamento.agenda.Modelos.PainelDia;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Servico;
import io.github.jvcamargo10.agendamento.agenda.Modelos.Unidade;
import io.github.jvcamargo10.agendamento.config.AgendaPropriedades;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AgendaController {

    private final AgendaRepositorio repositorio;
    private final AgendamentoServico servico;
    private final AgendaPropriedades propriedades;
    private final Clock relogio;

    public AgendaController(AgendaRepositorio repositorio, AgendamentoServico servico, AgendaPropriedades propriedades,
                            Clock relogio) {
        this.repositorio = repositorio;
        this.servico = servico;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------ consultas

    @QueryMapping
    public List<Unidade> unidades() {
        return repositorio.unidades();
    }

    @QueryMapping
    public List<Servico> servicos() {
        return repositorio.servicos();
    }

    @QueryMapping
    public List<Horario> horariosDisponiveis(@Argument int unidadeId, @Argument int servicoId, @Argument String dia) {
        ZoneId fuso = ZoneId.of(propriedades.fuso());
        LocalDate data = LocalDate.parse(dia);
        OffsetDateTime de = data.atStartOfDay(fuso).toOffsetDateTime();
        return repositorio.horariosDisponiveis(unidadeId, servicoId, de, de.plusDays(1), OffsetDateTime.now(relogio));
    }

    @QueryMapping
    public Agendamento agendamento(@Argument String codigo) {
        return repositorio.porCodigo(codigo.trim().toUpperCase()).orElse(null);
    }

    @QueryMapping
    public List<Agendamento> agendamentosDaUnidade(@Argument int unidadeId, @Argument String dia) {
        return repositorio.daUnidade(unidadeId, LocalDate.parse(dia));
    }

    @QueryMapping
    public List<PainelDia> painel(@Argument String dia) {
        return repositorio.painel(LocalDate.parse(dia));
    }

    // ------------------------------------------------------------ mutações

    @MutationMapping
    public Agendamento agendar(@Argument @Valid NovoAgendamento entrada) {
        return servico.agendar(entrada);
    }

    @MutationMapping
    public Agendamento cancelar(@Argument String codigo) {
        return servico.cancelar(codigo);
    }

    @MutationMapping
    public Agendamento registrarComparecimento(@Argument String codigo, @Argument boolean compareceu) {
        return servico.registrarComparecimento(codigo, compareceu);
    }

    // ------------------------------------------------------------ campos relacionados
    // @BatchMapping resolve o campo para a lista inteira de uma vez: uma lista de 50 agendamentos
    // faz 1 consulta de unidades, e não 50 (evita o problema N+1 do GraphQL).

    @BatchMapping(typeName = "Agendamento", field = "unidade")
    public Map<Agendamento, Unidade> unidadeDoAgendamento(List<Agendamento> agendamentos) {
        Map<Integer, Unidade> unidades = unidadesPorId(agendamentos.stream().map(Agendamento::unidadeId).toList());
        return agendamentos.stream().collect(Collectors.toMap(Function.identity(), a -> unidades.get(a.unidadeId()),
                (x, y) -> x));
    }

    @BatchMapping(typeName = "Agendamento", field = "servico")
    public Map<Agendamento, Servico> servicoDoAgendamento(List<Agendamento> agendamentos) {
        Map<Integer, Servico> servicos = servicosPorId();
        return agendamentos.stream().collect(Collectors.toMap(Function.identity(), a -> servicos.get(a.servicoId()),
                (x, y) -> x));
    }

    @BatchMapping(typeName = "PainelDia", field = "unidade")
    public Map<PainelDia, Unidade> unidadeDoPainel(List<PainelDia> linhas) {
        Map<Integer, Unidade> unidades = unidadesPorId(linhas.stream().map(PainelDia::unidadeId).toList());
        return linhas.stream().collect(Collectors.toMap(Function.identity(), l -> unidades.get(l.unidadeId()),
                (x, y) -> x));
    }

    @BatchMapping(typeName = "Unidade", field = "servicos")
    public Map<Unidade, List<Servico>> servicosDaUnidade(List<Unidade> unidades) {
        Map<Integer, Servico> servicos = servicosPorId();
        Map<Integer, List<Servico>> porUnidade = new HashMap<>();
        for (int[] par : repositorio.servicosPorUnidade(unidades.stream().map(Unidade::id).toList())) {
            porUnidade.computeIfAbsent(par[0], k -> new java.util.ArrayList<>()).add(servicos.get(par[1]));
        }
        return unidades.stream().collect(Collectors.toMap(Function.identity(),
                u -> porUnidade.getOrDefault(u.id(), List.of()), (x, y) -> x));
    }

    private Map<Integer, Unidade> unidadesPorId(List<Integer> ids) {
        return repositorio.unidades(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Unidade::id, Function.identity()));
    }

    private Map<Integer, Servico> servicosPorId() {
        return repositorio.servicos().stream().collect(Collectors.toMap(Servico::id, Function.identity()));
    }
}

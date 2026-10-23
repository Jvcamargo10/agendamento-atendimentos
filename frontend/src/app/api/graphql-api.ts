import { AgendaApi, Agendamento, ErroDeNegocio, Horario, NovoAgendamento, PainelDia, Unidade } from './modelos';

const CAMPOS = 'codigo status cidadaoNome cpfMascarado email inicio unidade { id nome } servico { id nome }';

/** Cliente GraphQL simples com fetch: as consultas são poucas e fixas, então não precisa de Apollo. */
export class GraphqlAgendaApi extends AgendaApi {
  constructor(private readonly url: string) {
    super();
  }

  private async executar<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
    const resposta = await fetch(this.url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });
    const corpo = await resposta.json();
    if (corpo.errors?.length) {
      const erro = corpo.errors[0];
      throw erro.extensions?.classification === 'BAD_REQUEST' ? new ErroDeNegocio(erro.message) : new Error(erro.message);
    }
    return corpo.data as T;
  }

  async unidades() {
    return (await this.executar<{ unidades: Unidade[] }>(
      '{ unidades { id nome endereco cidade uf servicos { id nome duracaoMinutos } } }')).unidades;
  }

  async horarios(unidadeId: string, servicoId: string, dia: string) {
    return (await this.executar<{ horariosDisponiveis: Horario[] }>(
      'query($u: ID!, $s: ID!, $d: String!) { horariosDisponiveis(unidadeId: $u, servicoId: $s, dia: $d) { vagaId inicio disponiveis } }',
      { u: unidadeId, s: servicoId, d: dia })).horariosDisponiveis;
  }

  async agendar(entrada: NovoAgendamento) {
    return (await this.executar<{ agendar: Agendamento }>(
      `mutation($e: NovoAgendamento!) { agendar(entrada: $e) { ${CAMPOS} } }`, { e: entrada })).agendar;
  }

  async buscar(codigo: string) {
    return (await this.executar<{ agendamento: Agendamento | null }>(
      `query($c: String!) { agendamento(codigo: $c) { ${CAMPOS} } }`, { c: codigo })).agendamento;
  }

  async cancelar(codigo: string) {
    return (await this.executar<{ cancelar: Agendamento }>(
      `mutation($c: String!) { cancelar(codigo: $c) { ${CAMPOS} } }`, { c: codigo })).cancelar;
  }

  async registrarComparecimento(codigo: string, compareceu: boolean) {
    return (await this.executar<{ registrarComparecimento: Agendamento }>(
      `mutation($c: String!, $v: Boolean!) { registrarComparecimento(codigo: $c, compareceu: $v) { ${CAMPOS} } }`,
      { c: codigo, v: compareceu })).registrarComparecimento;
  }

  async daUnidade(unidadeId: string, dia: string) {
    return (await this.executar<{ agendamentosDaUnidade: Agendamento[] }>(
      `query($u: ID!, $d: String!) { agendamentosDaUnidade(unidadeId: $u, dia: $d) { ${CAMPOS} } }`,
      { u: unidadeId, d: dia })).agendamentosDaUnidade;
  }

  async painel(dia: string) {
    return (await this.executar<{ painel: PainelDia[] }>(
      'query($d: String!) { painel(dia: $d) { unidade { id nome } dia agendados cancelados atendidos faltas } }',
      { d: dia })).painel;
  }
}

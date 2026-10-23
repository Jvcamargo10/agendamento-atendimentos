export type Status = 'AGENDADO' | 'CANCELADO' | 'ATENDIDO' | 'FALTOU';

export interface Servico { id: string; nome: string; duracaoMinutos: number; }
export interface Unidade { id: string; nome: string; endereco: string; cidade: string; uf: string; servicos: Servico[]; }
export interface Horario { vagaId: string; inicio: string; disponiveis: number; }

export interface Agendamento {
  codigo: string;
  status: Status;
  cidadaoNome: string;
  cpfMascarado: string;
  email: string;
  unidade: Pick<Unidade, 'id' | 'nome'>;
  servico: Pick<Servico, 'id' | 'nome'>;
  inicio: string;
}

export interface PainelDia {
  unidade: Pick<Unidade, 'id' | 'nome'>;
  dia: string;
  agendados: number;
  cancelados: number;
  atendidos: number;
  faltas: number;
}

export interface NovoAgendamento { vagaId: string; nome: string; cpf: string; email: string; }

/** Evento publicado no Kafka (a demo mostra o fluxo na tela). */
export interface EventoKafka {
  id: string;
  tipo: string;
  codigo: string;
  unidadeId: string;
  particao: number;
  offset: number;
  ocorridoEm: string;
  processado: boolean;
}

/** Contrato usado pelas telas. Duas implementações: GraphQL (servidor real) e demonstração (no navegador). */
export abstract class AgendaApi {
  abstract unidades(): Promise<Unidade[]>;
  abstract horarios(unidadeId: string, servicoId: string, dia: string): Promise<Horario[]>;
  abstract agendar(novo: NovoAgendamento): Promise<Agendamento>;
  abstract buscar(codigo: string): Promise<Agendamento | null>;
  abstract cancelar(codigo: string): Promise<Agendamento>;
  abstract registrarComparecimento(codigo: string, compareceu: boolean): Promise<Agendamento>;
  abstract daUnidade(unidadeId: string, dia: string): Promise<Agendamento[]>;
  abstract painel(dia: string): Promise<PainelDia[]>;
  eventos?(): EventoKafka[];
  reiniciar?(): void;
}

export class ErroDeNegocio extends Error {}

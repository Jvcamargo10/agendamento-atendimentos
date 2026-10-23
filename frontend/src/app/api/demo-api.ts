import { AgendaApi, Agendamento, ErroDeNegocio, EventoKafka, Horario, NovoAgendamento, PainelDia, Status, Unidade } from './modelos';
import { cpfValido, diaLocal, digitosCpf, mascararCpf, proximosDiasUteis } from './regras';

/**
 * Versão da API que roda no navegador, para a demonstração no GitHub Pages.
 * Reproduz as regras do back-end: lotação por horário, um agendamento ativo por dia por CPF,
 * cancelamento até 2 horas antes, e o painel atualizado de forma assíncrona por eventos (como o Kafka).
 */

const SERVICOS = [
  { id: '1', nome: 'Emissão de RG', duracaoMinutos: 30 },
  { id: '2', nome: 'Cadastro no CadÚnico', duracaoMinutos: 30 },
  { id: '3', nome: 'Alvará de funcionamento', duracaoMinutos: 30 },
  { id: '4', nome: 'Segunda via de IPTU', duracaoMinutos: 30 },
];

const UNIDADES = [
  { id: '1', nome: 'Central de Atendimento Centro', endereco: 'Praça da Matriz, 100', guiches: { 1: 3, 2: 2, 3: 2, 4: 2 } },
  { id: '2', nome: 'Central de Atendimento Norte', endereco: 'Av. das Palmeiras, 2.450', guiches: { 1: 2, 2: 2, 4: 1 } },
  { id: '3', nome: 'Posto Avançado Jardim Esperança', endereco: 'Rua dos Ipês, 87', guiches: { 2: 1, 4: 1 } },
] as const;

const NOMES = ['Ana Souza', 'Bruno Lima', 'Carla Mendes', 'Diego Rocha', 'Elisa Prado', 'Fábio Nunes', 'Gabriela Reis',
  'Hugo Matos', 'Isabela Costa', 'João Pereira', 'Karina Alves', 'Lucas Martins', 'Marina Duarte', 'Nelson Faria'];

const ANTECEDENCIA_MS = 2 * 60 * 60 * 1000;

interface Vaga { id: string; unidadeId: string; servicoId: string; inicio: string; capacidade: number; ocupadas: number; }
interface Registro { codigo: string; vagaId: string; dia: string; nome: string; cpf: string; email: string; status: Status; }

const atraso = (ms = 150) => new Promise((r) => setTimeout(r, ms));

export class DemoAgendaApi extends AgendaApi {
  private vagas = new Map<string, Vaga>();
  private registros = new Map<string, Registro>();
  private painelPorChave = new Map<string, PainelDia>();
  private log: EventoKafka[] = [];
  private processados = new Set<string>();
  private offsets = [0, 0, 0];
  private ouvintes = new Set<() => void>();
  private sequencia = 0;

  constructor(private readonly agora: () => Date = () => new Date(), private readonly atrasoConsumidor = 700, semear = true) {
    super();
    this.gerarVagas();
    if (semear) this.semear();
  }

  /** As telas se inscrevem para atualizar quando o "consumidor Kafka" processa um evento. */
  aoMudar(ouvinte: () => void): () => void {
    this.ouvintes.add(ouvinte);
    return () => this.ouvintes.delete(ouvinte);
  }

  private gerarVagas() {
    for (const dia of proximosDiasUteis(10, this.agora())) {
      for (const u of UNIDADES) {
        for (const [servicoId, guiches] of Object.entries(u.guiches)) {
          for (let h = 0; h < 18; h++) {
            const minutos = 8 * 60 + h * 30;
            const hora = `${String(Math.floor(minutos / 60)).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`;
            const inicio = new Date(`${dia}T${hora}:00-03:00`).toISOString();
            const id = String(++this.sequencia);
            this.vagas.set(id, { id, unidadeId: u.id, servicoId, inicio, capacidade: guiches as number, ocupadas: 0 });
          }
        }
      }
    }
  }

  /** Agenda fictícia já preenchida em parte, para a demo não começar vazia. */
  private semear() {
    let semente = 7;
    const aleatorio = () => (semente = (semente * 16807) % 2147483647) / 2147483647;
    const dias = proximosDiasUteis(3, this.agora());
    let n = 0;
    for (const vaga of this.vagas.values()) {
      if (!dias.includes(diaLocal(new Date(vaga.inicio)))) continue;
      while (vaga.ocupadas < vaga.capacidade && aleatorio() < 0.4) {
        const passou = new Date(vaga.inicio) < this.agora();
        const status: Status = passou ? (aleatorio() < 0.85 ? 'ATENDIDO' : 'FALTOU') : 'AGENDADO';
        const r: Registro = { codigo: this.codigo(), vagaId: vaga.id, dia: diaLocal(new Date(vaga.inicio)),
          nome: NOMES[n++ % NOMES.length], cpf: `000000000${String(n % 100).padStart(2, '0')}`, email: 'cidadao@exemplo.com', status };
        vaga.ocupadas++;
        this.registros.set(r.codigo, r);
        const p = this.linhaPainel(vaga.unidadeId, r.dia);
        p.agendados++;
        if (status === 'ATENDIDO') p.atendidos++;
        if (status === 'FALTOU') p.faltas++;
      }
    }
  }

  private codigo(): string {
    const alfabeto = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let c = '';
    do { c = Array.from({ length: 8 }, () => alfabeto[Math.floor(Math.random() * alfabeto.length)]).join(''); }
    while (this.registros.has(c));
    return c;
  }

  private linhaPainel(unidadeId: string, dia: string): PainelDia {
    const chave = `${unidadeId}|${dia}`;
    let linha = this.painelPorChave.get(chave);
    if (!linha) {
      const u = UNIDADES.find((x) => x.id === unidadeId)!;
      linha = { unidade: { id: u.id, nome: u.nome }, dia, agendados: 0, cancelados: 0, atendidos: 0, faltas: 0 };
      this.painelPorChave.set(chave, linha);
    }
    return linha;
  }

  private paraAgendamento(r: Registro): Agendamento {
    const vaga = this.vagas.get(r.vagaId)!;
    const u = UNIDADES.find((x) => x.id === vaga.unidadeId)!;
    const s = SERVICOS.find((x) => x.id === vaga.servicoId)!;
    return { codigo: r.codigo, status: r.status, cidadaoNome: r.nome, cpfMascarado: mascararCpf(r.cpf), email: r.email,
      unidade: { id: u.id, nome: u.nome }, servico: { id: s.id, nome: s.nome }, inicio: vaga.inicio };
  }

  /** Equivale a gravar na outbox e o publicador enviar ao Kafka. O consumidor do painel processa depois. */
  private publicar(tipo: string, r: Registro) {
    const vaga = this.vagas.get(r.vagaId)!;
    const particao = Number(vaga.unidadeId) % 3; // chave = unidade: eventos de uma unidade ficam em ordem
    const evento: EventoKafka = { id: crypto.randomUUID(), tipo, codigo: r.codigo, unidadeId: vaga.unidadeId, particao,
      offset: this.offsets[particao]++, ocorridoEm: this.agora().toISOString(), processado: false };
    this.log.unshift(evento);
    this.avisar();
    setTimeout(() => this.consumir(evento, r.dia), this.atrasoConsumidor);
  }

  /** Consumidor idempotente: o id do evento é registrado antes de somar no painel. */
  consumir(evento: EventoKafka, dia: string) {
    if (this.processados.has(evento.id)) return;
    this.processados.add(evento.id);
    const p = this.linhaPainel(evento.unidadeId, dia);
    if (evento.tipo === 'AGENDAMENTO_CRIADO') p.agendados++;
    if (evento.tipo === 'AGENDAMENTO_CANCELADO') { p.agendados--; p.cancelados++; }
    if (evento.tipo === 'CIDADAO_ATENDIDO') p.atendidos++;
    if (evento.tipo === 'CIDADAO_FALTOU') p.faltas++;
    evento.processado = true;
    this.avisar();
  }

  private avisar() { this.ouvintes.forEach((o) => o()); }

  override eventos() { return this.log.slice(0, 30); }

  async unidades(): Promise<Unidade[]> {
    await atraso(80);
    return UNIDADES.map((u) => ({ id: u.id, nome: u.nome, endereco: u.endereco, cidade: 'Vale Azul', uf: 'SP',
      servicos: SERVICOS.filter((s) => s.id in u.guiches) }));
  }

  async horarios(unidadeId: string, servicoId: string, dia: string): Promise<Horario[]> {
    await atraso();
    const agora = this.agora();
    return [...this.vagas.values()]
      .filter((v) => v.unidadeId === unidadeId && v.servicoId === servicoId && diaLocal(new Date(v.inicio)) === dia
        && new Date(v.inicio) > agora && v.ocupadas < v.capacidade)
      .map((v) => ({ vagaId: v.id, inicio: v.inicio, disponiveis: v.capacidade - v.ocupadas }));
  }

  async agendar(novo: NovoAgendamento): Promise<Agendamento> {
    await atraso(250);
    if (!cpfValido(novo.cpf)) throw new ErroDeNegocio('CPF inválido');
    const vaga = this.vagas.get(novo.vagaId);
    if (!vaga || vaga.ocupadas >= vaga.capacidade || new Date(vaga.inicio) <= this.agora()) {
      throw new ErroDeNegocio('Este horário não está mais disponível');
    }
    const dia = diaLocal(new Date(vaga.inicio));
    const cpf = digitosCpf(novo.cpf);
    if ([...this.registros.values()].some((r) => r.cpf === cpf && r.dia === dia && r.status === 'AGENDADO')) {
      throw new ErroDeNegocio('Você já tem um agendamento ativo neste dia');
    }
    vaga.ocupadas++;
    const r: Registro = { codigo: this.codigo(), vagaId: vaga.id, dia, nome: novo.nome.trim(), cpf,
      email: novo.email.trim().toLowerCase(), status: 'AGENDADO' };
    this.registros.set(r.codigo, r);
    this.publicar('AGENDAMENTO_CRIADO', r);
    return this.paraAgendamento(r);
  }

  async buscar(codigo: string) {
    await atraso(80);
    const r = this.registros.get(codigo.trim().toUpperCase());
    return r ? this.paraAgendamento(r) : null;
  }

  private exigirAgendado(codigo: string): Registro {
    const r = this.registros.get(codigo.trim().toUpperCase());
    if (!r) throw new ErroDeNegocio('Agendamento não encontrado');
    if (r.status !== 'AGENDADO') throw new ErroDeNegocio(`Agendamento está ${r.status.toLowerCase()} e não pode ser alterado`);
    return r;
  }

  async cancelar(codigo: string) {
    await atraso();
    const r = this.exigirAgendado(codigo);
    const vaga = this.vagas.get(r.vagaId)!;
    if (this.agora().getTime() + ANTECEDENCIA_MS > new Date(vaga.inicio).getTime()) {
      throw new ErroDeNegocio('O cancelamento só é permitido até 2 horas antes do horário');
    }
    r.status = 'CANCELADO';
    vaga.ocupadas--;
    this.publicar('AGENDAMENTO_CANCELADO', r);
    return this.paraAgendamento(r);
  }

  async registrarComparecimento(codigo: string, compareceu: boolean) {
    await atraso();
    const r = this.exigirAgendado(codigo);
    r.status = compareceu ? 'ATENDIDO' : 'FALTOU';
    this.publicar(compareceu ? 'CIDADAO_ATENDIDO' : 'CIDADAO_FALTOU', r);
    return this.paraAgendamento(r);
  }

  async daUnidade(unidadeId: string, dia: string) {
    await atraso();
    return [...this.registros.values()]
      .filter((r) => r.dia === dia && this.vagas.get(r.vagaId)!.unidadeId === unidadeId)
      .map((r) => this.paraAgendamento(r))
      .sort((a, b) => a.inicio.localeCompare(b.inicio) || a.cidadaoNome.localeCompare(b.cidadaoNome));
  }

  async painel(dia: string) {
    await atraso(80);
    return UNIDADES.map((u) => ({ ...this.linhaPainel(u.id, dia) }));
  }
}

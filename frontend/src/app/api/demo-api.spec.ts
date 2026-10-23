import { DemoAgendaApi } from './demo-api';
import { ErroDeNegocio } from './modelos';
import { cpfValido, proximosDiasUteis } from './regras';

// Segunda-feira, 04/03/2030, 07h em Brasília
const AGORA = new Date('2030-03-04T07:00:00-03:00');
const CPFS = ['52998224725', '11144477735', '39053344705', '12345678909'];

describe('regras', () => {
  it('valida CPF pelos dígitos verificadores', () => {
    expect(cpfValido('529.982.247-25')).toBe(true);
    expect(cpfValido('529.982.247-24')).toBe(false);
    expect(cpfValido('111.111.111-11')).toBe(false);
  });

  it('lista só dias úteis', () => {
    expect(proximosDiasUteis(6, AGORA)).toEqual(['2030-03-04', '2030-03-05', '2030-03-06', '2030-03-07', '2030-03-08', '2030-03-11']);
  });
});

describe('DemoAgendaApi', () => {
  let api: DemoAgendaApi;
  beforeEach(() => { api = new DemoAgendaApi(() => AGORA, 0, false); });

  async function horario(unidade: string, servico: string, dia: string, hora: string) {
    const lista = await api.horarios(unidade, servico, dia);
    const h = lista.find((x) => x.inicio === new Date(`${dia}T${hora}:00-03:00`).toISOString());
    if (!h) throw new Error('horário não encontrado');
    return h;
  }

  it('respeita a lotação do horário', async () => {
    // Posto 3, serviço 2: 1 guichê.
    const h = await horario('3', '2', '2030-03-08', '10:00');
    await api.agendar({ vagaId: h.vagaId, nome: 'Ana', cpf: CPFS[0], email: 'a@b.com' });
    await expect(api.agendar({ vagaId: h.vagaId, nome: 'Bia', cpf: CPFS[1], email: 'b@b.com' }))
      .rejects.toThrow('Este horário não está mais disponível');
  });

  it('um agendamento ativo por dia por CPF', async () => {
    const h1 = await horario('1', '1', '2030-03-08', '09:00');
    const h2 = await horario('1', '2', '2030-03-08', '11:00');
    await api.agendar({ vagaId: h1.vagaId, nome: 'Ana', cpf: CPFS[2], email: 'a@b.com' });
    await expect(api.agendar({ vagaId: h2.vagaId, nome: 'Ana', cpf: CPFS[2], email: 'a@b.com' }))
      .rejects.toBeInstanceOf(ErroDeNegocio);
  });

  it('cancelamento libera a vaga, mas só com 2 horas de antecedência', async () => {
    const longe = await horario('2', '4', '2030-03-08', '15:00');
    const a = await api.agendar({ vagaId: longe.vagaId, nome: 'Ana', cpf: CPFS[3], email: 'a@b.com' });
    expect((await api.cancelar(a.codigo)).status).toBe('CANCELADO');
    expect((await horario('2', '4', '2030-03-08', '15:00')).disponiveis).toBe(1);

    const cedo = (await api.horarios('3', '4', '2030-03-04')).find((h) => h.inicio.endsWith('T11:00:00.000Z'))!; // 08h local
    const b = await api.agendar({ vagaId: cedo.vagaId, nome: 'Bia', cpf: CPFS[0], email: 'b@b.com' });
    await expect(api.cancelar(b.codigo)).rejects.toThrow('2 horas');
  });

  it('o painel é atualizado pelos eventos, e um evento repetido conta uma vez só', async () => {
    const antes = (await api.painel('2030-03-08'))[0].agendados;
    const h = await horario('1', '3', '2030-03-08', '13:00');
    await api.agendar({ vagaId: h.vagaId, nome: 'Ana', cpf: CPFS[1], email: 'a@b.com' });
    await new Promise((r) => setTimeout(r, 5));
    expect((await api.painel('2030-03-08'))[0].agendados).toBe(antes + 1);

    const [evento] = api.eventos();
    expect(evento.processado).toBe(true);
    api.consumir(evento, '2030-03-08'); // reentrega
    expect((await api.painel('2030-03-08'))[0].agendados).toBe(antes + 1);
  });
});

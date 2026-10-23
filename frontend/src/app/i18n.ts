import { Injectable, computed, signal } from '@angular/core';

export type Idioma = 'pt' | 'en';

const TEXTOS = {
  pt: {
    titulo: 'Agendamento de Atendimentos',
    subtitulo: 'Prefeitura Municipal de Vale Azul (fictícia)',
    demo: 'Demonstração: tudo roda no seu navegador, com dados fictícios. Agenda reinicia ao recarregar.',
    abas: { agendar: 'Agendar', meu: 'Meu agendamento', balcao: 'Balcão', painel: 'Painel', arquitetura: 'Arquitetura' },
    unidade: 'Unidade', servico: 'Serviço', dia: 'Dia', horario: 'Horário', vagas: (n: number) => `${n} vaga${n > 1 ? 's' : ''}`,
    semHorarios: 'Nenhum horário disponível neste dia.', escolhaHorario: 'Escolha um horário',
    seusDados: 'Seus dados', nome: 'Nome completo', cpf: 'CPF', email: 'E-mail', cpfDica: 'Teste com 529.982.247-25',
    confirmar: 'Confirmar agendamento', enviando: 'Enviando…',
    confirmado: 'Agendamento confirmado', guardeCodigo: 'Guarde este código para consultar ou cancelar.',
    novo: 'Fazer outro agendamento',
    codigo: 'Código', consultar: 'Consultar', naoEncontrado: 'Nenhum agendamento com este código.',
    cancelar: 'Cancelar agendamento', regraCancelamento: 'O cancelamento é permitido até 2 horas antes.',
    status: { AGENDADO: 'Agendado', CANCELADO: 'Cancelado', ATENDIDO: 'Atendido', FALTOU: 'Faltou' },
    compareceu: 'Compareceu', faltou: 'Faltou', semAgendamentos: 'Nenhum agendamento neste dia.',
    agendados: 'Agendados', cancelados: 'Cancelados', atendidos: 'Atendidos', faltas: 'Faltas',
    painelDica: 'Os números são calculados por um consumidor Kafka a partir dos eventos, não por consulta direta à agenda.',
    eventos: 'Eventos no Kafka (tópico agendamentos.eventos)', semEventos: 'Agende, cancele ou registre um atendimento para ver os eventos.',
    particao: 'partição', processado: 'processado', pendente: 'aguardando consumidor',
  },
  en: {
    titulo: 'Service Appointments',
    subtitulo: 'City of Vale Azul (fictitious)',
    demo: 'Demo: everything runs in your browser with fictitious data. The schedule resets on reload.',
    abas: { agendar: 'Book', meu: 'My booking', balcao: 'Front desk', painel: 'Dashboard', arquitetura: 'Architecture' },
    unidade: 'Office', servico: 'Service', dia: 'Day', horario: 'Time', vagas: (n: number) => `${n} spot${n > 1 ? 's' : ''}`,
    semHorarios: 'No times available on this day.', escolhaHorario: 'Pick a time',
    seusDados: 'Your details', nome: 'Full name', cpf: 'CPF (Brazilian ID)', email: 'E-mail', cpfDica: 'Try 529.982.247-25',
    confirmar: 'Confirm booking', enviando: 'Sending…',
    confirmado: 'Booking confirmed', guardeCodigo: 'Keep this code to look up or cancel your booking.',
    novo: 'Book another',
    codigo: 'Code', consultar: 'Look up', naoEncontrado: 'No booking with this code.',
    cancelar: 'Cancel booking', regraCancelamento: 'Cancellation is allowed up to 2 hours before.',
    status: { AGENDADO: 'Booked', CANCELADO: 'Cancelled', ATENDIDO: 'Attended', FALTOU: 'No-show' },
    compareceu: 'Attended', faltou: 'No-show', semAgendamentos: 'No bookings on this day.',
    agendados: 'Booked', cancelados: 'Cancelled', atendidos: 'Attended', faltas: 'No-shows',
    painelDica: 'These numbers are computed by a Kafka consumer from events, not by querying the schedule directly.',
    eventos: 'Kafka events (topic agendamentos.eventos)', semEventos: 'Book, cancel or record attendance to see events.',
    particao: 'partition', processado: 'processed', pendente: 'waiting for consumer',
  },
};

export type Textos = typeof TEXTOS.pt;

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly idioma = signal<Idioma>(I18n.inicial());
  readonly t = computed<Textos>(() => TEXTOS[this.idioma()]);

  usar(idioma: Idioma) {
    this.idioma.set(idioma);
    document.documentElement.lang = idioma === 'pt' ? 'pt-BR' : 'en';
    try { localStorage.setItem('agenda-idioma', idioma); } catch { /* sem armazenamento: só nesta sessão */ }
  }

  private static inicial(): Idioma {
    try {
      const salvo = localStorage.getItem('agenda-idioma');
      if (salvo === 'pt' || salvo === 'en') return salvo;
    } catch { /* ignora */ }
    return typeof navigator !== 'undefined' && navigator.language?.startsWith('pt') ? 'pt' : 'en';
  }
}

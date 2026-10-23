import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DemoAgendaApi } from '../api/demo-api';
import { AgendaApi, EventoKafka, PainelDia } from '../api/modelos';
import { dataLonga, diaLocal, horaLocal, proximosDiasUteis } from '../api/regras';
import { I18n } from '../i18n';

@Component({
  selector: 'app-painel',
  imports: [FormsModule],
  template: `
    <div class="filtros">
      <label class="campo">{{ t().dia }}
        <select [ngModel]="dia()" (ngModelChange)="dia.set($event)">
          @for (d of dias; track d) { <option [value]="d">{{ data(d) }}</option> }
        </select>
      </label>
    </div>
    <p class="dica">{{ t().painelDica }}</p>
    <div class="painel">
      @for (p of linhas(); track p.unidade.id) {
        <section class="cartao">
          <h3>{{ p.unidade.nome }}</h3>
          <div class="numeros">
            <div><strong>{{ p.agendados }}</strong><span>{{ t().agendados }}</span></div>
            <div><strong>{{ p.atendidos }}</strong><span>{{ t().atendidos }}</span></div>
            <div><strong>{{ p.faltas }}</strong><span>{{ t().faltas }}</span></div>
            <div><strong>{{ p.cancelados }}</strong><span>{{ t().cancelados }}</span></div>
          </div>
        </section>
      }
    </div>

    @if (temEventos) {
      <h2 class="titulo-secao">{{ t().eventos }}</h2>
      <ol class="eventos">
        @for (e of eventos(); track e.id) {
          <li [class.processado]="e.processado">
            <span class="mono tipo">{{ e.tipo }}</span>
            <span class="mono">{{ e.codigo }}</span>
            <span>{{ t().particao }} {{ e.particao }} · offset {{ e.offset }}</span>
            <span class="estado">{{ e.processado ? '✓ ' + t().processado : '… ' + t().pendente }}</span>
            <small>{{ hora(e.ocorridoEm) }}</small>
          </li>
        } @empty {
          <li class="vazio">{{ t().semEventos }}</li>
        }
      </ol>
    }
  `,
})
export class Painel {
  private readonly api = inject(AgendaApi);
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly dias = [diaLocal(new Date()), ...proximosDiasUteis(5).filter((d) => d !== diaLocal(new Date()))].slice(0, 5);
  protected readonly dia = signal(this.dias[0]);
  protected readonly linhas = signal<PainelDia[]>([]);
  protected readonly eventos = signal<EventoKafka[]>([]);
  protected readonly temEventos = !!this.api.eventos;

  constructor() {
    effect(() => { this.atualizar(this.dia()); });
    if (this.api instanceof DemoAgendaApi) {
      const sair = this.api.aoMudar(() => this.atualizar(this.dia()));
      inject(DestroyRef).onDestroy(sair);
    }
  }

  private atualizar(dia: string) {
    this.api.painel(dia).then((l) => this.linhas.set(l));
    this.eventos.set(this.api.eventos?.() ?? []);
  }

  protected data(dia: string) { return dataLonga(dia, this.i18n.idioma()); }
  protected hora(iso: string) { return horaLocal(iso, this.i18n.idioma()); }
}

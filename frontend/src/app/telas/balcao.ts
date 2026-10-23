import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgendaApi, Agendamento, ErroDeNegocio, Unidade } from '../api/modelos';
import { dataLonga, diaLocal, horaLocal, proximosDiasUteis } from '../api/regras';
import { I18n } from '../i18n';

/** Tela do atendente: lista do dia e registro de comparecimento. */
@Component({
  selector: 'app-balcao',
  imports: [FormsModule],
  template: `
    <div class="filtros">
      <label class="campo">{{ t().unidade }}
        <select [ngModel]="unidadeId()" (ngModelChange)="unidadeId.set($event)">
          @for (u of unidades(); track u.id) { <option [value]="u.id">{{ u.nome }}</option> }
        </select>
      </label>
      <label class="campo">{{ t().dia }}
        <select [ngModel]="dia()" (ngModelChange)="dia.set($event)">
          @for (d of dias; track d) { <option [value]="d">{{ data(d) }}</option> }
        </select>
      </label>
    </div>
    @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
    <div class="tabela-rolagem">
      <table>
        <thead><tr><th>{{ t().horario }}</th><th>{{ t().nome }}</th><th>{{ t().servico }}</th><th>Status</th><th></th></tr></thead>
        <tbody>
          @for (a of lista(); track a.codigo) {
            <tr>
              <td class="mono">{{ hora(a.inicio) }}</td>
              <td>{{ a.cidadaoNome }}<br><small class="mono">{{ a.cpfMascarado }}</small></td>
              <td>{{ a.servico.nome }}</td>
              <td><span class="status" [attr.data-status]="a.status">{{ t().status[a.status] }}</span></td>
              <td class="acoes">
                @if (a.status === 'AGENDADO') {
                  <button class="pequeno" (click)="registrar(a, true)">{{ t().compareceu }}</button>
                  <button class="pequeno secundario" (click)="registrar(a, false)">{{ t().faltou }}</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="vazio">{{ t().semAgendamentos }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class Balcao {
  private readonly api = inject(AgendaApi);
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly dias = [diaLocal(new Date()), ...proximosDiasUteis(5).filter((d) => d !== diaLocal(new Date()))].slice(0, 5);
  protected readonly unidades = signal<Unidade[]>([]);
  protected readonly unidadeId = signal('1');
  protected readonly dia = signal(this.dias[0]);
  protected readonly lista = signal<Agendamento[]>([]);
  protected readonly erro = signal('');

  constructor() {
    this.api.unidades().then((u) => this.unidades.set(u));
    effect(() => { this.carregar(this.unidadeId(), this.dia()); });
  }

  private carregar(unidade: string, dia: string) {
    this.api.daUnidade(unidade, dia).then((l) => this.lista.set(l));
  }

  protected hora(iso: string) { return horaLocal(iso, this.i18n.idioma()); }
  protected data(dia: string) { return dataLonga(dia, this.i18n.idioma()); }

  protected async registrar(a: Agendamento, compareceu: boolean) {
    this.erro.set('');
    try {
      await this.api.registrarComparecimento(a.codigo, compareceu);
      this.carregar(this.unidadeId(), this.dia());
    } catch (e) {
      this.erro.set(e instanceof ErroDeNegocio ? e.message : 'Erro inesperado.');
    }
  }
}

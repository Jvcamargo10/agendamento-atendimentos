import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgendaApi, Agendamento, ErroDeNegocio } from '../api/modelos';
import { dataLonga, horaLocal } from '../api/regras';
import { I18n } from '../i18n';

@Component({
  selector: 'app-meu-agendamento',
  imports: [FormsModule],
  template: `
    <form class="linha-busca" (ngSubmit)="consultar()">
      <label class="campo">{{ t().codigo }}<input name="codigo" [(ngModel)]="codigo" maxlength="8" class="mono" placeholder="ABCD2345"></label>
      <button type="submit" [disabled]="codigo.trim().length < 8">{{ t().consultar }}</button>
    </form>
    @if (naoEncontrado()) { <p class="vazio">{{ t().naoEncontrado }}</p> }
    @if (agendamento(); as a) {
      <section class="cartao">
        <span class="status" [attr.data-status]="a.status">{{ t().status[a.status] }}</span>
        <dl class="resumo">
          <dt>{{ t().codigo }}</dt><dd class="mono">{{ a.codigo }}</dd>
          <dt>{{ t().nome }}</dt><dd>{{ a.cidadaoNome }} · {{ a.cpfMascarado }}</dd>
          <dt>{{ t().unidade }}</dt><dd>{{ a.unidade.nome }}</dd>
          <dt>{{ t().servico }}</dt><dd>{{ a.servico.nome }}</dd>
          <dt>{{ t().horario }}</dt><dd>{{ data(a.inicio) }}, {{ hora(a.inicio) }}</dd>
        </dl>
        @if (a.status === 'AGENDADO') {
          <p class="dica">{{ t().regraCancelamento }}</p>
          <button class="perigo" (click)="cancelar(a.codigo)">{{ t().cancelar }}</button>
        }
        @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
      </section>
    }
  `,
})
export class MeuAgendamento {
  private readonly api = inject(AgendaApi);
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected codigo = '';
  protected readonly agendamento = signal<Agendamento | null>(null);
  protected readonly naoEncontrado = signal(false);
  protected readonly erro = signal('');

  protected hora(iso: string) { return horaLocal(iso, this.i18n.idioma()); }
  protected data(iso: string) { return dataLonga(iso, this.i18n.idioma()); }

  protected async consultar() {
    this.erro.set('');
    const a = await this.api.buscar(this.codigo);
    this.agendamento.set(a);
    this.naoEncontrado.set(!a);
  }

  protected async cancelar(codigo: string) {
    try {
      this.agendamento.set(await this.api.cancelar(codigo));
    } catch (e) {
      this.erro.set(e instanceof ErroDeNegocio ? e.message : 'Erro inesperado.');
    }
  }
}

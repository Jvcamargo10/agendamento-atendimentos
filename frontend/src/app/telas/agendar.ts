import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgendaApi, Agendamento, ErroDeNegocio, Horario, Unidade } from '../api/modelos';
import { cpfValido, dataLonga, horaLocal, proximosDiasUteis } from '../api/regras';
import { I18n } from '../i18n';

@Component({
  selector: 'app-agendar',
  imports: [FormsModule],
  template: `
    @if (confirmado(); as a) {
      <section class="cartao confirmacao">
        <p class="selo">✓ {{ t().confirmado }}</p>
        <p class="codigo-grande">{{ a.codigo }}</p>
        <p>{{ t().guardeCodigo }}</p>
        <dl class="resumo">
          <dt>{{ t().unidade }}</dt><dd>{{ a.unidade.nome }}</dd>
          <dt>{{ t().servico }}</dt><dd>{{ a.servico.nome }}</dd>
          <dt>{{ t().horario }}</dt><dd>{{ data(a.inicio) }}, {{ hora(a.inicio) }}</dd>
          <dt>{{ t().cpf }}</dt><dd>{{ a.cpfMascarado }}</dd>
        </dl>
        <button (click)="recomecar()">{{ t().novo }}</button>
      </section>
    } @else {
      <div class="grade-agendar">
        <section>
          <label class="campo">{{ t().unidade }}
            <select [ngModel]="unidadeId()" (ngModelChange)="trocarUnidade($event)">
              @for (u of unidades(); track u.id) { <option [value]="u.id">{{ u.nome }}</option> }
            </select>
          </label>
          <label class="campo">{{ t().servico }}
            <select [ngModel]="servicoId()" (ngModelChange)="servicoId.set($event)">
              @for (s of servicosDaUnidade(); track s.id) { <option [value]="s.id">{{ s.nome }}</option> }
            </select>
          </label>

          <p class="rotulo">{{ t().dia }}</p>
          <div class="dias" role="radiogroup">
            @for (d of dias; track d) {
              <button type="button" class="chip" role="radio" [attr.aria-checked]="d === dia()" (click)="dia.set(d)">{{ data(d) }}</button>
            }
          </div>

          <p class="rotulo">{{ t().escolhaHorario }}</p>
          <div class="horarios">
            @for (h of horarios(); track h.vagaId) {
              <button type="button" class="horario" [attr.aria-pressed]="h.vagaId === vagaId()" (click)="vagaId.set(h.vagaId)">
                <strong>{{ hora(h.inicio) }}</strong><small>{{ t().vagas(h.disponiveis) }}</small>
              </button>
            } @empty {
              <p class="vazio">{{ t().semHorarios }}</p>
            }
          </div>
        </section>

        <form class="cartao" (ngSubmit)="confirmar()">
          <h2>{{ t().seusDados }}</h2>
          <label class="campo">{{ t().nome }}<input name="nome" [(ngModel)]="nome" required minlength="3" autocomplete="name"></label>
          <label class="campo">{{ t().cpf }}
            <input name="cpf" [(ngModel)]="cpf" required inputmode="numeric" placeholder="000.000.000-00"
                   [attr.aria-invalid]="cpf.length >= 11 && !cpfOk()">
            <small class="dica">{{ t().cpfDica }}</small>
          </label>
          <label class="campo">{{ t().email }}<input name="email" type="email" [(ngModel)]="email" required autocomplete="email"></label>
          @if (erro()) { <p class="erro" role="alert">{{ erro() }}</p> }
          <button type="submit" [disabled]="!podeEnviar() || enviando()">{{ enviando() ? t().enviando : t().confirmar }}</button>
        </form>
      </div>
    }
  `,
})
export class Agendar {
  private readonly api = inject(AgendaApi);
  private readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;

  // Dias que ainda têm horário no futuro (depois das 16h30, o dia de hoje já acabou).
  protected readonly dias = proximosDiasUteis(11).filter((d) => new Date(`${d}T16:30:00-03:00`) > new Date()).slice(0, 10);
  protected readonly unidades = signal<Unidade[]>([]);
  protected readonly unidadeId = signal('1');
  protected readonly servicoId = signal('1');
  protected readonly dia = signal(this.dias[0]);
  protected readonly horarios = signal<Horario[]>([]);
  protected readonly vagaId = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly erro = signal('');
  protected readonly confirmado = signal<Agendamento | null>(null);
  protected nome = '';
  protected cpf = '';
  protected email = '';

  protected readonly servicosDaUnidade = computed(() => this.unidades().find((u) => u.id === this.unidadeId())?.servicos ?? []);

  constructor() {
    this.api.unidades().then((u) => this.unidades.set(u));
    effect(() => {
      const [u, s, d] = [this.unidadeId(), this.servicoId(), this.dia()];
      this.vagaId.set(null);
      this.api.horarios(u, s, d).then((h) => this.horarios.set(h));
    });
  }

  protected trocarUnidade(id: string) {
    this.unidadeId.set(id);
    const servicos = this.unidades().find((u) => u.id === id)?.servicos ?? [];
    if (!servicos.some((s) => s.id === this.servicoId())) this.servicoId.set(servicos[0]?.id ?? '');
  }

  protected cpfOk() { return cpfValido(this.cpf); }
  protected podeEnviar() { return !!this.vagaId() && this.nome.trim().length >= 3 && this.cpfOk() && this.email.includes('@'); }
  protected hora(iso: string) { return horaLocal(iso, this.i18n.idioma()); }
  protected data(dia: string) { return dataLonga(dia, this.i18n.idioma()); }

  protected async confirmar() {
    if (!this.podeEnviar()) return;
    this.enviando.set(true);
    this.erro.set('');
    try {
      this.confirmado.set(await this.api.agendar({ vagaId: this.vagaId()!, nome: this.nome, cpf: this.cpf, email: this.email }));
    } catch (e) {
      this.erro.set(e instanceof ErroDeNegocio ? e.message : 'Erro inesperado. Tente novamente.');
      this.api.horarios(this.unidadeId(), this.servicoId(), this.dia()).then((h) => this.horarios.set(h));
    } finally {
      this.enviando.set(false);
    }
  }

  protected recomecar() {
    this.confirmado.set(null);
    this.nome = this.cpf = this.email = '';
    this.api.horarios(this.unidadeId(), this.servicoId(), this.dia()).then((h) => this.horarios.set(h));
  }
}

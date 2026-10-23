import { Component, inject, signal } from '@angular/core';
import { environment } from '../environments/environment';
import { I18n, Idioma } from './i18n';
import { Agendar } from './telas/agendar';
import { Arquitetura } from './telas/arquitetura';
import { Balcao } from './telas/balcao';
import { MeuAgendamento } from './telas/meu-agendamento';
import { Painel } from './telas/painel';

type Aba = 'agendar' | 'meu' | 'balcao' | 'painel' | 'arquitetura';

@Component({
  selector: 'app-root',
  imports: [Agendar, MeuAgendamento, Balcao, Painel, Arquitetura],
  template: `
    <header class="cabecalho">
      <div class="marca">
        <img src="favicon.svg" alt="" width="40" height="40">
        <div><h1>{{ t().titulo }}</h1><p>{{ t().subtitulo }}</p></div>
      </div>
      <div class="idiomas" role="group" aria-label="Idioma / Language">
        @for (i of idiomas; track i) {
          <button type="button" [attr.aria-pressed]="i18n.idioma() === i" (click)="i18n.usar(i)">{{ i.toUpperCase() }}</button>
        }
      </div>
    </header>
    @if (demo) { <p class="faixa-demo">{{ t().demo }}</p> }
    <nav class="abas" role="tablist">
      @for (a of abas; track a) {
        <button type="button" role="tab" [attr.aria-selected]="aba() === a" (click)="aba.set(a)">{{ t().abas[a] }}</button>
      }
    </nav>
    <main>
      @switch (aba()) {
        @case ('agendar') { <app-agendar /> }
        @case ('meu') { <app-meu-agendamento /> }
        @case ('balcao') { <app-balcao /> }
        @case ('painel') { <app-painel /> }
        @case ('arquitetura') { <app-arquitetura /> }
      }
    </main>
    <footer><a href="https://github.com/Jvcamargo10/agendamento-atendimentos">GitHub</a> · Java · Spring Boot · GraphQL · Kafka · Kubernetes</footer>
  `,
})
export class App {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly demo = environment.demo;
  protected readonly idiomas: Idioma[] = ['pt', 'en'];
  protected readonly abas: Aba[] = ['agendar', 'meu', 'balcao', 'painel', 'arquitetura'];
  protected readonly aba = signal<Aba>('agendar');
}

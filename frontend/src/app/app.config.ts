import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { environment } from '../environments/environment';
import { DemoAgendaApi } from './api/demo-api';
import { GraphqlAgendaApi } from './api/graphql-api';
import { AgendaApi } from './api/modelos';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    { provide: AgendaApi, useFactory: () => environment.demo ? new DemoAgendaApi() : new GraphqlAgendaApi(environment.graphqlUrl) },
  ],
};

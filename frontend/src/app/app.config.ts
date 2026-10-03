import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';

/*
 * Configuration de l'application Angular standalone.
 *
 * Dans l'architecture standalone (Angular 17+), on n'utilise plus NgModule.
 * Tous les providers sont déclarés ici et bootstrappés dans main.ts.
 *
 * provideHttpClient() : enregistre HttpClient comme service injectable.
 * Sans ça, HttpClient ne peut pas être injecté dans ArticleService.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
  ]
};

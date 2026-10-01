import { Routes } from '@angular/router';
import { autenticadoGuard, perfilGuard } from './core/auth/auth.guard';

/**
 * Mapa de telas.
 *
 * <p>Cada rota carrega o proprio componente sob demanda: o app antigo entregava as
 * onze abas em um HTML so, e tudo era baixado mesmo sem ser aberto.</p>
 *
 * <p>O perfil FAMILIAR so alcanca o painel e o proprio perfil. O guard aqui e
 * conveniencia de navegacao; quem realmente decide e o backend.</p>
 */
export const routes: Routes = [
  {
    path: 'entrar',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'painel' },
      {
        path: 'painel',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'perfil',
        loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'plano',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/plan/plan').then((m) => m.Plan),
      },
      {
        path: 'gastos',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/expenses/expenses').then((m) => m.Expenses),
      },
      {
        path: 'calendario',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/calendar/calendar').then((m) => m.Calendar),
      },
      {
        path: 'compras',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/shopping/shopping').then((m) => m.Shopping),
      },
      {
        path: 'lugares',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/places/places').then((m) => m.Places),
      },
      {
        path: 'filmes',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/movies/movies').then((m) => m.Movies),
      },
      {
        path: 'feed',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/feed/feed').then((m) => m.Feed),
      },
      {
        path: 'premios',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/rewards/rewards').then((m) => m.Rewards),
      },
      {
        path: 'metas',
        canActivate: [perfilGuard('ADMIN', 'BEATRIZ')],
        loadComponent: () => import('./features/goals/goals').then((m) => m.Goals),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

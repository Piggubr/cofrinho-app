import { Routes } from '@angular/router';
import { autenticadoGuard, perfilGuard } from './core/auth/auth.guard';

/**
 * Mapa de telas.
 *
 * <p>Cada rota carrega o proprio componente sob demanda: o app antigo entregava as
 * onze abas em um HTML so, e tudo era baixado mesmo sem ser aberto.</p>
 *
 * <p>O MEMBRO da familia so alcanca o painel e o proprio perfil. O guard aqui e
 * conveniencia de navegacao; quem realmente decide e o backend.</p>
 */
export const routes: Routes = [
  {
    path: 'entrar',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  // Abertas, sem login: precisam ser lidas antes de criar a conta.
  {
    path: 'privacidade',
    loadComponent: () => import('./features/legal/privacidade').then((m) => m.Privacidade),
  },
  {
    path: 'termos',
    loadComponent: () => import('./features/legal/termos').then((m) => m.Termos),
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
        path: 'familia',
        loadComponent: () => import('./features/family/family').then((m) => m.Family),
      },
      {
        path: 'plano',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/plan/plan').then((m) => m.Plan),
      },
      {
        path: 'gastos',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/expenses/expenses').then((m) => m.Expenses),
      },
      {
        path: 'calendario',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/calendar/calendar').then((m) => m.Calendar),
      },
      {
        path: 'compras',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/shopping/shopping').then((m) => m.Shopping),
      },
      {
        path: 'lugares',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/places/places').then((m) => m.Places),
      },
      {
        path: 'filmes',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/movies/movies').then((m) => m.Movies),
      },
      {
        path: 'feed',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/feed/feed').then((m) => m.Feed),
      },
      {
        path: 'premios',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/rewards/rewards').then((m) => m.Rewards),
      },
      {
        path: 'metas',
        canActivate: [perfilGuard('ADMIN', 'TITULAR')],
        loadComponent: () => import('./features/goals/goals').then((m) => m.Goals),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

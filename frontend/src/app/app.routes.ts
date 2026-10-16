import { Routes } from '@angular/router';
import { autenticadoGuard, perfilGuard, termosGuard } from './core/auth/auth.guard';

/**
 * Mapa de telas.
 *
 * <p>Cada rota carrega o proprio componente sob demanda: o app antigo entregava as
 * onze abas em um HTML so, e tudo era baixado mesmo sem ser aberto.</p>
 *
 * <p>Todo papel da familia alcanca todas as telas, cada um com os proprios dados; so o
 * Premium e do titular. O guard aqui e conveniencia de navegacao; quem realmente decide e
 * o backend.</p>
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
  // Termos novos ainda nao aceitos param aqui antes de qualquer outra tela.
  // Aberta: o link do e-mail pode ser aberto sem sessao, em outro aparelho.
  {
    path: 'confirmar-email',
    loadComponent: () => import('./features/confirmar-email/confirmar-email').then((m) => m.ConfirmarEmail),
  },
  {
    path: 'aceite',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./features/aceite/aceite').then((m) => m.Aceite),
  },
  // Como quer ser chamado e data de nascimento, uma vez, antes do resto.
  {
    path: 'sobre-voce',
    canActivate: [autenticadoGuard],
    loadComponent: () => import('./features/sobre-voce/sobre-voce').then((m) => m.SobreVoce),
  },
  // Informar o documento (opcional), oferecido pelo Perfil.

  {
    path: 'cpf',
    canActivate: [autenticadoGuard, termosGuard],
    loadComponent: () => import('./features/cpf/cpf').then((m) => m.Cpf),
  },
  {
    path: '',
    canActivate: [autenticadoGuard],
    // Filhos: roda depois de a sessao ser restaurada pelo guard acima.
    canActivateChild: [termosGuard],
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
        loadComponent: () => import('./features/expenses/expenses').then((m) => m.Expenses),
      },
      {
        path: 'receitas',
        loadComponent: () => import('./features/incomes/incomes').then((m) => m.Incomes),
      },
      {
        path: 'relatorios',
        loadComponent: () => import('./features/reports/reports').then((m) => m.Reports),
      },
      {
        path: 'contas-e-cartoes',
        loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
      },
      {
        path: 'contas-fixas',
        loadComponent: () => import('./features/bills/bills').then((m) => m.Bills),
      },
      {
        path: 'orcamentos',
        loadComponent: () => import('./features/budgets/budgets').then((m) => m.Budgets),
      },
      {
        path: 'calendario',
        loadComponent: () => import('./features/calendar/calendar').then((m) => m.Calendar),
      },
      {
        path: 'compras',
        loadComponent: () => import('./features/shopping/shopping').then((m) => m.Shopping),
      },
      {
        path: 'lugares',
        loadComponent: () => import('./features/places/places').then((m) => m.Places),
      },
      {
        path: 'filmes',
        loadComponent: () => import('./features/movies/movies').then((m) => m.Movies),
      },
      {
        path: 'feed',
        loadComponent: () => import('./features/feed/feed').then((m) => m.Feed),
      },
      {
        path: 'premios',
        loadComponent: () => import('./features/rewards/rewards').then((m) => m.Rewards),
      },
      {
        path: 'planejar',
        loadComponent: () => import('./features/plan-hub/plan-hub').then((m) => m.PlanHub),
      },
      // Minhas metas: pessoais, entao valem para todos os perfis, membro incluido.
      {
        path: 'metas',
        loadComponent: () =>
          import('./features/minhas-metas/minhas-metas').then((m) => m.MinhasMetas),
      },
      {
        path: 'girls-math',
        loadComponent: () => import('./features/girls-math/girls-math').then((m) => m.GirlsMath),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

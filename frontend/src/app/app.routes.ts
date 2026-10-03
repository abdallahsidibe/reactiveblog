import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'articles',
    pathMatch: 'full'
  },
  {
    path: 'articles',
    loadComponent: () =>
      import('./features/article-list/article-list.component')
        .then(m => m.ArticleListComponent)
  },
  {
    path: 'articles/new',
    loadComponent: () =>
      import('./features/article-form/article-form.component')
        .then(m => m.ArticleFormComponent)
  },
  {
    path: 'articles/:id',
    loadComponent: () =>
      import('./features/article-detail/article-detail.component')
        .then(m => m.ArticleDetailComponent)
  },
  {
    path: 'articles/:id/edit',
    loadComponent: () =>
      import('./features/article-form/article-form.component')
        .then(m => m.ArticleFormComponent)
  },
  {
    path: '**',
    redirectTo: 'articles'
  }
];

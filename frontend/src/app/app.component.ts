import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <nav>
      <a routerLink="/articles">Reactive Blog</a>
      <a routerLink="/articles">Articles</a>
      <a routerLink="/articles/new">Nouvel article</a>
    </nav>
    <main>
      <router-outlet />
    </main>
  `,
  styles: [`
    nav {
      display: flex;
      gap: 1.5rem;
      padding: 1rem 2rem;
      background: #1e293b;
      align-items: center;
    }
    nav a {
      color: #e2e8f0;
      text-decoration: none;
      font-weight: 500;
    }
    nav a:first-child {
      font-size: 1.2rem;
      font-weight: 700;
      margin-right: auto;
      color: #38bdf8;
    }
    main {
      max-width: 900px;
      margin: 2rem auto;
      padding: 0 1rem;
    }
  `]
})
export class AppComponent {}

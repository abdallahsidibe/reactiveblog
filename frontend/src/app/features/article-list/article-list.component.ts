import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ArticleService } from '../../core/services/article.service';
import { Article } from '../../core/models/article.model';

/*
 * ArticleListComponent — liste des articles + recherche réactive.
 *
 * La recherche utilise RxJS pour créer un pipeline réactif :
 *
 *   searchControl.valueChanges          → Observable<string> (chaque frappe)
 *     .pipe(
 *       debounceTime(300)               → attend 300ms après la dernière frappe
 *       distinctUntilChanged()          → ignore si la valeur n'a pas changé
 *       switchMap(keyword → HTTP)       → annule la requête précédente si nouvelle frappe
 *     )                                 → Observable<Article[]>
 *
 * switchMap est crucial : si l'utilisateur tape "sp", puis "spr" rapidement,
 * switchMap annule la requête "sp" et ne garde que "spr".
 * Sans lui, les réponses pourraient arriver dans le désordre.
 */
@Component({
  selector: 'app-article-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  template: `
    <div class="header">
      <h1>Articles</h1>
      <input
        [formControl]="searchControl"
        placeholder="Rechercher un article..."
        class="search-input"
      />
    </div>

    <ng-container *ngIf="articles$ | async as articles">
      <p *ngIf="articles.length === 0" class="empty">Aucun article trouvé.</p>

      <div class="article-grid">
        <article *ngFor="let article of articles" class="card">
          <h2>
            <a [routerLink]="['/articles', article.id]">{{ article.title }}</a>
          </h2>
          <p class="meta">
            Par <strong>{{ article.author }}</strong> —
            {{ article.createdAt | date:'dd/MM/yyyy' }}
          </p>
          <p class="excerpt">{{ article.content | slice:0:150 }}...</p>
          <div class="actions">
            <a [routerLink]="['/articles', article.id, 'edit']" class="btn-edit">Modifier</a>
            <button (click)="delete(article.id)" class="btn-delete">Supprimer</button>
          </div>
        </article>
      </div>
    </ng-container>
  `,
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    h1 { margin: 0; }
    .search-input { padding: .5rem 1rem; border: 1px solid #cbd5e1; border-radius: 6px; width: 260px; font-size: 1rem; }
    .empty { color: #64748b; }
    .article-grid { display: grid; gap: 1rem; }
    .card { border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; }
    .card h2 { margin: 0 0 .4rem; font-size: 1.1rem; }
    .card h2 a { text-decoration: none; color: #0f172a; }
    .card h2 a:hover { color: #0284c7; }
    .meta { color: #64748b; font-size: .85rem; margin: 0 0 .6rem; }
    .excerpt { color: #475569; font-size: .9rem; margin: 0 0 1rem; }
    .actions { display: flex; gap: .5rem; }
    .btn-edit { padding: .3rem .8rem; background: #0284c7; color: #fff; border-radius: 4px; text-decoration: none; font-size: .85rem; }
    .btn-delete { padding: .3rem .8rem; background: #dc2626; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: .85rem; }
  `]
})
export class ArticleListComponent implements OnInit {

  searchControl = new FormControl('');
  articles$!: Observable<Article[]>;

  constructor(private articleService: ArticleService) {}

  ngOnInit(): void {
    /*
     * Pipeline RxJS de recherche réactive :
     *
     * startWith('') → émet une valeur initiale vide pour déclencher findAll()
     *                 au chargement du composant (sans attendre une frappe)
     */
    this.articles$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(keyword =>
        keyword && keyword.trim().length > 0
          ? this.articleService.search(keyword.trim())
          : this.articleService.findAll()
      )
    );
  }

  delete(id: number): void {
    if (!confirm('Confirmer la suppression ?')) return;
    this.articleService.delete(id).subscribe(() => {
      // Recharge la liste après suppression
      this.searchControl.setValue(this.searchControl.value ?? '');
    });
  }
}

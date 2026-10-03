import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { ArticleService } from '../../core/services/article.service';
import { Article } from '../../core/models/article.model';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-article-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <ng-container *ngIf="article$ | async as article">
      <a routerLink="/articles" class="back">← Retour</a>
      <article class="detail">
        <h1>{{ article.title }}</h1>
        <p class="meta">
          Par <strong>{{ article.author }}</strong> —
          Publié le {{ article.createdAt | date:'dd/MM/yyyy à HH:mm' }}
        </p>
        <div class="content">{{ article.content }}</div>
        <div class="actions">
          <a [routerLink]="['/articles', article.id, 'edit']" class="btn-edit">Modifier</a>
          <button (click)="delete(article.id)" class="btn-delete">Supprimer</button>
        </div>
      </article>
    </ng-container>
  `,
  styles: [`
    .back { color: #0284c7; text-decoration: none; display: inline-block; margin-bottom: 1rem; }
    .detail { border: 1px solid #e2e8f0; border-radius: 8px; padding: 2rem; }
    h1 { margin: 0 0 .5rem; }
    .meta { color: #64748b; font-size: .9rem; margin: 0 0 1.5rem; }
    .content { line-height: 1.7; white-space: pre-wrap; margin-bottom: 2rem; }
    .actions { display: flex; gap: .5rem; }
    .btn-edit { padding: .4rem 1rem; background: #0284c7; color: #fff; border-radius: 4px; text-decoration: none; }
    .btn-delete { padding: .4rem 1rem; background: #dc2626; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
  `]
})
export class ArticleDetailComponent implements OnInit {

  article$!: Observable<Article>;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private articleService: ArticleService
  ) {}

  ngOnInit(): void {
    /*
     * route.paramMap émet chaque fois que l'URL change.
     * switchMap(params → HTTP) annule la requête précédente si l'id change
     * avant que la réponse arrive (navigation rapide entre articles).
     */
    this.article$ = this.route.paramMap.pipe(
      switchMap(params => {
        const id = Number(params.get('id'));
        return this.articleService.findById(id);
      })
    );
  }

  delete(id: number): void {
    if (!confirm('Confirmer la suppression ?')) return;
    this.articleService.delete(id).subscribe(() => {
      this.router.navigate(['/articles']);
    });
  }
}

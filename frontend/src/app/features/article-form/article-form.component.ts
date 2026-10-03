import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { ArticleService } from '../../core/services/article.service';

/*
 * ArticleFormComponent — formulaire réactif pour créer et modifier un article.
 *
 * Angular Reactive Forms :
 * - FormGroup  : groupe de champs liés
 * - FormControl: un champ individuel avec validators
 * - Validators : validations synchrones (required, minLength, maxLength)
 *
 * Le même composant gère la création et l'édition :
 * - si l'URL est /articles/new     → mode création
 * - si l'URL est /articles/:id/edit → mode édition (pré-rempli)
 */
@Component({
  selector: 'app-article-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="form-container">
      <h1>{{ isEditMode ? 'Modifier l\'article' : 'Nouvel article' }}</h1>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">

        <div class="field">
          <label for="title">Titre</label>
          <input id="title" formControlName="title" type="text" />
          <span class="error" *ngIf="f['title'].invalid && f['title'].touched">
            {{ getError('title') }}
          </span>
        </div>

        <div class="field">
          <label for="author">Auteur</label>
          <input id="author" formControlName="author" type="text" />
          <span class="error" *ngIf="f['author'].invalid && f['author'].touched">
            {{ getError('author') }}
          </span>
        </div>

        <div class="field">
          <label for="content">Contenu</label>
          <textarea id="content" formControlName="content" rows="10"></textarea>
          <span class="error" *ngIf="f['content'].invalid && f['content'].touched">
            Le contenu est obligatoire.
          </span>
        </div>

        <div class="buttons">
          <a routerLink="/articles" class="btn-cancel">Annuler</a>
          <button type="submit" [disabled]="form.invalid" class="btn-submit">
            {{ isEditMode ? 'Enregistrer' : 'Créer' }}
          </button>
        </div>

      </form>
    </div>
  `,
  styles: [`
    .form-container { max-width: 640px; }
    h1 { margin-bottom: 1.5rem; }
    .field { display: flex; flex-direction: column; gap: .4rem; margin-bottom: 1.2rem; }
    label { font-weight: 600; color: #334155; }
    input, textarea {
      padding: .5rem .75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 1rem;
      font-family: inherit;
    }
    input:focus, textarea:focus { outline: 2px solid #0284c7; border-color: transparent; }
    .error { color: #dc2626; font-size: .85rem; }
    .buttons { display: flex; gap: .75rem; margin-top: 1.5rem; }
    .btn-cancel { padding: .5rem 1.2rem; border: 1px solid #cbd5e1; border-radius: 6px; text-decoration: none; color: #475569; }
    .btn-submit { padding: .5rem 1.2rem; background: #0284c7; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 1rem; }
    .btn-submit:disabled { background: #93c5fd; cursor: not-allowed; }
  `]
})
export class ArticleFormComponent implements OnInit {

  form!: FormGroup;
  isEditMode = false;
  private articleId?: number;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private articleService: ArticleService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      title:   ['', [Validators.required, Validators.minLength(3), Validators.maxLength(255)]],
      author:  ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', Validators.required],
    });

    // Détecte le mode édition via l'URL (:id/edit)
    this.route.paramMap.pipe(
      switchMap(params => {
        const id = params.get('id');
        if (id && id !== 'new') {
          this.isEditMode = true;
          this.articleId = Number(id);
          return this.articleService.findById(this.articleId);
        }
        return of(null);
      })
    ).subscribe(article => {
      if (article) {
        this.form.patchValue({
          title:   article.title,
          author:  article.author,
          content: article.content,
        });
      }
    });
  }

  // Raccourci pour accéder aux controls dans le template
  get f() { return this.form.controls; }

  getError(field: string): string {
    const control = this.f[field];
    if (control.hasError('required'))   return `Ce champ est obligatoire.`;
    if (control.hasError('minlength'))  return `Minimum ${control.errors?.['minlength'].requiredLength} caractères.`;
    if (control.hasError('maxlength'))  return `Maximum ${control.errors?.['maxlength'].requiredLength} caractères.`;
    return '';
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const request = this.form.value;
    const action$ = this.isEditMode && this.articleId
      ? this.articleService.update(this.articleId, request)
      : this.articleService.create(request);

    action$.subscribe(article => {
      this.router.navigate(['/articles', article.id]);
    });
  }
}

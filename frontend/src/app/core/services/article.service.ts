import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Article, ArticleRequest } from '../models/article.model';

/*
 * ArticleService — couche HTTP côté Angular.
 *
 * HttpClient retourne des Observable<T> (RxJS).
 * C'est l'équivalent Angular des Mono<T>/Flux<T> côté Spring :
 *
 *   Spring WebFlux    │  Angular RxJS
 *  ───────────────────┼─────────────────────────
 *   Mono<Article>     │  Observable<Article>
 *   Flux<Article>     │  Observable<Article[]>
 *   Mono<Void>        │  Observable<void>
 *
 * Comme en Reactor, un Observable ne fait rien tant qu'on ne s'y abonne pas.
 * L'abonnement se fait via le pipe async dans le template ou via .subscribe()
 * dans le composant.
 *
 * providedIn: 'root' → singleton, disponible partout sans module.
 */
@Injectable({ providedIn: 'root' })
export class ArticleService {

  private readonly apiUrl = '/api/articles';

  constructor(private http: HttpClient) {}

  // GET /api/articles
  findAll(): Observable<Article[]> {
    return this.http.get<Article[]>(this.apiUrl);
  }

  // GET /api/articles?search=keyword
  search(keyword: string): Observable<Article[]> {
    const params = new HttpParams().set('search', keyword);
    return this.http.get<Article[]>(this.apiUrl, { params });
  }

  // GET /api/articles/:id
  findById(id: number): Observable<Article> {
    return this.http.get<Article>(`${this.apiUrl}/${id}`);
  }

  // POST /api/articles
  create(request: ArticleRequest): Observable<Article> {
    return this.http.post<Article>(this.apiUrl, request);
  }

  // PUT /api/articles/:id
  update(id: number, request: ArticleRequest): Observable<Article> {
    return this.http.put<Article>(`${this.apiUrl}/${id}`, request);
  }

  // DELETE /api/articles/:id
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}

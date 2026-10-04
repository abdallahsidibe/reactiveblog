---
name: angular
description: Generates Angular 22 standalone components, services, forms and full features with Angular Material, signals, reactive forms and strict typing. Use when building or modifying Angular code.
disable-model-invocation: false
---

Implement Angular 22 code following these rules strictly.

**Argument:** $ARGUMENTS
_(format: `component <Name>` | `service <Name>` | `feature <name>` | `form <Name>` | `build`)_

---

## Absolute Rules

- **Standalone components** only — no NgModule ever
- **Signals** for all local state: `signal()`, `computed()`, `effect()`
- **Reactive Forms** with `FormBuilder` for all forms
- **CSS** only — never SCSS
- **Strict typing** — no `any`, generics on every `HttpClient` call
- `inject()` for dependency injection inside components and services
- API URLs always from `environment.ts`
- Errors displayed in real time under each field
- Submit button disabled while form is invalid

---

## `/angular component <ComponentName>`

Create a standalone component.

1. Read existing components in `src/app/components/` to match conventions
2. Create these files:

**`<name>.component.ts`**
```typescript
import { Component, signal, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-<name>',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './<name>.component.html',
  styleUrl: './<name>.component.css'
})
export class <Name>Component implements OnInit {
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {}
}
```

**`<name>.component.html`** — use Angular Material components
**`<name>.component.css`** — scoped styles

3. Register route in `app.routes.ts` if it's a page

---

## `/angular service <ServiceName>`

Create a typed HTTP service.

1. Read existing services in `src/app/services/` to match conventions
2. Create `<name>.service.ts`:

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class <Name>Service {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/<resource>`;

  getAll(): Observable<<Model>[]> {
    return this.http.get<<Model>[]>(this.baseUrl);
  }

  getById(id: number): Observable<<Model>> {
    return this.http.get<<Model>>(`${this.baseUrl}/${id}`);
  }

  create(payload: Create<Model>Request): Observable<<Model>> {
    return this.http.post<<Model>>(this.baseUrl, payload);
  }

  update(id: number, payload: Partial<<Model>>): Observable<<Model>> {
    return this.http.patch<<Model>>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
```

3. Add the TypeScript interface in `src/app/models/index.ts`

---

## `/angular form <ComponentName>`

Create a reactive form component with real-time validation.

```typescript
import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { debounceTime, switchMap, map, catchError } from 'rxjs/operators';

@Component({
  selector: 'app-<name>-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, /* Angular Material modules */],
  templateUrl: './<name>-form.component.html',
  styleUrl: './<name>-form.component.css'
})
export class <Name>FormComponent {
  private readonly fb = inject(FormBuilder);

  success = signal(false);
  serverError = signal<string | null>(null);

  form = this.fb.group({
    // Add fields with validators
    // Example:
    // amount: [null, [Validators.required, Validators.min(1)], [this.amountValidator()]],
    // reason: ['', [Validators.required, Validators.minLength(20)]],
  });

  // Async validator example
  private amountValidator() {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value) return of(null);
      return of(control.value).pipe(
        debounceTime(300),
        switchMap(value => /* service call */),
        map(result => result.valid ? null : { exceedsLimit: true }),
        catchError(() => of(null))
      );
    };
  }

  get f() { return this.form.controls; }

  submit(): void {
    if (this.form.invalid) return;
    // call service
  }
}
```

---

## `/angular feature <feature-name>`

Generate a complete feature: model + service + component + route.

Execute in order:
1. Add interfaces to `src/app/models/index.ts`
2. Create `src/app/services/<feature>.service.ts`
3. Create `src/app/components/<feature>/` (ts + html + css)
4. Register in `app.routes.ts`
5. Run `npm run build` and fix all errors

Component data-loading pattern:
```typescript
ngOnInit(): void {
  this.service.getAll().subscribe({
    next: (data) => { this.items.set(data); this.loading.set(false); },
    error: () => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); }
  });
}
```

---

## `/angular build`

1. Run `npm run build`
2. Fix every TypeScript and template error
3. Run `npm test -- --watch=false --browsers=ChromeHeadless`
4. Report results

---

## Angular Material — Quick Reference

| Need | Module |
|------|--------|
| Table | `MatTableModule` |
| Form field | `MatFormFieldModule` + `MatInputModule` |
| Button | `MatButtonModule` |
| Icon | `MatIconModule` |
| Badge / chip | `MatChipsModule` |
| Select | `MatSelectModule` |
| Radio | `MatRadioModule` |
| Pagination | `MatPaginatorModule` |
| Spinner | `MatProgressSpinnerModule` |
| Snackbar | `MatSnackBarModule` |
| Dialog | `MatDialogModule` |

Import each module in the component's `imports: []` array.

---

## Environments

```typescript
// environment.ts (production — nginx proxy)
export const environment = { production: true, apiUrl: '' };

// environment.development.ts
export const environment = { production: false, apiUrl: 'http://localhost:8080' };
```

---

## Project Structure

```
src/app/
├── components/<feature>/
│   ├── <feature>.component.ts
│   ├── <feature>.component.html
│   └── <feature>.component.css
├── services/
│   └── <feature>.service.ts
├── models/
│   └── index.ts
├── app.routes.ts
└── environments/
    ├── environment.ts
    └── environment.development.ts
```

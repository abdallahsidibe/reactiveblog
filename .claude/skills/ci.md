# Créer le workflow CI (GitHub Actions)

Génère le fichier `.github/workflows/ci.yml` adapté au projet courant.

## Analyse préalable

Avant de générer, lire :
- `pom.xml` ou `package.json` → identifier le build tool et les commandes de test
- `docker-compose*.yml` → identifier les services requis (BDD, cache…)
- `README.md` → récupérer les prérequis (Java, Node, versions)

## Structure cible

```
.github/workflows/ci.yml
```

## Déclencheurs standards

```yaml
on:
  push:
    branches: [develop, "feature/**"]
  pull_request:
    branches: [develop, main]
```

## Jobs à inclure

### Backend (Java / Maven)
- `actions/setup-java` avec la version détectée dans `pom.xml`
- Cache `~/.m2/repository` via `actions/cache`
- `mvn verify` (compile + test en une seule commande)
- Publication du rapport de test (`junit-results`)

### Frontend (Node / Angular)
- `actions/setup-node` avec la version détectée dans `package.json`
- Cache `~/.npm`
- `npm ci` puis `ng build --configuration=production`
- `ng test --watch=false --browsers=ChromeHeadless` si des tests existent

### Services (si détectés dans docker-compose)
- PostgreSQL → `services: postgres` avec image et variables d'env
- Redis → `services: redis` avec image et healthcheck

## Règles de génération

- Utiliser les **versions épinglées** pour les actions (`actions/checkout@v4`, pas `@latest`)
- Toujours ajouter un `timeout-minutes` sur les jobs (ex: 15)
- Ne pas stocker de secrets en clair — utiliser `${{ secrets.NOM_SECRET }}`
- Nommer les steps de façon lisible (pas "Run step 1")
- Ajouter `continue-on-error: false` implicitement (comportement par défaut, ne pas surcharger)

## Après génération

- Vérifier la syntaxe YAML (indentation, quotes)
- S'assurer que les versions Java/Node correspondent à `pom.xml` / `package.json`
- Rappeler à l'utilisateur d'ajouter les secrets nécessaires dans Settings → Secrets

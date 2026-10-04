# Créer le workflow CD (GitHub Actions)

Génère le fichier `.github/workflows/release.yml` et/ou `deploy.yml` adaptés au projet courant.

## Analyse préalable

Avant de générer, lire :
- `docker-compose*.yml` → identifier les services et images
- `README.md` → récupérer la stack et l'architecture
- `.github/workflows/ci.yml` s'il existe → réutiliser les mêmes versions et conventions

## Deux fichiers cibles

```
.github/workflows/release.yml   # Déclenché sur tag vX.Y.Z → build + push image Docker
.github/workflows/deploy.yml    # Déclenché manuellement ou après release → déploiement
```

## `release.yml` — Build et push image Docker

### Déclencheur
```yaml
on:
  push:
    tags: ["v*.*.*"]
```

### Jobs
1. **build-and-push**
   - Checkout
   - `docker/login-action` → login sur le registry (GHCR ou DockerHub)
   - `docker/metadata-action` → extraire le tag depuis `github.ref`
   - `docker/build-push-action` → build multi-platform + push
   - Signer l'image si compliance requise (`cosign`)

### Secrets requis
- `REGISTRY_USERNAME` / `REGISTRY_PASSWORD` (DockerHub)
- ou utiliser `${{ secrets.GITHUB_TOKEN }}` pour GHCR (GitHub Container Registry)

## `deploy.yml` — Déploiement

### Déclencheur
```yaml
on:
  workflow_dispatch:          # Manuel
    inputs:
      environment:
        type: choice
        options: [staging, production]
  workflow_run:               # Automatique après release réussie
    workflows: ["Release"]
    types: [completed]
```

### Jobs
1. **deploy**
   - Environnement GitHub (`environment: staging` ou `production`) → approval gate
   - SSH vers le serveur cible ou appel API cloud (ECS, Cloud Run, Render…)
   - Health check post-déploiement
   - Notification (Slack, email) en cas d'échec

## Règles de génération

- **Ne jamais mettre de secrets en clair** — toujours `${{ secrets.NOM }}` ou `${{ vars.NOM }}`
- Utiliser les **environments GitHub** pour les approbations manuelles avant prod
- Épingler les actions à une version (`@v4`, jamais `@latest`)
- Séparer staging et production dans des jobs distincts avec `needs:`
- Ajouter `concurrency` pour éviter deux déploiements simultanés :
  ```yaml
  concurrency:
    group: deploy-${{ github.ref }}
    cancel-in-progress: false   # Ne pas annuler un déploiement en cours
  ```

## Gitflow et CD

| Branche / événement | Action CD |
|---|---|
| Push sur `develop` | Déploiement automatique en **staging** |
| Tag `v*` sur `main` | Build image + déploiement en **production** (avec approbation) |
| `hotfix/*` mergé dans `main` | Même pipeline que tag |

## Après génération

- Créer les environments `staging` et `production` dans GitHub Settings → Environments
- Ajouter les secrets dans chaque environment (pas au niveau repo)
- Tester d'abord sur `staging` avant d'activer le déploiement automatique en `production`

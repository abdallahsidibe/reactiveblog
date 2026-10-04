# Créer une Pull Request Gitflow

Crée une PR en respectant le workflow Gitflow du projet.

## Règles Gitflow

- `feature/*` → merge dans `develop`
- `release/*` → merge dans `develop` ET `main`
- `hotfix/*`  → merge dans `develop` ET `main`
- Ne jamais ouvrir une PR directement vers `main` depuis une feature

## Étapes

### 1. Déterminer la branche cible

```bash
git branch --show-current
```

- Si la branche courante commence par `feature/` → base = `develop`
- Si `release/` ou `hotfix/` → base = `main` (puis une seconde PR vers `develop`)

### 2. Vérifier les commits inclus

```bash
git log origin/<base>..HEAD --oneline
```

### 3. Créer la PR avec gh

```bash
gh pr create \
  --base <branche-cible> \
  --head <branche-courante> \
  --title "<type>(<scope>): <sujet>" \
  --body "$(cat <<'EOF'
## Summary

- <bullet 1>
- <bullet 2>
- <bullet 3>

## Test plan

- [ ] <étape 1>
- [ ] <étape 2>
- [ ] <étape 3>
EOF
)"
```

## Règles du titre

Format Conventional Commits : `<type>(<scope>): <sujet>`

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`

- Sujet en minuscules, impératif, sans point final, max 72 caractères
- Aucun `Co-Authored-By` ni mention d'outil IA

## Après la création

Affiche l'URL de la PR retournée par `gh pr create`.

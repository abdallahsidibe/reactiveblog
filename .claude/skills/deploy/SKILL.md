---
name: deploy
description: Deploy a Docker Compose app (Spring Boot + Angular) via GitHub Actions to a VPS. Covers VPS setup, GHCR image publishing, SSH secrets, and zero-downtime restart.
disable-model-invocation: false
---

Deploy the application using GitHub Actions → GHCR → VPS over SSH.

**Argument:** $ARGUMENTS
_(format: `setup-vps` | `add-secret` | `rollback` | `status` | `logs <service>`)_

---

## Architecture

```
push main
  → GitHub Actions
      ├── build backend image  → ghcr.io/<owner>/riskboard-backend:latest
      ├── build frontend image → ghcr.io/<owner>/riskboard-frontend:latest
      └── SSH to VPS
            └── docker compose -f docker-compose.prod.yml pull
                docker compose up -d --no-deps backend frontend
```

GitLab CI handles **build + test** only.
GitHub Actions handles **deploy** only.

---

## `/deploy setup-vps`

Run once on a fresh Ubuntu 24 VPS.

### 1. Connect to the VPS

```bash
ssh root@<VPS_IP>
```

### 2. Install Docker

```bash
apt update && apt upgrade -y
apt install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list
apt update && apt install -y docker-ce docker-compose-plugin
```

### 3. Create deploy user (ne jamais utiliser root en prod)

```bash
useradd -m -s /bin/bash deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh
# coller la clé publique GitHub Actions
echo "<SSH_PUBLIC_KEY>" >> /home/deploy/.ssh/authorized_keys
chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

### 4. Créer le dossier app + .env

```bash
mkdir -p /opt/riskboard
chown deploy:deploy /opt/riskboard

# Copier docker-compose.prod.yml depuis le repo (scp ou nano)
scp docker-compose.prod.yml deploy@<VPS_IP>:/opt/riskboard/

# Créer le .env sur le VPS
cat > /opt/riskboard/.env << 'EOF'
DB_NAME=riskboard
DB_USERNAME=riskboard
DB_PASSWORD=<mot_de_passe_fort>
GITHUB_REPOSITORY_OWNER=<votre_username_github>
EOF
chmod 600 /opt/riskboard/.env
```

### 5. Authentifier Docker au GHCR (pull des images privées)

```bash
# Sur le VPS, en tant que deploy
su - deploy
echo "<GITHUB_PAT>" | docker login ghcr.io -u <github_username> --password-stdin
```

> Le PAT doit avoir le scope `read:packages`.

---

## `/deploy add-secret`

Ajouter les secrets GitHub nécessaires :

| Secret | Valeur |
|--------|--------|
| `VPS_HOST` | IP publique du VPS |
| `VPS_USER` | `deploy` |
| `SSH_PRIVATE_KEY` | Clé privée SSH (correspond à la clé publique sur le VPS) |

```bash
# Générer une paire de clés dédiée au déploiement
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/riskboard_deploy

# Afficher la clé privée à coller dans GitHub Secrets
cat ~/.ssh/riskboard_deploy

# Afficher la clé publique à mettre sur le VPS
cat ~/.ssh/riskboard_deploy.pub
```

Dans GitHub → Settings → Secrets and variables → Actions → New repository secret.

---

## `/deploy rollback`

Revenir à la version précédente (image taguée avec le SHA du commit).

```bash
# Sur le VPS
cd /opt/riskboard

# Lister les images disponibles
docker images | grep riskboard

# Rollback backend vers un SHA précis
docker compose -f docker-compose.prod.yml stop backend
docker tag ghcr.io/<owner>/riskboard-backend:<SHA> ghcr.io/<owner>/riskboard-backend:latest
docker compose -f docker-compose.prod.yml up -d --no-deps backend
```

Ou via GitHub Actions → workflow_dispatch sur un SHA précis (à ajouter si besoin).

---

## `/deploy status`

```bash
# Sur le VPS
cd /opt/riskboard
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml top
```

Depuis n'importe où :

```bash
curl -f http://<VPS_IP>:4201/api/risklimits && echo "✅ OK" || echo "❌ DOWN"
```

---

## `/deploy logs <service>`

```bash
# Sur le VPS — remplacer <service> par backend, frontend ou postgres
docker compose -f docker-compose.prod.yml logs -f <service> --tail=100
```

---

## Fichiers clés

| Fichier | Rôle |
|---------|------|
| `.github/workflows/deploy.yml` | Pipeline GitHub Actions |
| `docker-compose.prod.yml` | Compose production (images GHCR) |
| `docker-compose.yml` | Compose développement local (build local) |
| `.gitlab-ci.yml` | Build + test (GitLab uniquement) |
| `/opt/riskboard/.env` | Secrets sur le VPS (jamais committé) |

---

## Ports exposés sur le VPS

| Service | Port | URL |
|---------|------|-----|
| Frontend (nginx) | 4201 | `http://<VPS_IP>:4201` |
| Backend (Spring Boot) | 8081 | `http://<VPS_IP>:8081` |
| Swagger UI | 8081 | `http://<VPS_IP>:8081/swagger-ui.html` |

> Ouvrir ces ports dans le pare-feu : `ufw allow 4201 && ufw allow 8081`

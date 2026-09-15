# FASE 7C-2 — Aprovisionamiento del servidor con Ansible

> **Objetivo**: configurar la VM Ubuntu 24.04 creada en la Fase 7C
> (Terraform) e instalar el stack completo: Docker + Compose V2,
> Nginx (reverse proxy), Certbot (HTTPS), Azure CLI, secretos desde
> Key Vault y despliegue del backend con `docker compose`.

---

## Estructura

| Archivo | Propósito |
|---|---|
| `ansible.cfg` | Configuración local (usuario remoto, SSH pipelining) |
| `hosts.ini` | Inventario: VM `botwap-prod` (20.88.28.27) |
| `playbook.yml` | Playbook principal (7 fases de aprovisionamiento) |
| `templates/botwap.env.j2` | Plantilla del `.env` con secretos del Key Vault |
| `templates/nginx-botwap.conf.j2` | Reverse proxy `:80 → 127.0.0.1:8080` |

## Prerrequisitos (antes de ejecutar)

1. **Clave SSH**: `infrastructure/terraform/id_ed25519_azure` (chmod 600).
   La fuente canónica vive en `~/.ssh/id_ed25519_azure`; la copia del
   repo está excluida de Git vía `.gitignore`.
2. **DNS**: registro A `api.johnkider.app → 20.88.28.27` creado en
   Name.com y ya propagado. El playbook **falla** antes de tocar
   Certbot si el dominio no resuelve a la VM (protege el rate limit
   de Let's Encrypt: 5 certificados/semana).
3. **Secretos en Key Vault** (`kv-botwap-prod-gmsy`):
   `whatsapp-access-token`, `whatsapp-app-secret`, `whatsapp-verify-token`,
   `postgres-password`, `whatsapp-phone-number-id`, `whatsapp-waba-id`,
   `whatsapp-app-id`.

## Ejecución

```bash
cd infrastructure/ansible

# Simulación (no cambia nada en la VM)
ansible-playbook playbook.yml --check --diff

# Aprovisionamiento real (10-20 min la primera vez: build de Maven en la VM)
ansible-playbook playbook.yml
```

## Qué hace el playbook (en orden)

1. **Sistema**: apt upgrade, Docker CE + Compose V2 (repo oficial),
   Nginx, Certbot + `python3-certbot-nginx`, Azure CLI (repo de MS).
2. **Key Vault**: `az login --identity` (Managed Identity, sin
   credenciales), lectura de los 7 secretos con `no_log: true`,
   validación de existencia y de valores no vacíos (fail-fast).
3. **Archivos**: `/opt/botwap/` + `docker-compose.yml` + contexto de
   build del backend (`pom.xml`, `src/`, `Dockerfile`).
4. **Entorno**: `/opt/botwap/.env` generado por plantilla — **0600,
   root:root**, valores inyectados del Key Vault, con `no_log`.
5. **Nginx**: sitio `botwap` en `sites-available` + symlink en
   `sites-enabled`, sitio default deshabilitado, `nginx -t`, restart.
6. **HTTPS**: Certbot `--nginx` no interactivo (solo si el cert no
   existe) + `certbot.timer` para renovación automática.
7. **Despliegue**: `docker compose up -d --build` y espera activa
   hasta que `/actuator/health` responda `"UP"`.

## Seguridad aplicada

- Secretos **jamás** en logs (`no_log: true` en toda tarea sensible).
- `.env` solo legible por root (`0600`); directorio `0750`.
- PostgreSQL y backend publicados **solo en loopback**
  (`127.0.0.1:5433` / `127.0.0.1:8080`); Nginx es la única puerta.
- La VM lee el Key Vault con su **Managed Identity** (rol
  *Key Vault Secrets User*); no hay tokens ni credenciales en disco.

## Después del playbook (manual)

1. Verificar salud: `curl https://api.johnkider.app/actuator/health`
2. Configurar el webhook en Meta Dashboard:
   `https://api.johnkider.app/webhook/whatsapp` con el *verify token*.

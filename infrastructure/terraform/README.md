# FASE 7C — Infraestructura base en Azure con Terraform

> **Objetivo**: crear la infraestructura base en Azure sobre la que, en fases
> posteriores, Ansible instalará Docker, Docker Compose, Nginx, Java y
> PostgreSQL y desplegará el backend Spring Boot de `botwap`.

Esta fase **solo genera infraestructura**. No instala ni configura software
algún dentro de la VM (`terraform apply` no se ejecuta en esta fase).

---

## Arquitectura objetivo

```
Internet
   |
   v
api.johnkider.app            (DNS + HTTPS: fase posterior)
   |
   v
Azure Static Public IP        (Standard SKU, zona redundante)
   |
   v
Nginx                         (fase posterior: Ansible)
   |
   v
Docker Compose                (fase posterior: Ansible)
   |
   +---- Spring Boot WebFlux   (backend/botwap)
   +---- PostgreSQL
   +---- Outbox Poller
   |
   v
Meta WhatsApp Cloud API
```

Los recursos realmente provisionados por esta fase:

| # | Recurso | Nombre | Comentario |
|---|---------|--------|------------|
| 1 | Resource Group | `botwap-prod-rg` | Grupo contenedor |
| 2 | Virtual Network | `vnet-botwap-prod` | `10.20.0.0/16` |
| 3 | Subnet | `subnet-botwap-prod` | `10.20.1.0/24` |
| 4 | Network Security Group | `nsg-botwap-prod` | Reglas SSH/HTTP/HTTPS |
| 5 | Public IP | `pip-botwap-prod` | Estática, Standard, zona redundante |
| 6 | Network Interface | `nic-botwap-prod` | Conecta VM, PIP y NSG |
| 7 | Linux VM | `vm-botwap-prod` | Ubuntu 24.04 LTS, `Standard_B2ats_v2` |
| 8 | Key Vault | `kv-botwap-prod-<suffix>` | RBAC, soft-delete, purge-protection |
| 9 | Role Assignment | — | VM MI → `Key Vault Secrets User` |

> El número exacto de recursos creados lo muestra `terraform plan`.

---

## Prerrequisitos

1. **Azure CLI** autenticado en la suscripción `Azure for Students`:
   ```bash
   az login
   az account set --subscription "Azure for Students"
   ```
   Verificar:
   ```bash
   az account show --query "{subscription:name,id:id,tenantId:tenantId}" -o table
   ```
   Debe mostrar `Azure for Students` con tenant
   `8d12c81d-4ade-42ab-8d1b-c86a8b028e16`.

2. **Terraform** >= 1.6.0. Verificar:
   ```bash
   terraform version
   ```

3. La autenticación de Terraform se realiza vía **Azure CLI** (no se
   hardcodean credenciales).

---

## Archivos

| Archivo | Propósito | Versionado |
|---------|-----------|------------|
| `versions.tf` | Versión mínima de Terraform y providers (`azurerm`, `random`) | Sí |
| `main.tf` | RG, VNet, Subnet, NSG, PIP, NIC, Key Vault, VM, Role Assignment | Sí |
| `variables.tf` | Variables con valores por defecto documentados | Sí |
| `outputs.tf` | Salidas informativas (nunca secretos) | Sí |
| `terraform.tfvars` | Valores reales (incluye la clave pública SSH real) | **No** (`.gitignore`) |
| `terraform.tfvars.example` | Plantilla con valores de ejemplo (placeholders) | Sí |
| `.gitignore` | Ignora estado, caché, variables reales y overrides | Sí |

---

## Configuración

1. Copia la plantilla de ejemplo y rellena con tus valores reales (nunca
   pegues secretos en `terraform.tfvars.example`):

   ```bash
   cp terraform.tfvars.example terraform.tfvars
   ```

2. En `terraform.tfvars`, establece tu **clave pública SSH** real:

   ```hcl
   ssh_public_key = "ssh-ed25519 AAAA... tu@email.com"
   ```

   > La clave pública utilizada en producción es la del par
   > `~/.ssh/id_ed25519_azure`. **Nunca** leas la clave privada.

3. Valores con defaults sensatos en `variables.tf` (sobre-escribibles en
   `terraform.tfvars`):

   | Variable | Default | Comentario |
   |----------|---------|------------|
   | `project_name` | `botwap` | Prefijo de nombres |
   | `environment` | `prod` | Entorno |
   | `location` | `eastus` | Región Azure |
   | `resource_group_name` | `botwap-prod-rg` | Resource Group |
   | `vm_size` | `Standard_B2ats_v2` | SKU de la VM |
   | `admin_username` | `azureadmin` | Usuario admin VM |
   | `vnet_address_space` | `["10.20.0.0/16"]` | CIDR VNet |
   | `subnet_address_prefix` | `10.20.1.0/24` | CIDR subnet |
   | `allowed_ssh_source_cidr` | `190.121.129.156/32` | IP admin SSH |

---

## Comandos habituales

```bash
# 1. Inicializar providers y backend (state local)
terraform init

# 2. Dar formato a los archivos .tf
terraform fmt -recursive

# 3. Validar sintaxis y configuración
terraform validate

# 4. Previsualizar los cambios (NO crea nada)
terraform plan
```

> ⚠️ **En esta fase NO se ejecuta `terraform apply`.** El despliegue real de
> recursos se hará en una fase posterior. `terraform plan` es el paso final.

---

## Reglas de red (NSG `nsg-botwap-prod`)

| Prioridad | Protocolo/Puerto | Origen | Acceso | Comentario |
|-----------|------------------|--------|--------|------------|
| 1000 | TCP 22 (SSH) | `190.121.129.156/32` | Allow | Solo el IP del admin |
| 1010 | TCP 80 (HTTP) | `0.0.0.0/0` | Allow | Entrada web (nginx) |
| 1020 | TCP 443 (HTTPS) | `0.0.0.0/0` | Allow | Entrada web (nginx TLS) |

**Explícitamente CERRADOS** (no hay regla de allow; la regla *implicit
deny* de Azure aplica):
- 5432 (PostgreSQL) — acceso solo interno desde Docker Compose
- 8080 (Spring Boot) — acceso solo interno desde Docker Compose
- 3000 — cerrado
- 5433 — cerrado

---

## Key Vault

- **Autorización**: Azure RBAC (`rbac_authorization_enabled = true`).
  No se usan *legacy access policies*.
- **Soft Delete**: habilitado, retención de 7 días.
- **Purge Protection**: habilitado.
- **SKU**: `standard`.
- **Secretos**: **NO** se crean en esta fase. Se agregarán posteriormente
  (`WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN`,
  etc.) cuando se configure el despliegue del backend.

---

## Managed Identity

- La VM usa **System Assigned Managed Identity** (`identity { type = "SystemAssigned" }`).
- Se crea un **Role Assignment** que otorga el rol **Key Vault Secrets User**
  sobre el Key Vault a la identidad de la VM.
- **NO** se otorgan los roles `Owner`, `Contributor`, `User Access Administrator`
  ni `Key Vault Administrator`.
- La identidad solo podrá **leer** secretos una vez creados en una fase
  posterior.

---

## Seguridad

- SSH **solo** desde `190.121.129.156/32`.
- Password authentication **deshabilitado** (`disable_password_authentication = true`).
- Autenticación SSH **exclusivamente** por clave pública.
- La clave privada `~/.ssh/id_ed25519_azure` **nunca** se lee ni se sube.
- `terraform.tfvars` está en `.gitignore` (no se versiona).
- No se escriben secretos de Meta, tokens, contraseñas ni credenciales Azure
  en ningún `.tf` del repositorio.
- La única información sensible local es la clave pública SSH en
  `terraform.tfvars`.

---

## Qué NO hace esta fase

- ❌ Instalar Docker / Docker Compose
- ❌ Instalar Nginx
- ❌ Instalar Certbot / configurar HTTPS
- ❌ Instalar Java / PostgreSQL
- ❌ Desplegar la aplicación backend
- ❌ Configurar el dominio `api.johnkider.app` (DNS en Name.com — fase posterior)
- ❌ Configurar el webhook de Meta para WhatsApp
- ❌ Crear secretos en Key Vault
- ❌ Ejecutar `terraform apply`

---

## Siguiente fase (preview)

- **Ansible**: conectar vía SSH a `azureadmin@<public_ip>` y provisionar Docker,
  Docker Compose, Nginx (reverse proxy), Java 21 y PostgreSQL.
- **Docker Compose**: levantar `backend` + `postgres` con secretos inyectados
  desde Azure Key Vault.
- **DNS**: apuntar `api.johnkider.app` a la Public IP estática.
- **HTTPS**: Nginx + Certbot con Let's Encrypt.
- **Meta**: registrar `https://api.johnkider.app/webhook/whatsapp`.
- **Backend**: perfil `meta` + `BOT_WHATSAPP_CLIENT=meta`.

---

## Estado local

Durante esta fase el *state* de Terraform se mantiene local
(`terraform.tfstate`, ignorado por Git). Para producción se recomienda
migrar a un **backend remoto** (Azure Storage Account con locking) en una
fase posterior.


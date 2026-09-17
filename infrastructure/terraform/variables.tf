# ============================================================
# BotWap — Infraestructura base en Azure (Fase 7C)
# Variables de configuración.
# ============================================================

variable "project_name" {
  description = "Nombre del proyecto. Se usa como prefijo de convención de nombres de recursos."
  type        = string
  default     = "botwap"
}

variable "environment" {
  description = "Nombre del entorno (p. ej. prod, staging)."
  type        = string
  default     = "prod"
}

variable "location" {
  description = "Región de Azure donde se crearán los recursos."
  type        = string
  default     = "northcentralus"
}

variable "resource_group_name" {
  description = "Nombre del Resource Group donde se desplegarán los recursos."
  type        = string
  default     = "botwap-prod-rg"
}

variable "vm_size" {
  description = "SKU de la máquina virtual. Se valida su disponibilidad en la región durante terraform plan."
  type        = string
  default     = "Standard_B2als_v2"
}

variable "admin_username" {
  description = "Usuario administrador de la VM Linux."
  type        = string
  default     = "azureadmin"
}

variable "ssh_public_key" {
  description = "Clave pública SSH (ed25519) para acceder a la VM. Se inyecta desde terraform.tfvars (ignorado por Git)."
  type        = string
  # Sin valor por defecto: debe proporcionarse.
}

variable "vnet_address_space" {
  description = "Address space del Virtual Network en notación CIDR."
  type        = list(string)
  default     = ["10.20.0.0/16"]
}

variable "subnet_address_prefix" {
  description = "Address prefix de la subnet en notación CIDR."
  type        = string
  default     = "10.20.1.0/24"
}

variable "allowed_ssh_source_cidr" {
  description = "CIDR permitido para acceso SSH (puerto 22) a la VM."
  type        = list(string)
  default     = ["190.121.129.156/32","190.121.129.156/32"]
}

# ============================================================
# BotWap — Infraestructura base en Azure (Fase 7C)
# Fichero de versiones y proveedores de Terraform.
# ============================================================

terraform {
  required_version = ">= 1.6.0"

  # El provider azurerm es el único necesario (gestión de recursos Azure).
  # El provider random se usa exclusivamente para generar el sufijo único
  # exigido por Azure Key Vault (el nombre debe ser globalmente único).
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

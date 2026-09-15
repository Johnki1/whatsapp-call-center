# ============================================================
# BotWap — Infraestructura base en Azure (Fase 7C)
# Outputs útiles (nunca exponen secretos).
# ============================================================

output "resource_group_name" {
  description = "Nombre del Resource Group creado."
  value       = azurerm_resource_group.main.name
}

output "location" {
  description = "Región de Azure donde se desplegó la infraestructura."
  value       = azurerm_resource_group.main.location
}

output "public_ip_address" {
  description = "Dirección IP pública estática asignada a la VM."
  value       = azurerm_public_ip.main.ip_address
}

output "vm_name" {
  description = "Nombre de la máquina virtual Linux."
  value       = azurerm_linux_virtual_machine.main.name
}

output "vm_private_ip" {
  description = "IP privada interna de la VM (asignada dinámicamente por la subnet)."
  value       = azurerm_network_interface.main.private_ip_address
}

output "vm_public_ip" {
  description = "IP pública de la VM (alias de public_ip_address)."
  value       = azurerm_public_ip.main.ip_address
}

output "managed_identity_principal_id" {
  description = "Principal ID de la Managed Identity system-assigned de la VM."
  value       = azurerm_linux_virtual_machine.main.identity[0].principal_id
}

output "key_vault_name" {
  description = "Nombre globalmente único del Key Vault creado."
  value       = azurerm_key_vault.main.name
}

output "key_vault_uri" {
  description = "URI del Key Vault (p. ej. https://kv-xxx.vault.azure.net/)."
  value       = azurerm_key_vault.main.vault_uri
}

output "vnet_name" {
  description = "Nombre de la Virtual Network creada."
  value       = azurerm_virtual_network.main.name
}

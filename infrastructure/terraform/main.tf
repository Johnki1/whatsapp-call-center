# ============================================================
# BotWap — Infraestructura base en Azure (Fase 7C)
# ============================================================
# Crea: RG, VNet, Subnet, NSG (+reglas), Public IP, NIC,
# Key Vault (RBAC), Linux VM (SystemAssigned Managed Identity)
# y Role Assignment (Key Vault Secrets User).
#
# NO instala software dentro de la VM (Docker, Nginx, Java,
# PostgreSQL, Certbot...). Eso es responsabilidad de Ansible
# en una fase posterior. No se ejecuta terraform apply aquí.
# ============================================================

provider "azurerm" {
  features {}
}

# Cliente/datos de Azure. Se autentica vía Azure CLI (`az login`).
# No se hardcodean credenciales en el código.
data "azurerm_client_config" "current" {}

# Tags comunes
locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# Sufijo único para el Key Vault (nombre globalmente único, 3-24 chars).
resource "random_string" "kv_suffix" {
  length  = 4
  special = false
  upper   = false
  numeric = true
  lower   = true
}

# 1. Resource Group
resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.common_tags
}

# 2. Virtual Network
resource "azurerm_virtual_network" "main" {
  name                = "vnet-${var.project_name}-${var.environment}"
  address_space       = var.vnet_address_space
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

# 3. Subnet
resource "azurerm_subnet" "main" {
  name                 = "subnet-${var.project_name}-${var.environment}"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.subnet_address_prefix]
}

# 4. Network Security Group
resource "azurerm_network_security_group" "main" {
  name                = "nsg-${var.project_name}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

# Regla 1 (prioridad 1000): SSH — únicamente desde la IP del admin (TCP 22).
#   NO se abre a 0.0.0.0/0.
resource "azurerm_network_security_rule" "ssh" {
  name                        = "Allow-SSH"
  resource_group_name         = azurerm_resource_group.main.name
  network_security_group_name = azurerm_network_security_group.main.name
  priority                    = 1000
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  source_address_prefixes     = var.allowed_ssh_source_cidr
  destination_port_range      = "22"
  destination_address_prefix  = "*"
}

# Regla 2 (prioridad 1010): HTTP — Internet (TCP 80).
resource "azurerm_network_security_rule" "http" {
  name                        = "Allow-HTTP"
  resource_group_name         = azurerm_resource_group.main.name
  network_security_group_name = azurerm_network_security_group.main.name
  priority                    = 1010
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  source_address_prefix       = "0.0.0.0/0"
  destination_port_range      = "80"
  destination_address_prefix  = "*"
}

# Regla 3 (prioridad 1020): HTTPS — Internet (TCP 443).
resource "azurerm_network_security_rule" "https" {
  name                        = "Allow-HTTPS"
  resource_group_name         = azurerm_resource_group.main.name
  network_security_group_name = azurerm_network_security_group.main.name
  priority                    = 1020
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  source_address_prefix       = "0.0.0.0/0"
  destination_port_range      = "443"
  destination_address_prefix  = "*"
}

# 5. Public IP (estática, Standard, zona redundante)
#    Standard SKU sin `zones` explícitos es zone-redundant por defecto.
#    Se mantiene estática para poder enlazar luego api.johnkider.app.
resource "azurerm_public_ip" "main" {
  name                = "pip-${var.project_name}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = local.common_tags
}

# 6. Network Interface
resource "azurerm_network_interface" "main" {
  name                = "nic-${var.project_name}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.main.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.main.id
  }

  tags = local.common_tags
}

# Asociación del NSG a la NIC (conecta el NSG a la interfaz de la VM).
# En azurerm provider v5 el recurso correcto es
# azurerm_network_interface_security_group_association.
resource "azurerm_network_interface_security_group_association" "main" {
  network_interface_id      = azurerm_network_interface.main.id
  network_security_group_id = azurerm_network_security_group.main.id
}

# 7. Key Vault (Azure RBAC, sin legacy access policies). NO se crean secretos.
resource "azurerm_key_vault" "main" {
  name                       = "kv-${var.project_name}-${var.environment}-${random_string.kv_suffix.result}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  purge_protection_enabled   = true
  tags                       = local.common_tags
}

# 8. Linux Virtual Machine (Ubuntu 24.04 LTS, SystemAssigned Managed Identity)
resource "azurerm_linux_virtual_machine" "main" {
  name                            = "vm-${var.project_name}-${var.environment}"
  location                        = azurerm_resource_group.main.location
  resource_group_name             = azurerm_resource_group.main.name
  size                            = var.vm_size
  admin_username                  = var.admin_username
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.main.id]

  admin_ssh_key {
    username   = var.admin_username
    public_key = var.ssh_public_key
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # SystemAssigned Managed Identity (sin credenciales gestionadas por usuario)
  identity {
    type = "SystemAssigned"
  }

  tags = local.common_tags
}

# 9. Role Assignment: VM Managed Identity -> "Key Vault Secrets User"
#    Solo lectura de secretos. NO se otorgan Owner/Contributor/Administrator.
resource "azurerm_role_assignment" "vm_kv_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_virtual_machine.main.identity[0].principal_id
}


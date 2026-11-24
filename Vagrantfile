# -*- mode: ruby -*-
# vi: set ft=ruby :

# Máquina virtual Ubuntu con Docker para levantar central y dos tiendas, cada una con su web.
Vagrant.configure("2") do |config|
  config.vm.box = "bento/ubuntu-24.04"

  # El proyecto del equipo queda disponible dentro de la VM en /vagrant.
  config.vm.synced_folder ".", "/vagrant"

  # Frontend (repositorio aparte, clonado al lado de este): si existe la carpeta
  # ../pointOfSales-frontend, queda en /pointOfSales-frontend y docker compose la construye.
  frontend_dir = File.expand_path("../pointOfSales-frontend", __dir__)
  if File.directory?(frontend_dir)
    config.vm.synced_folder frontend_dir, "/pointOfSales-frontend"
  else
    puts "Aviso: no se encontró #{frontend_dir}; la web no se levantará. Clona pointOfSales-frontend al lado de este repositorio."
  end

  # Redes privadas (host-only): la VM tiene una IP por nodo, para simular tres máquinas
  # distintas (cada una con su web en el puerto 80 y su API en el 8080). Así no se ocupan
  # puertos de localhost, para no chocar con otras aplicaciones del equipo.
  # Central 192.168.56.10, tienda 1 192.168.56.11, tienda 2 192.168.56.12.
  # Se cambian con POS_CENTRAL_IP, POS_STORE1_IP y POS_STORE2_IP antes de "vagrant up".
  config.vm.network "private_network", ip: ENV.fetch("POS_CENTRAL_IP", "192.168.56.10")
  config.vm.network "private_network", ip: ENV.fetch("POS_STORE1_IP", "192.168.56.11")
  config.vm.network "private_network", ip: ENV.fetch("POS_STORE2_IP", "192.168.56.12")

  config.vm.provider "virtualbox" do |vb|
    vb.name = "medinasystems-pos"
    vb.memory = 8192
    vb.cpus = 4
  end

  # Instala Docker desde el repositorio oficial de Docker.
  config.vm.provision "shell", privileged: true, inline: <<-SHELL
    #!/bin/bash
    set -euo pipefail
    export DEBIAN_FRONTEND=noninteractive

    apt-get update
    apt-get install -y ca-certificates curl

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
      > /etc/apt/sources.list.d/docker.list

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Permite usar docker al usuario vagrant sin sudo (efectivo en la siguiente sesión).
    usermod -aG docker vagrant
  SHELL

  # Levanta el entorno en cada "vagrant up" o "vagrant reload".
  config.vm.provision "compose", type: "shell", run: "always", privileged: true, inline: <<-SHELL
    #!/bin/bash
    set -euo pipefail
    cd /vagrant
    # Entorno local: si no hay .env, se crea desde .env.example (valores de desarrollo).
    if [ ! -f .env ]; then cp .env.example .env; fi
    # La web solo se levanta si el repositorio del frontend está disponible.
    if [ -f /pointOfSales-frontend/Dockerfile ]; then
      docker compose --profile web up -d --build
    else
      docker compose up -d --build
    fi
  SHELL
end

# -*- mode: ruby -*-
# vi: set ft=ruby :

# Máquina virtual Ubuntu con Docker para levantar central y dos tiendas.
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

  # Puertos de la central, la tienda 1 y la tienda 2, con sus bases de datos.
  [4200, 8080, 8081, 8082, 5432, 5433, 5434].each do |port|
    config.vm.network "forwarded_port", guest: port, host: port, host_ip: "127.0.0.1"
  end

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

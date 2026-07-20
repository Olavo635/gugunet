#!/bin/bash

# Cores para o terminal
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem Cor

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}          Compilador do Servidor Gugunet             ${NC}"
echo -e "${CYAN}====================================================${NC}"

# Verificar se java/javac está disponível
if ! command -v javac &> /dev/null; then
    echo -e "${RED}[X] JDK (javac) não encontrado! Por favor, instale o Java Development Kit.${NC}"
    exit 1
fi

compile_plugin() {
    local name=$1
    local main_package=$2
    echo -e "${YELLOW}[+] Compilando $name...${NC}"
    
    mkdir -p "$name/classes"
    rm -rf "$name/classes/*"
    
    javac --release 21 -cp Gugunet/powernukkitx.jar -d "$name/classes" "$name/src/$main_package"/*.java
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}[X] Erro de compilação em $name!${NC}"
        return 1
    fi
    
    echo -e "${GREEN}[✓] $name compilado!${NC}"
    echo -e "${YELLOW}[+] Empacotando $name.jar...${NC}"
    
    jar cf "Gugunet/plugins/$name.jar" -C "$name/classes" org -C "$name" plugin.yml
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}[X] Erro ao empacotar o arquivo JAR para $name.${NC}"
        return 1
    fi
    
    echo -e "${GREEN}[✓] $name.jar copiado para Gugunet/plugins/ com sucesso!${NC}"
    return 0
}

compile_plugin "MiniWorldEdit" "org/gugunet/miniworldedit"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "MiniNPC" "org/gugunet/mininpc"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "GugunetCore" "org/gugunet/core"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "Guardian" "org/gugunet/guardian"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "Prison" "org/gugunet/prison"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "BattleRoyale" "org/gugunet/br"
if [ $? -ne 0 ]; then exit 1; fi

compile_plugin "Impostor" "org/gugunet/impostor"
if [ $? -ne 0 ]; then exit 1; fi

echo -e "${CYAN}====================================================${NC}"
echo -e "${GREEN}[✓] Todos os plugins foram construídos com sucesso!${NC}"
echo -e "${CYAN}====================================================${NC}"
exit 0

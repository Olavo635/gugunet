#!/bin/bash

# Cores para o output
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # Sem Cor

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}            Gugunet Server Starter                  ${NC}"
echo -e "${CYAN}====================================================${NC}"

# Verificar se tmux está instalado
if command -v tmux &> /dev/null; then
    echo -e "${GREEN}[+] tmux detectado!${NC} Iniciando servidor em uma sessão tmux..."
    tmux new-session -d -s gugunet 'cd Gugunet && ./start.sh'
    echo -e "${GREEN}[✓] Servidor Gugunet iniciado com sucesso!${NC}"
    echo -e "Use o comando: ${YELLOW}tmux attach -t gugunet${NC} para abrir o console."
    echo -e "Para sair do console sem desligar o servidor, use: ${YELLOW}Ctrl+B e depois D${NC}"
    exit 0
fi

# Verificar se screen está instalado
if command -v screen &> /dev/null; then
    echo -e "${GREEN}[+] screen detectado!${NC} Iniciando servidor em uma sessão screen..."
    screen -dmS gugunet bash -c 'cd Gugunet && ./start.sh'
    echo -e "${GREEN}[✓] Servidor Gugunet iniciado com sucesso!${NC}"
    echo -e "Use: ${YELLOW}screen -r gugunet${NC} para acessar o console do servidor."
    echo -e "Para sair do console sem desligar o servidor, use: ${YELLOW}Ctrl+A e depois D${NC}"
    exit 0
fi

# Se não houver nem screen nem tmux, dar opções
echo -e "${YELLOW}[!] Nem tmux nem screen foram encontrados no sistema.${NC}"
echo -e "Escolha uma opção:"
echo ""
echo -e "1) Iniciar o servidor aqui neste terminal (foreground)"
echo -e "2) Iniciar o servidor em segundo plano (background) e salvar logs em arquivo"
echo -e "3) Sair"
echo ""
read -p "Escolha uma opção (1-3): " opcao

case $opcao in
    1)
        cd Gugunet && ./start.sh
        ;;
    2)
        echo -e "${GREEN}[+] Iniciando servidor em segundo plano...${NC}"
        nohup bash -c "cd Gugunet && ./start.sh" > Gugunet/server_background.log 2>&1 &
        SERVER_PID=$!
        echo -e "${GREEN}[✓] Servidor iniciado (PID: $SERVER_PID). Log: Gugunet/server_background.log${NC}"
        echo ""
        echo -e "Para ver os logs em tempo real, use: ${CYAN}tail -f Gugunet/server_background.log${NC}"
        echo -e "Para parar o servidor, use: ${RED}kill $SERVER_PID${NC}"
        ;;
    *)
        echo "Saindo..."
        exit 0
        ;;
esac

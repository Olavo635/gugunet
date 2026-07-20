#!/bin/bash
cd "$(dirname "$0")"

PORT=8080
IP=$(hostname -I | awk '{print $1}')

echo "=========================================="
echo "  Gugunet JSON UI Editor"
echo "=========================================="
echo ""
echo "  Local:   http://localhost:$PORT"
echo "  Rede:    http://$IP:$PORT"
echo ""
echo "  Acesse de outros dispositivos usando"
echo "  o IP da rede local acima."
echo ""
echo "  Ctrl+C para parar."
echo "=========================================="
echo ""

python3 -m http.server $PORT --bind 0.0.0.0 2>/dev/null || \
python -m SimpleHTTPServer $PORT 2>/dev/null || \
npx serve -l $PORT -s . 2>/dev/null || \
echo "Erro: nenhum servidor encontrado. Instale python3 ou node."

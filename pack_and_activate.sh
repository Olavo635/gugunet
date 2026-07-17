#!/bin/bash
# =============================================================================
# Gugunet Texture Pack — Script de empacotamento e ativação
# Uso: bash pack_and_activate.sh
# =============================================================================

PACK_SRC="/home/olavo/Downloads/gugunet/texture/Gugunet Texture Pack"
SERVER_DIR="/home/olavo/Downloads/gugunet/Gugunet"
PACK_ZIP="$SERVER_DIR/resource_packs/gugunet_pack.zip"

echo "🔄 Incrementando versão no manifest.json para forçar limpeza de cache no cliente..."
python3 -c "
import json
manifest_path = '${PACK_SRC}/manifest.json'
with open(manifest_path, 'r+') as f:
    data = json.load(f)
    v = data['header']['version']
    v[2] += 1
    data['header']['version'] = v
    if 'modules' in data and len(data['modules']) > 0:
        data['modules'][0]['version'] = v
    f.seek(0)
    json.dump(data, f, indent=4, ensure_ascii=False)
    f.truncate()
"

echo "📦 Empacotando Gugunet Texture Pack..."
mkdir -p "$SERVER_DIR/resource_packs"

# Remove zip antigo se existir
rm -f "$PACK_ZIP"

# Criar ZIP do pack (precisa estar com a pasta raiz dentro)
cd "/home/olavo/Downloads/gugunet/texture/Gugunet Texture Pack"
zip -r "$PACK_ZIP" .

echo "✅ Pack criado em: $PACK_ZIP"
echo ""
echo "📋 Próximo passo: configure o server.properties do PNX para enviar o pack."
echo "   Adicione ou edite as linhas abaixo no server.properties:"
echo ""
echo "   texture-pack=resource_packs/gugunet_pack.zip"
echo "   texture-pack-required=0"
echo ""
echo "🚀 Reinicie o servidor para aplicar."

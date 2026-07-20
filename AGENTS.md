# Gugunet Server — Guia de Desenvolvimento

## Projeto

Servidor Minecraft Bedrock rodando **PowerNukkitX** com 6 plugins Java custom e resource pack com JSON UI. Estrutura:

```
/
├── Gugunet/                  # Servidor PNX
│   ├── plugins/              # JARs compilados
│   ├── resource_packs/       # ZIP do resource pack
│   └── powernukkitx.jar      # Servidor
├── BattleRoyale/             # BR minigame (~2050 linhas)
├── GugunetCore/              # Lobby, ranks, stats SQLite, scoreboard
├── Guardian/                 # Proteção de mundos
├── MiniNPC/                  # NPCs custom com skins e poses
├── MiniWorldEdit/            # WorldEdit custom
├── Prison/                   # Sistema de prisão com mineração
├── texture/Gugunet Texture Pack/  # Resource pack fonte
│   └── ui/                   # JSON UI: server_form.json, custom_minigame_menu.json
├── build.sh                  # Compila todos os plugins
├── pack_and_activate.sh      # Empacota resource pack
└── start.sh                  # Inicia o servidor
```

## JSON UI (Minecraft Bedrock)

### Regras de ouro para botões funcionarem

1. `$pressed_button_name: "button.form_button_click"` — essencial pro clique
2. `$button_text_binding_type: "collection"` + `$button_text_grid_collection_name: "form_buttons"` — obrigatórios
3. `bindings` com `collection_details` apontando `form_buttons` no `form_button` ou ancestral

### Armadilhas comuns

- **`bindings: []` na instância anula o protótipo** — arrays substituem completamente na herança `@`
- **`long_form` com `size: [0, 0]` quebra cliques** — sempre usar `["100%", "100%"]`
- **Fonte custom não carrega** — `default_formats.json` em `font/` (não `fonts/`), caminho relativo `"ttf": "font/MinecraftTen.ttf"`
- **Texto centralizado**: `anchor_from: "center"`, `anchor_to: "center"`, `text_alignment: "center"`
- **Label separado pra fonte custom**: deixe `$button_text: ""` e use label com `layer` maior que o botão

### server_form.json

Substitui forms pelo título. Exemplo com view binding:
```json
"binding_type": "view",
"source_property_name": "(#title_text = 'Minigames')",
"target_property_name": "#visible"
```

## Compilação

```bash
bash build.sh          # Compila tudo (6 plugins)
bash pack_and_activate.sh  # Empacota resource pack (incrementa versão)
```

Compilação individual:
```bash
javac --release 21 -cp Gugunet/powernukkitx.jar -d Plugin/classes Plugin/src/.../*.java
jar cf Gugunet/plugins/Plugin.jar -C Plugin/classes org -C Plugin plugin.yml
```

## Operação do Servidor

### Iniciar
```bash
bash start.sh  # usa tmux > screen > background
```

### Acessar console
`tmux attach -t gugunet` ou `screen -r gugunet`
Sair: `Ctrl+B` `D` (tmux) ou `Ctrl+A` `D` (screen)

### ⚠️ Parar o servidor (CRÍTICO)
**NUNCA mate o processo na força bruta** — corrompe LevelDB e SQLite!

Sempre usar `stop` no console ou `/stop` in-game.

Se travou: `tmux send-keys -t gugunet 'stop' Enter` → aguardar 10s → `kill <PID>` (SIGTERM) → último caso `kill -9 <PID>` (PNX faz recovery automático do LevelDB)

### Backup antes de alterações
```bash
cp -r Gugunet/worlds Gugunet/worlds_backup_$(date +%Y%m%d_%H%M%S)
```

## Plugins

| Plugin | Função | Observação |
|--------|--------|------------|
| GugunetCore | Lobby, ranks, SQLite, scoreboard, chat | Cria mundos planos na inicialização |
| BattleRoyale | BR completo: loot, gás, baús voadores | Itens custom via NBT |
| Guardian | Proteção de mundos | GUI formas admin |
| MiniNPC | NPCs com skins, poses, comandos | Skin salva em JSON no data folder |
| MiniWorldEdit | Wand, set, replace, copy/paste, undo/redo | Limite de 500K blocos |
| Prison | Prisão com sentença, mineração | 6 tipos de minério com pesos |

Inter-plugin: Guardian e Prison usam **reflection** pra chamar `BattleRoyale.forceRemovePlayer()`.

## Editor JSON UI

Editor visual para criar menus JSON UI. Localizado em `texture/Gugunet Texture Pack/ui/editor/`.

### Abrir editor
```bash
cd texture/Gugunet\ Texture\ Pack/ui/editor
xdg-open index.html
```

### Formato de export `.gugunet-ui`

Quando o usuário enviar um arquivo `.gugunet-ui`, importar e implementar:

```json
{
  "version": 1,
  "meta": {
    "name": "Nome do Menu",
    "author": "Usuário",
    "created": "2025-01-01",
    "description": "Descrição do que o menu faz"
  },
  "target": {
    "file": "custom_minigame_menu.json",
    "namespace": "wqqn1ynamespace",
    "rootElement": "wqqn1ynamespace"
  },
  "elements": [
    {
      "id": "el_abc123",
      "_componentType": "button",
      "_name": "Jogar Button",
      "type": "panel",
      "size": [188, 40],
      "offset": [0, 0],
      "anchor_from": "top_left",
      "anchor_to": "top_left",
      "layer": 4,
      "$label_text": "Jogar",
      "$button_text_binding_type": "collection",
      "$button_text_grid_collection_name": "form_buttons",
      "$pressed_button_name": "button.form_button_click",
      "$default_button_background_texture": "textures/green_default",
      "$hover_button_background_texture": "textures/green_hover",
      "$pressed_button_background_texture": "textures/green_pressed",
      "$font_type": "MinecraftTen",
      "$font_size": 1.8,
      "$text_alignment": "center",
      "bindings": [
        { "binding_type": "collection_details", "binding_collection_name": "form_buttons" }
      ],
      "children": []
    }
  ],
  "prototypes": {
    "custom_button": {
      "description": "Botão custom reutilizável",
      "element": { /* estrutura do protótipo */ }
    }
  },
  "textures": [
    { "path": "textures/green_default", "nineslice": [2, 2, 2, 4], "baseSize": [8, 8] }
  ],
  "instructions": "Criar menu de seleção de minigames com botões dinâmicos"
}
```

### Como implementar

1. **Ler `.gugunet-ui`** → extrair `elements` e `target`
2. **Criar/Atualizar** o arquivo JSON em `texture/Gugunet Texture Pack/ui/<target.file>`
3. **Copiar texturas** mencionadas em `textures[]` para `texture/Gugunet Texture Pack/textures/`
4. **Criar JSON伴侶** para texturas 9-slice (ex: `green_default.json`)
5. **Atualizar `_ui_defs.json`** se necessário
6. **Executar** `bash pack_and_activate.sh` para empacotar

### Regras de implementação

- Botões sempre precisam de `$pressed_button_name`, `$button_text_binding_type: "collection"`, `$button_text_grid_collection_name: "form_buttons"`
- `bindings` com `collection_details` obrigatório em botões com factory
- Nunca usar `bindings: []` em instâncias (anula protótipo)
- Labels com fonte custom: `$button_text: ""` no botão, label separado com `layer` maior
- Texturas 9-slice precisam de JSON伴侶 com `nineslice_size` e `base_size`

## SQLite

`Gugunet/plugins/GugunetCore/gugunet_stats.db` — tabela `player_stats(uuid, name, level, xp, money)`. Fallback JSON em `players_stats.json`.

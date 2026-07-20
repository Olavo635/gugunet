# Guia JSON UI para Minecraft Bedrock (PowerNukkitX)

## Como fazer um botão de formulário funcionar corretamente

### 1. Estrutura mínima de um botão funcional

O sistema de forms do Minecraft Bedrock precisa de 3 coisas para processar um clique:

```
custom_button (painel)
  ├── panel_name (painel com offset)
  │   └── text (label) → texto do botão
  └── form_button@common_buttons.light_content_button
        ├── $pressed_button_name: "button.form_button_click"
        ├── $button_text: "" (vazio, usa label separado)
        ├── $button_text_binding_type: "collection"      ← obrigatório
        ├── $button_text_grid_collection_name: "form_buttons"  ← obrigatório
        └── bindings:
              └── collection_details → form_buttons       ← obrigatório
```

### 2. Requisitos para o clique funcionar

```json
{
  "form_button@common_buttons.light_content_button": {
    "$pressed_button_name": "button.form_button_click",  // evento de clique
    "$button_text_binding_type": "collection",            // NECESSÁRIO para o form reconhecer
    "$button_text_grid_collection_name": "form_buttons",  // NECESSÁRIO para o form reconhecer
    "bindings": [
      { "binding_type": "collection_details",
        "binding_collection_name": "form_buttons" }       // NECESSÁRIO para o form reconhecer
    ]
  }
}
```

**`$button_text`** pode ficar vazio (`""`) — o texto é exibido por um `label` separado. As propriedades `$button_text_binding_type` e `$button_text_grid_collection_name` precisam estar presentes mesmo assim.

### 3. `collection_index` e `collection_details`

- **`collection_index`** vai na **instância** do botão (ex: `play_button@custom_button`)
- **`collection_details`** vai no `bindings` do `form_button` (dentro do protótipo)
- O sistema de forms busca `collection_index` subindo a árvore de controles (child → parent → grandparent)

```json
// Protótipo
"custom_button": {
  "controls": [
    { "form_button@...": {
        "bindings": [
          { "binding_type": "collection_details",
            "binding_collection_name": "form_buttons" }
        ]
    } }
  ]
}

// Instância
"play_button@custom_button": {
  "collection_index": 0,          // ← aqui
  "$button_size": [188, 40]
}
```

### 4. Armadilhas comuns

#### ❌ `bindings: []` na instância anula o protótipo
```json
"play_button@custom_button": {
  "bindings": []  // ISSO SOBRESCREVE os bindings do protótipo!
}
```
Se a instância define `bindings`, os bindings do protótipo são DESCARTADOS. Só use `bindings` na instância se quiser substituir completamente.

#### ❌ `collection_details` no lugar errado
O `collection_details` precisa estar no `form_button` ou em um ANCESTRAL. Se estiver em um FILHO/IRMÃO, não é encontrado.

#### ❌ `long_form` com `size: [0, 0]`
O container `long_form` no `server_form.json` precisa ter `size: ["100%", "100%"]`. Com `[0, 0]` o sistema de forms pode não processar cliques corretamente.

```json
// server_form.json - CORRETO
"long_form": {
  "type": "panel",
  "size": ["100%", "100%"],     // ← NUNCA [0, 0]
  "controls": [...]
}
```

#### ❌ Fonte personalizada não carrega
Se a fonte MinecraftTen não aparecer, pode ser problema no `default_formats.json`. Verificar:
- Arquivo em `font/default_formats.json` (não `fonts/`)
- Caminho relativo à raiz do pack: `"ttf": "font/MinecraftTen.ttf"`
- O TTF precisa existir no caminho indicado

### 5. Centralizar texto vertical e horizontalmente

Use `anchor_from: "center"` com `anchor_to: "center"` e `offset: [0, 0]`:

```json
"text": {
  "anchor_from": "center",
  "anchor_to": "center",
  "type": "label",
  "text_alignment": "center",
  "offset": [0, 0]
}
```

### 6. Usar label separado para texto (em vez do texto do botão)

O `form_button` renderiza texto via `$button_text`. Para ter controle total sobre a fonte e alinhamento:
- Deixe `$button_text: ""` no form_button
- Adicione um `label` separado com `font_type: "MinecraftTen"` e `text_alignment: "center"`
- Dê ao label um `layer` maior que o do form_button (ex: label layer 6, button layer 1)

### 7. Sobreposição de protótipos (`@`)

Quando você faz `meu_botao@meu_prototipo`:
- **Objetos** mesclam (instance override prototype)
- **Arrays** (bindings, controls) SUBSTITUEM completamente
- Para herdar bindings do protótipo, NÃO declare `bindings` na instância

### 8. Botão de fechar (X)

Use `$pressed_button_name: "button.menu_exit"` para fechar o form (envia índice -1).

### 9. server_form.json - substituir formulário padrão

Para mostrar um UI customizado quando o título bater:

```json
"long_form": {
  "size": ["100%", "100%"],
  "controls": [
    {
      "custom_form@meu_namespace.meu_painel": {
        "bindings": [
          { "binding_name": "#title_text",
            "binding_name_override": "#title_text" },
          { "binding_type": "view",
            "source_property_name": "(#title_text = 'Meu Titulo')",
            "target_property_name": "#visible" }
        ]
      }
    },
    {
      "server_form@common_dialogs.main_panel_no_buttons": {
        "bindings": [
          { "binding_name": "#title_text",
            "binding_name_override": "#title_text" },
          { "binding_type": "view",
            "source_property_name": "(not (#title_text = 'Meu Titulo'))",
            "target_property_name": "#visible" }
        ]
      }
    }
  ]
}
```

### 10. Fluxo de desenvolvimento

1. Editar JSON em `texture/Gugunet Texture Pack/ui/`
2. Editar Java em `GugunetCore/src/`
3. Compilar plugins: `bash build.sh`
4. Empacotar resource pack: `bash pack_and_activate.sh`
5. Reiniciar servidor
6. Cliente precisa baixar o resource pack atualizado (versão incrementa automático)

const Properties = {
  currentElement: null,

  render(element) {
    this.currentElement = element;
    const container = document.getElementById('properties-content');
    if (!element) {
      container.innerHTML = '<p class="empty-state">Selecione um elemento</p>';
      return;
    }
    const type = getElementType(element);
    let html = '';

    html += this._group('Identificação', `
      <div class="prop-row">
        <label>Nome</label>
        <input type="text" data-prop="_name" value="${Utils.escapeHtml(element._name || '')}">
      </div>
      <div class="prop-row">
        <label>Tipo</label>
        <input type="text" value="${type}" disabled>
      </div>
      <div class="prop-row">
        <label>ID</label>
        <input type="text" value="${element.id}" disabled>
      </div>
    `);

    html += this._group('Layout', `
      <div class="prop-row">
        <label>Size X</label>
        <input type="text" data-prop="size.0" value="${element.size ? element.size[0] : 100}">
      </div>
      <div class="prop-row">
        <label>Size Y</label>
        <input type="text" data-prop="size.1" value="${element.size ? element.size[1] : 100}">
      </div>
      <div class="prop-row">
        <label>Offset X</label>
        <input type="number" data-prop="offset.0" value="${element.offset ? element.offset[0] : 0}">
      </div>
      <div class="prop-row">
        <label>Offset Y</label>
        <input type="number" data-prop="offset.1" value="${element.offset ? element.offset[1] : 0}">
      </div>
      <div class="prop-row">
        <label>Anchor From</label>
        <select data-prop="anchor_from">
          ${this._anchorOptions(element.anchor_from || 'top_left')}
        </select>
      </div>
      <div class="prop-row">
        <label>Anchor To</label>
        <select data-prop="anchor_to">
          ${this._anchorOptions(element.anchor_to || 'top_left')}
        </select>
      </div>
      <div class="prop-row">
        <label>Layer</label>
        <input type="number" data-prop="layer" value="${element.layer || 0}" min="0" max="100">
      </div>
    `);

    if (type === 'label') {
      html += this._group('Texto', `
        <div class="prop-row">
          <label>Texto</label>
          <input type="text" data-prop="text" value="${Utils.escapeHtml(element.text || '')}">
        </div>
        <div class="prop-row">
          <label>Font</label>
          <select data-prop="font_type">
            ${this._fontOptions(element.font_type || 'MinecraftTen')}
          </select>
        </div>
        <div class="prop-row">
          <label>Scale</label>
          <input type="number" data-prop="font_scale_factor" value="${element.font_scale_factor || 1}" step="0.1" min="0.1">
        </div>
        <div class="prop-row">
          <label>Alinhamento</label>
          <select data-prop="text_alignment">
            ${this._alignOptions(element.text_alignment || 'left')}
          </select>
        </div>
        <div class="prop-row">
          <label>Cor</label>
          <input type="color" data-prop="color" value="${element.color || '#ffffff'}">
        </div>
        <div class="prop-row">
          <label>Shadow</label>
          <select data-prop="shadow">
            <option value="false" ${!element.shadow ? 'selected' : ''}>Não</option>
            <option value="true" ${element.shadow ? 'selected' : ''}>Sim</option>
          </select>
        </div>
      `);
    }

    if (type === 'image') {
      html += this._group('Imagem', `
        <div class="prop-row">
          <label>Texture</label>
          <input type="text" data-prop="texture" value="${Utils.escapeHtml(element.texture || '')}">
        </div>
        <div class="prop-row">
          <label>NineSlice</label>
          <input type="text" data-prop="nineslice_size"
            value="${element.nineslice_size ? JSON.stringify(element.nineslice_size) : ''}"
            placeholder="[2, 2, 2, 4] ou 2">
        </div>
        <div class="prop-row">
          <label>Base Size</label>
          <input type="text" data-prop="base_size"
            value="${element.base_size ? JSON.stringify(element.base_size) : ''}"
            placeholder="[8, 8] ou 12">
        </div>
      `);
    }

    if (type === 'button') {
      html += this._group('Button', `
        <div class="prop-row">
          <label>Label</label>
          <input type="text" data-prop="$label_text" value="${Utils.escapeHtml(element.$label_text || '')}">
        </div>
        <div class="prop-row">
          <label>Font</label>
          <select data-prop="$font_type">
            ${this._fontOptions(element.$font_type || 'MinecraftTen')}
          </select>
        </div>
        <div class="prop-row">
          <label>Scale</label>
          <input type="number" data-prop="$font_size" value="${element.$font_size || 1.8}" step="0.1" min="0.1">
        </div>
        <div class="prop-row">
          <label>Alinhamento</label>
          <select data-prop="$text_alignment">
            ${this._alignOptions(element.$text_alignment || 'center')}
          </select>
        </div>
        <div class="prop-row">
          <label>Pressed</label>
          <input type="text" data-prop="$pressed_button_name"
            value="${Utils.escapeHtml(element.$pressed_button_name || 'button.form_button_click')}">
        </div>
        <div class="prop-row">
          <label>Texture Def</label>
          <input type="text" data-prop="$default_button_background_texture"
            value="${Utils.escapeHtml(element.$default_button_background_texture || '')}">
        </div>
        <div class="prop-row">
          <label>Texture Hover</label>
          <input type="text" data-prop="$hover_button_background_texture"
            value="${Utils.escapeHtml(element.$hover_button_background_texture || '')}">
        </div>
        <div class="prop-row">
          <label>Texture Press</label>
          <input type="text" data-prop="$pressed_button_background_texture"
            value="${Utils.escapeHtml(element.$pressed_button_background_texture || '')}">
        </div>
        <div class="prop-row">
          <label>NineSlice</label>
          <input type="text" data-prop="nineslice_size"
            value="${element.nineslice_size ? JSON.stringify(element.nineslice_size) : ''}"
            placeholder="[2, 2, 2, 4]">
        </div>
        <div class="prop-row">
          <label>Base Size</label>
          <input type="text" data-prop="base_size"
            value="${element.base_size ? JSON.stringify(element.base_size) : ''}"
            placeholder="[8, 8]">
        </div>
      `);
    }

    if (type === 'stack_panel') {
      html += this._group('Stack Panel', `
        <div class="prop-row">
          <label>Orientação</label>
          <select data-prop="orientation">
            <option value="vertical" ${element.orientation === 'vertical' ? 'selected' : ''}>Vertical</option>
            <option value="horizontal" ${element.orientation === 'horizontal' ? 'selected' : ''}>Horizontal</option>
          </select>
        </div>
      `);
    }

    html += this._group('Bindings', BindingEditor.render(element.bindings || [], () => App.refresh()));

    html += `<div style="padding: 8px 0;">
      <button id="btn-delete-element" style="background: var(--danger); width: 100%;">Excluir Elemento</button>
    </div>`;

    container.innerHTML = html;
    this._bindEvents(element);
  },

  _group(title, content) {
    return `<div class="prop-group">
      <div class="prop-group-title">${title}</div>
      ${content}
    </div>`;
  },

  _anchorOptions(selected) {
    const options = ['top_left', 'top_middle', 'top_right', 'left', 'center', 'right',
      'bottom_left', 'bottom_middle', 'bottom_right'];
    return options.map(o => `<option value="${o}" ${o === selected ? 'selected' : ''}>${o}</option>`).join('');
  },

  _fontOptions(selected) {
    const fonts = ['MinecraftTen', 'MinecraftRegular', 'MinecraftBold'];
    return fonts.map(f => `<option value="${f}" ${f === selected ? 'selected' : ''}>${f}</option>`).join('');
  },

  _alignOptions(selected) {
    return `<option value="left" ${selected === 'left' ? 'selected' : ''}>Esquerda</option>
      <option value="center" ${selected === 'center' ? 'selected' : ''}>Centro</option>
      <option value="right" ${selected === 'right' ? 'selected' : ''}>Direita</option>`;
  },

  _renderBindings(bindings) {
    let html = '';
    bindings.forEach((b, i) => {
      html += `<div class="binding-item" data-index="${i}">
        <button class="binding-remove" data-index="${i}">&times;</button>
        <div class="prop-row">
          <label>Tipo</label>
          <select data-binding="binding_type" data-index="${i}">
            <option value="view" ${b.binding_type === 'view' ? 'selected' : ''}>view</option>
            <option value="collection" ${b.binding_type === 'collection' ? 'selected' : ''}>collection</option>
            <option value="collection_details" ${b.binding_type === 'collection_details' ? 'selected' : ''}>collection_details</option>
          </select>
        </div>
        <div class="prop-row">
          <label>Nome</label>
          <input type="text" data-binding="binding_name" data-index="${i}"
            value="${Utils.escapeHtml(b.binding_name || '')}">
        </div>
        ${b.binding_type === 'collection' ? `
        <div class="prop-row">
          <label>Override</label>
          <input type="text" data-binding="binding_name_override" data-index="${i}"
            value="${Utils.escapeHtml(b.binding_name_override || '')}">
        </div>
        ` : ''}
        <div class="prop-row">
          <label>Collection</label>
          <input type="text" data-binding="binding_collection_name" data-index="${i}"
            value="${Utils.escapeHtml(b.binding_collection_name || '')}">
        </div>
      </div>`;
    });
    html += `<button id="btn-add-binding" style="width: 100%; margin-top: 4px;">+ Adicionar Binding</button>`;
    return html;
  },

  _bindEvents(element) {
    const container = document.getElementById('properties-content');

    container.querySelectorAll('[data-prop]').forEach(input => {
      const handler = (e) => {
        App.saveState();
        const prop = e.target.dataset.prop;
        let val = e.target.value;
        if (prop.includes('.')) {
          const [key, idx] = prop.split('.');
          if (!element[key]) element[key] = [0, 0];
          element[key][parseInt(idx)] = isNaN(Number(val)) ? val : Number(val);
        } else if (prop === 'layer') {
          element[prop] = parseInt(val) || 0;
        } else if (prop === 'font_scale_factor' || prop === '$font_size') {
          element[prop] = parseFloat(val) || 1;
        } else if (prop === 'shadow') {
          element[prop] = val === 'true';
        } else if (prop === 'nineslice_size' || prop === 'base_size') {
          try {
            element[prop] = val ? JSON.parse(val) : null;
          } catch {
            const num = Number(val);
            element[prop] = isNaN(num) ? val : num;
          }
        } else {
          element[prop] = val;
        }
        App.refresh();
      };
      input.addEventListener('change', handler);
      if (input.tagName === 'INPUT' && input.type !== 'select-one') {
        input.addEventListener('input', handler);
      }
    });

    BindingEditor.bindEvents(container, element.bindings || [], () => App.refresh());

    const deleteBtn = document.getElementById('btn-delete-element');
    if (deleteBtn) {
      deleteBtn.addEventListener('click', () => {
        App.deleteSelected();
      });
    }
  }
};

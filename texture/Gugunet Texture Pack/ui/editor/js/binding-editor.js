const BindingEditor = {
  render(bindings, onChange) {
    let html = '<div class="binding-editor">';

    if (!bindings || bindings.length === 0) {
      html += '<p class="empty-state" style="padding: 8px;">Nenhum binding</p>';
    } else {
      bindings.forEach((b, i) => {
        html += this._renderBinding(b, i);
      });
    }

    html += '<button class="btn-add-binding" data-action="add">+ Adicionar Binding</button>';
    html += '</div>';
    return html;
  },

  _renderBinding(binding, index) {
    const type = binding.binding_type || 'collection_details';
    const typeColors = {
      'view': '#4fc3f7',
      'collection': '#66bb6a',
      'collection_details': '#ffa726'
    };
    const color = typeColors[type] || '#888';

    let html = `<div class="binding-card" data-index="${index}" style="border-left: 3px solid ${color};">`;
    html += `<div class="binding-header">`;
    html += `<span class="binding-type-badge" style="background: ${color};">${type}</span>`;
    html += `<button class="binding-remove" data-index="${index}">&times;</button>`;
    html += `</div>`;

    html += `<div class="binding-fields">`;

    if (type === 'view') {
      html += this._field('source', 'Expr.', binding.source_property_name || '', index, 'source_property_name');
      html += this._field('target', 'Alvo', binding.target_property_name || '', index, 'target_property_name');
    } else if (type === 'collection') {
      html += this._field('name', 'Campo', binding.binding_name || '', index, 'binding_name');
      html += this._field('override', 'Override', binding.binding_name_override || '', index, 'binding_name_override');
      html += this._field('collection', 'Collection', binding.binding_collection_name || '', index, 'binding_collection_name');
    } else if (type === 'collection_details') {
      html += this._field('collection', 'Collection', binding.binding_collection_name || 'form_buttons', index, 'binding_collection_name');
    }

    html += `</div></div>`;
    return html;
  },

  _field(icon, label, value, index, key) {
    return `<div class="binding-field">
      <span class="binding-field-icon">${icon[0].toUpperCase()}</span>
      <div class="binding-field-content">
        <label>${label}</label>
        <input type="text" value="${Utils.escapeHtml(value)}" data-index="${index}" data-key="${key}">
      </div>
    </div>`;
  },

  bindEvents(container, bindings, onChange) {
    container.querySelectorAll('.binding-card select[data-key]').forEach(select => {
      select.addEventListener('change', (e) => {
        const idx = parseInt(e.target.dataset.index);
        const key = e.target.dataset.key;
        bindings[idx][key] = e.target.value;
        if (onChange) onChange();
      });
    });

    container.querySelectorAll('.binding-card input[data-key]').forEach(input => {
      input.addEventListener('change', (e) => {
        const idx = parseInt(e.target.dataset.index);
        const key = e.target.dataset.key;
        bindings[idx][key] = e.target.value;
        if (onChange) onChange();
      });
    });

    container.querySelectorAll('.binding-remove').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const idx = parseInt(e.target.dataset.index);
        bindings.splice(idx, 1);
        if (onChange) onChange();
      });
    });

    const addBtn = container.querySelector('.btn-add-binding');
    if (addBtn) {
      addBtn.addEventListener('click', () => {
        bindings.push({
          binding_type: 'collection_details',
          binding_collection_name: 'form_buttons'
        });
        if (onChange) onChange();
      });
    }
  }
};

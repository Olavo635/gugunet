const Validator = {
  validate(elements) {
    const issues = [];
    this._validateRecursive(elements, issues);
    return issues;
  },

  _validateRecursive(elements, issues) {
    for (const el of elements) {
      const type = getElementType(el);

      if (type === 'button') {
        if (!el.$pressed_button_name) {
          issues.push({
            type: 'error',
            element: el.id,
            message: 'Button sem $pressed_button_name — clique não funcionará'
          });
        }
        if (el.$pressed_button_name && el.$pressed_button_name !== 'button.form_button_click') {
          issues.push({
            type: 'warning',
            element: el.id,
            message: `$pressed_button_name não é "button.form_button_click"`
          });
        }
        if (Array.isArray(el.bindings) && el.bindings.length === 0) {
          issues.push({
            type: 'error',
            element: el.id,
            message: 'Button com bindings: [] anula collection_details do protótipo'
          });
        }
        if (!el.$button_text_binding_type || el.$button_text_binding_type !== 'collection') {
          issues.push({
            type: 'warning',
            element: el.id,
            message: 'Button sem $button_text_binding_type: "collection"'
          });
        }
        if (!el.$button_text_grid_collection_name) {
          issues.push({
            type: 'warning',
            element: el.id,
            message: 'Button sem $button_text_grid_collection_name'
          });
        }
      }

      if (type === 'label') {
        if (!el.text && !el.$text) {
          issues.push({
            type: 'warning',
            element: el.id,
            message: 'Label sem texto definido'
          });
        }
      }

      if (type === 'image') {
        if (!el.texture) {
          issues.push({
            type: 'warning',
            element: el.id,
            message: 'Image sem texture definida'
          });
        }
      }

      const size = el.size;
      if (size && Array.isArray(size) && size[0] === 0 && size[1] === 0) {
        issues.push({
          type: 'warning',
          element: el.id,
          message: 'Size [0, 0] pode quebrar cliques'
        });
      }

      if (el.children) {
        this._validateRecursive(el.children, issues);
      }
    }
  },

  renderOutput(container, issues) {
    container.innerHTML = '';
    if (issues.length === 0) {
      container.innerHTML = '<span class="validation-ok">✓ Nenhum problema encontrado</span>';
      return;
    }
    const errors = issues.filter(i => i.type === 'error');
    const warnings = issues.filter(i => i.type === 'warning');
    let html = '';
    if (errors.length) html += `<span class="validation-error">❌ ${errors.length} erro(s)</span>`;
    if (warnings.length) html += `<span class="validation-warning">⚠ ${warnings.length} aviso(s)</span>`;
    for (const issue of issues) {
      const cls = issue.type === 'error' ? 'validation-error' : 'validation-warning';
      const icon = issue.type === 'error' ? '❌' : '⚠';
      html += `<span class="${cls}">${icon} ${issue.message}</span>`;
    }
    container.innerHTML = html;
  }
};

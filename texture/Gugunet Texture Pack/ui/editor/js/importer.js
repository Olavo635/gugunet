const Importer = {
  fromMinecraftJson(json, namespace) {
    try {
      const data = typeof json === 'string' ? JSON.parse(json) : json;
      const elements = [];

      // Formato 1: { "namespace": "name", "wqqn1ynamespace": { ... } }
      if (data.namespace && data[data.namespace]) {
        const nsData = data[data.namespace];
        if (nsData.controls) {
          for (const control of nsData.controls) {
            const el = this._parseControl(control, namespace);
            if (el) elements.push(el);
          }
        }
        return elements;
      }

      // Formato 2: { "namespace_name": { ... } }
      const ns = data[namespace] || data[Object.keys(data)[0]];
      if (!ns) return elements;

      // Se tem controls direto
      if (ns.controls) {
        for (const control of ns.controls) {
          const el = this._parseControl(control, namespace);
          if (el) elements.push(el);
        }
        return elements;
      }

      // Se tem um sub-objeto com controls
      const root = ns[Object.keys(ns)[0]];
      if (root && root.controls) {
        for (const control of root.controls) {
          const el = this._parseControl(control, namespace);
          if (el) elements.push(el);
        }
      }
      return elements;
    } catch (e) {
      console.error('Erro ao importar JSON:', e);
      return [];
    }
  },

  _parseControl(control, namespace) {
    const key = Object.keys(control)[0];
    if (!key) return null;

    const data = control[key];
    if (!data) return null;

    const isButton = key.includes('form_button') || key.includes('button');
    const isStackPanel = (data.type === 'stack_panel') || key.includes('stack_panel');
    const isPanel = data.type === 'panel' || (!isButton && !isStackPanel && data.controls);
    const isLabel = data.type === 'label';
    const isImage = data.type === 'image';

    if (isButton) {
      return this._parseButton(key, data, namespace);
    }
    if (isLabel) {
      return this._parseLabel(key, data);
    }
    if (isImage) {
      return this._parseImage(key, data);
    }
    if (isStackPanel) {
      return this._parseStackPanel(key, data, namespace);
    }
    if (isPanel) {
      return this._parsePanel(key, data, namespace);
    }
    return null;
  },

  _parseButton(key, data, namespace) {
    const el = createComponentElement('button');
    el._name = key.split('@')[0].replace(/_/g, ' ');
    el.size = data.size || [188, 40];
    el.offset = data.offset || [0, 0];
    el.anchor_from = data.anchor_from || 'top_left';
    el.anchor_to = data.anchor_to || 'top_left';
    el.layer = data.layer || 1;

    if (data.controls) {
      for (const ctrl of data.controls) {
        const ctrlKey = Object.keys(ctrl)[0];
        if (ctrlKey === 'label_text') {
          el.$label_text = ctrl[ctrlKey].text || '';
          el.$font_type = ctrl[ctrlKey].font_type || 'MinecraftTen';
          el.$font_size = ctrl[ctrlKey].font_scale_factor || 1.8;
          el.$text_alignment = ctrl[ctrlKey].text_alignment || 'center';
        }
        if (ctrlKey && ctrlKey.includes('form_button')) {
          const fb = ctrl[ctrlKey];
          el.$default_button_background_texture = fb.$default_button_texture || 'textures/ui/glass_pane';
          el.$hover_button_background_texture = fb.$hover_button_texture || 'textures/ui/glass_pane_hover';
          el.$pressed_button_background_texture = fb.$pressed_button_texture || 'textures/ui/button_black_hover';
          el.$pressed_button_name = fb.$pressed_button_name || 'button.form_button_click';
          el.$button_text_binding_type = fb.$button_text_binding_type || 'collection';
          el.$button_text_grid_collection_name = fb.$button_text_grid_collection_name || 'form_buttons';
          if (fb.bindings) el.bindings = Utils.deepClone(fb.bindings);
        }
      }
    }

    if (data.bindings) el.bindings = Utils.deepClone(data.bindings);
    return el;
  },

  _parseLabel(key, data) {
    const el = createComponentElement('label');
    el._name = key.replace(/_/g, ' ');
    el.text = data.text || '';
    el.size = data.size || [100, 20];
    el.offset = data.offset || [0, 0];
    el.anchor_from = data.anchor_from || 'top_left';
    el.anchor_to = data.anchor_to || 'top_left';
    el.layer = data.layer || 1;
    el.font_type = data.font_type || 'MinecraftTen';
    el.font_scale_factor = data.font_scale_factor || 1;
    el.text_alignment = data.text_alignment || 'left';
    el.color = data.color || '#ffffff';
    el.shadow = data.shadow || false;
    return el;
  },

  _parseImage(key, data) {
    const el = createComponentElement('image');
    el._name = key.replace(/_/g, ' ');
    el.texture = data.texture || '';
    el.size = data.size || [64, 64];
    el.offset = data.offset || [0, 0];
    el.anchor_from = data.anchor_from || 'top_left';
    el.anchor_to = data.anchor_to || 'top_left';
    el.layer = data.layer || 1;
    if (data.nineslice_size) el.nineslice_size = data.nineslice_size;
    if (data.base_size) el.base_size = data.base_size;
    return el;
  },

  _parsePanel(key, data, namespace) {
    const el = createComponentElement('panel');
    el._name = key.split('@')[0].replace(/_/g, ' ');
    el.size = data.size || [200, 150];
    el.offset = data.offset || [0, 0];
    el.anchor_from = data.anchor_from || 'top_left';
    el.anchor_to = data.anchor_to || 'top_left';
    el.layer = data.layer || 0;
    if (data.controls) {
      el.children = [];
      for (const ctrl of data.controls) {
        const child = this._parseControl(ctrl, namespace);
        if (child) el.children.push(child);
      }
    }
    return el;
  },

  _parseStackPanel(key, data, namespace) {
    const el = createComponentElement('stack_panel');
    el._name = key.split('@')[0].replace(/_/g, ' ');
    el.size = data.size || [200, 150];
    el.offset = data.offset || [0, 0];
    el.anchor_from = data.anchor_from || 'top_left';
    el.anchor_to = data.anchor_to || 'top_left';
    el.layer = data.layer || 0;
    el.orientation = data.orientation || 'vertical';
    if (data.controls) {
      el.children = [];
      for (const ctrl of data.controls) {
        const child = this._parseControl(ctrl, namespace);
        if (child) el.children.push(child);
      }
    }
    return el;
  }
};

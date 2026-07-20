const Exporter = {
  toMinecraftJson(elements, namespace) {
    namespace = namespace || 'gugunet';
    const result = {};
    const controls = [];

    for (const el of elements) {
      const json = this._elementToJson(el, namespace);
      controls.push(json);
    }

    return JSON.stringify({ [namespace]: { [namespace]: { controls } } }, null, 2);
  },

  _elementToJson(el, namespace) {
    const type = getElementType(el);
    const name = el._name || 'element';

    if (type === 'button') {
      return this._buttonToJson(el, namespace, name);
    }
    if (type === 'label') {
      return this._labelToJson(el, name);
    }
    if (type === 'image') {
      return this._imageToJson(el, name);
    }
    if (type === 'panel') {
      return this._panelToJson(el, namespace, name);
    }
    if (type === 'stack_panel') {
      return this._stackPanelToJson(el, namespace, name);
    }
    return {};
  },

  _buttonToJson(el, namespace, name) {
    const protoName = `${namespace}.${name.replace(/[^a-zA-Z0-9_]/g, '_')}`;
    const controls = [];

    controls.push({
      label_text: {
        type: 'label',
        anchor_from: 'center',
        anchor_to: 'center',
        layer: 6,
        text_alignment: el.$text_alignment || 'center',
        shadow: el.$shadow || false,
        offset: [0, 0],
        font_type: el.$font_type || 'MinecraftTen',
        font_scale_factor: el.$font_size || 1.8,
        text: el.$label_text || el.$button_text || ''
      }
    });

    controls.push({
      form_button: {
        'form_button@common_buttons.light_content_button': {
          '$default_button_texture': el.$default_button_background_texture || 'textures/ui/glass_pane',
          '$hover_button_texture': el.$hover_button_background_texture || 'textures/ui/glass_pane_hover',
          '$pressed_button_texture': el.$pressed_button_background_texture || 'textures/ui/button_black_hover',
          '$default_state_border_visible': false,
          '$hover_state_border_visible': false,
          '$pressed_state_border_visible': false,
          '$pressed_button_name': el.$pressed_button_name || 'button.form_button_click',
          'size': el.size || [188, 40],
          '$button_text': '',
          '$button_text_binding_type': 'collection',
          '$button_text_grid_collection_name': 'form_buttons',
          '$button_text_max_size': ['100%', 20],
          'bindings': el.bindings && el.bindings.length > 0
            ? el.bindings
            : [{ binding_type: 'collection_details', binding_collection_name: 'form_buttons' }]
        }
      }
    });

    const result = {};
    const key = `${name.replace(/[^a-zA-Z0-9_]/g, '_')}@${protoName}`;
    result[key] = {
      type: 'panel',
      size: el.size || [188, 40],
      offset: el.offset || [0, 0],
      anchor_from: el.anchor_from || 'top_left',
      anchor_to: el.anchor_to || 'top_left',
      controls: controls
    };
    return result;
  },

  _labelToJson(el, name) {
    const result = {};
    result[name.replace(/[^a-zA-Z0-9_]/g, '_')] = {
      type: 'label',
      size: el.size || [100, 20],
      offset: el.offset || [0, 0],
      anchor_from: el.anchor_from || 'top_left',
      anchor_to: el.anchor_to || 'top_left',
      layer: el.layer || 1,
      text: el.text || '',
      font_type: el.font_type || 'MinecraftTen',
      font_scale_factor: el.font_scale_factor || 1,
      text_alignment: el.text_alignment || 'left',
      color: el.color || '#ffffff',
      shadow: el.shadow || false
    };
    return result;
  },

  _imageToJson(el, name) {
    const result = {};
    const img = {
      type: 'image',
      size: el.size || [64, 64],
      offset: el.offset || [0, 0],
      anchor_from: el.anchor_from || 'top_left',
      anchor_to: el.anchor_to || 'top_left',
      layer: el.layer || 1,
      texture: el.texture || ''
    };
    if (el.nineslice_size) img.nineslice_size = el.nineslice_size;
    if (el.base_size) img.base_size = el.base_size;
    result[name.replace(/[^a-zA-Z0-9_]/g, '_')] = img;
    return result;
  },

  _panelToJson(el, namespace, name) {
    const controls = [];
    if (el.children) {
      for (const child of el.children) {
        controls.push(this._elementToJson(child, namespace));
      }
    }
    const result = {};
    result[name.replace(/[^a-zA-Z0-9_]/g, '_')] = {
      type: 'panel',
      size: el.size || [200, 150],
      offset: el.offset || [0, 0],
      anchor_from: el.anchor_from || 'top_left',
      anchor_to: el.anchor_to || 'top_left',
      layer: el.layer || 0,
      controls: controls
    };
    return result;
  },

  _stackPanelToJson(el, namespace, name) {
    const controls = [];
    if (el.children) {
      for (const child of el.children) {
        controls.push(this._elementToJson(child, namespace));
      }
    }
    const result = {};
    result[name.replace(/[^a-zA-Z0-9_]/g, '_')] = {
      type: 'stack_panel',
      size: el.size || [200, 150],
      offset: el.offset || [0, 0],
      anchor_from: el.anchor_from || 'top_left',
      anchor_to: el.anchor_to || 'top_left',
      layer: el.layer || 0,
      orientation: el.orientation || 'vertical',
      controls: controls
    };
    return result;
  }
};

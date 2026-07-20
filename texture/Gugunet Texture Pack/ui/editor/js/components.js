const ComponentDefs = {
  button: {
    label: 'Button',
    icon: 'B',
    defaults: {
      type: 'panel',
      size: [188, 40],
      offset: [0, 0],
      anchor_from: 'top_left',
      anchor_to: 'top_left',
      layer: 1,
      $pressed_button_name: 'button.form_button_click',
      $button_text: 'Botão',
      $default_button_background_texture: 'textures/ui/glass_pane',
      $hover_button_background_texture: 'textures/ui/glass_pane_hover',
      $pressed_button_background_texture: 'textures/ui/button_black_hover',
      $font_type: 'MinecraftTen',
      $font_size: 1.8,
      $label_text: 'Botão',
      $text_alignment: 'center',
      $button_text_binding_type: 'collection',
      $button_text_grid_collection_name: 'form_buttons',
      children: []
    }
  },

  label: {
    label: 'Label',
    icon: 'L',
    defaults: {
      type: 'label',
      size: [100, 20],
      offset: [0, 0],
      anchor_from: 'top_left',
      anchor_to: 'top_left',
      layer: 1,
      text: 'Texto',
      font_type: 'MinecraftTen',
      font_scale_factor: 1.0,
      text_alignment: 'left',
      color: '#ffffff',
      shadow: false,
      children: []
    }
  },

  image: {
    label: 'Image',
    icon: 'I',
    defaults: {
      type: 'image',
      size: [64, 64],
      offset: [0, 0],
      anchor_from: 'top_left',
      anchor_to: 'top_left',
      layer: 1,
      texture: 'textures/ui/glass_pane',
      nineslice_size: null,
      base_size: null,
      visible: true,
      children: []
    }
  },

  panel: {
    label: 'Panel',
    icon: 'P',
    defaults: {
      type: 'panel',
      size: [200, 150],
      offset: [0, 0],
      anchor_from: 'top_left',
      anchor_to: 'top_left',
      layer: 0,
      visible: true,
      children: []
    }
  },

  stack_panel: {
    label: 'Stack Panel',
    icon: 'S',
    defaults: {
      type: 'stack_panel',
      size: [200, 150],
      offset: [0, 0],
      anchor_from: 'top_left',
      anchor_to: 'top_left',
      layer: 0,
      orientation: 'vertical',
      visible: true,
      children: []
    }
  }
};

function createComponentElement(type) {
  const def = ComponentDefs[type];
  if (!def) return null;
  return {
    id: Utils.generateId(),
    ...Utils.deepClone(def.defaults),
    _componentType: type,
    _name: def.label,
    children: []
  };
}

function getElementType(el) {
  if (el._componentType) return el._componentType;
  if (el.type === 'label') return 'label';
  if (el.type === 'image') return 'image';
  if (el.type === 'stack_panel') return 'stack_panel';
  if (el.type === 'panel') return 'panel';
  return 'panel';
}

function getElementDisplayName(el) {
  let name = el._name || el._componentType || el.type;
  const label = el.text || el.$label_text || el.$button_text || '';
  if (label) name += `: "${label}"`;
  return name;
}

function flattenTree(elements, result = []) {
  for (const el of elements) {
    result.push(el);
    if (el.children && el.children.length > 0) {
      flattenTree(el.children, result);
    }
  }
  return result;
}

function findById(elements, id) {
  for (const el of elements) {
    if (el.id === id) return el;
    if (el.children) {
      const found = findById(el.children, id);
      if (found) return found;
    }
  }
  return null;
}

function findParent(elements, id, parent = null) {
  for (const el of elements) {
    if (el.id === id) return parent;
    if (el.children) {
      const found = findParent(el.children, id, el);
      if (found !== undefined) return found;
    }
  }
  return undefined;
}

function removeById(elements, id) {
  for (let i = 0; i < elements.length; i++) {
    if (elements[i].id === id) {
      elements.splice(i, 1);
      return true;
    }
    if (elements[i].children && removeById(elements[i].children, id)) {
      return true;
    }
  }
  return false;
}

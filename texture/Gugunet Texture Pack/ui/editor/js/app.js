const App = {
  elements: [],
  selectedElement: null,
  projectName: 'Untitled',
  textureBasePath: '../../textures/',

  init() {
    CanvasRenderer.init(document.getElementById('preview-canvas'));
    DragDrop.init();
    this._bindToolbar();
    this._bindToolButtons();
    this._bindCanvas();
    this._bindKeyboard();
    this._loadTexturesFromServer();
    this.refresh();
  },

  _bindToolbar() {
    document.getElementById('btn-new').addEventListener('click', () => this.newProject());
    document.getElementById('btn-import-project').addEventListener('click', () => this.importProject());
    document.getElementById('btn-save-project').addEventListener('click', () => this.saveProject());
    document.getElementById('btn-import-mc').addEventListener('click', () => this.importMinecraftJson());
    document.getElementById('btn-export').addEventListener('click', () => this.exportMinecraftJson());
    document.getElementById('btn-export-gugunet').addEventListener('click', () => this.exportGugunetUI());
    document.getElementById('btn-validate').addEventListener('click', () => this.validate());
    document.getElementById('btn-undo').addEventListener('click', () => this.undo());
    document.getElementById('btn-redo').addEventListener('click', () => this.redo());
    document.getElementById('btn-zoom-in').addEventListener('click', () => {
      CanvasRenderer.setZoom(CanvasRenderer.zoom + 0.25);
      this.refresh();
    });
    document.getElementById('btn-zoom-out').addEventListener('click', () => {
      CanvasRenderer.setZoom(CanvasRenderer.zoom - 0.25);
      this.refresh();
    });
  },

  _bindToolButtons() {
    const toolBtns = document.querySelectorAll('.tool-btn');
    const setTool = (tool) => {
      CanvasRenderer.setTool(tool);
      toolBtns.forEach(b => b.classList.remove('active'));
      document.getElementById('tool-' + tool)?.classList.add('active');
    };

    document.getElementById('tool-select')?.addEventListener('click', () => setTool('select'));
    document.getElementById('tool-move')?.addEventListener('click', () => setTool('move'));
    document.getElementById('tool-scale')?.addEventListener('click', () => setTool('scale'));
    document.getElementById('tool-snap')?.addEventListener('click', () => {
      CanvasRenderer.snapToGrid = !CanvasRenderer.snapToGrid;
      document.getElementById('tool-snap')?.classList.toggle('active', CanvasRenderer.snapToGrid);
    });
  },

  _bindCanvas() {
    const canvas = document.getElementById('preview-canvas');
    let touchStartTime = 0;
    let touchStartX = 0;
    let touchStartY = 0;

    canvas.addEventListener('click', (e) => {
      if (CanvasRenderer.transform.dragging) return;
      const rect = canvas.getBoundingClientRect();
      const scaleX = canvas.width / rect.width;
      const scaleY = canvas.height / rect.height;
      const mx = (e.clientX - rect.left) * scaleX;
      const my = (e.clientY - rect.top) * scaleY;
      const hit = CanvasRenderer.hitTest(this.elements, mx, my);
      this.selectElement(hit);
    });

    canvas.addEventListener('touchstart', (e) => {
      touchStartTime = Date.now();
      const touch = e.touches[0];
      touchStartX = touch.clientX;
      touchStartY = touch.clientY;
    }, { passive: true });

    canvas.addEventListener('touchend', (e) => {
      if (CanvasRenderer.transform.dragging) return;
      const elapsed = Date.now() - touchStartTime;
      if (elapsed > 300) return;
      const touch = e.changedTouches[0];
      const dx = Math.abs(touch.clientX - touchStartX);
      const dy = Math.abs(touch.clientY - touchStartY);
      if (dx > 10 || dy > 10) return;
      const rect = canvas.getBoundingClientRect();
      const scaleX = canvas.width / rect.width;
      const scaleY = canvas.height / rect.height;
      const mx = (touch.clientX - rect.left) * scaleX;
      const my = (touch.clientY - rect.top) * scaleY;
      const hit = CanvasRenderer.hitTest(this.elements, mx, my);
      this.selectElement(hit);
    }, { passive: true });
  },

  _bindKeyboard() {
    document.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;

      if (e.key === 'Delete' || e.key === 'Backspace') {
        if (this.selectedElement) this.deleteSelected();
      }
      if (e.ctrlKey && e.key === 'z') {
        e.preventDefault();
        this.undo();
      }
      if (e.ctrlKey && (e.key === 'y' || (e.shiftKey && e.key === 'z'))) {
        e.preventDefault();
        this.redo();
      }
      if (e.ctrlKey && e.key === 's') {
        e.preventDefault();
        this.saveProject();
      }
      if (e.ctrlKey && e.key === 'o') {
        e.preventDefault();
        this.importProject();
      }
      if (e.ctrlKey && e.key === 'e') {
        e.preventDefault();
        this.exportGugunetUI();
      }
      if (e.key === 'Escape') {
        this.selectElement(null);
      }
      if (!e.ctrlKey && !e.altKey && !e.metaKey) {
        if (e.key === 'q' || e.key === 'Q') {
          document.getElementById('tool-select')?.click();
        } else if (e.key === 'g' || e.key === 'G') {
          document.getElementById('tool-move')?.click();
        } else if (e.key === 's' || e.key === 'S') {
          if (!e.ctrlKey) document.getElementById('tool-scale')?.click();
        }
      }
    });
  },

  _updateUndoButtons() {
    document.getElementById('btn-undo').disabled = !UndoManager.canUndo();
    document.getElementById('btn-redo').disabled = !UndoManager.canRedo();
  },

  saveState() {
    UndoManager.saveState(this.elements);
    this._updateUndoButtons();
  },

  undo() {
    this.saveState();
    this.elements = UndoManager.undo(this.elements);
    this.selectedElement = null;
    CanvasRenderer.selectedId = null;
    Properties.render(null);
    this.refresh();
  },

  redo() {
    this.elements = UndoManager.redo(this.elements);
    this.selectedElement = null;
    CanvasRenderer.selectedId = null;
    Properties.render(null);
    this.refresh();
  },

  refresh() {
    CanvasRenderer.loadTexturesForElements(this.elements);
    CanvasRenderer.render(this.elements);
    this._renderTree();
    if (this.selectedElement) {
      CanvasRenderer.selectedId = this.selectedElement.id;
    } else {
      CanvasRenderer.selectedId = null;
    }
    CanvasRenderer.render(this.elements);
    this._updateUndoButtons();
  },

  selectElement(el) {
    this.selectedElement = el;
    CanvasRenderer.selectedId = el ? el.id : null;
    Properties.render(el);
    this._renderTree();
    this.refresh();
  },

  addElementToCanvas(type, mx, my) {
    this.saveState();
    const el = createComponentElement(type);
    if (!el) return;
    const x = Math.round(mx / CanvasRenderer.zoom);
    const y = Math.round(my / CanvasRenderer.zoom);
    el.offset = [x, y];
    this.elements.push(el);
    this.selectElement(el);
  },

  moveElement(elementId, mx, my) {
    const el = findById(this.elements, elementId);
    if (!el) return;
    this.saveState();
    const x = Math.round(mx / CanvasRenderer.zoom);
    const y = Math.round(my / CanvasRenderer.zoom);
    el.offset = [x, y];
    this.refresh();
  },

  deleteSelected() {
    if (!this.selectedElement) return;
    this.saveState();
    removeById(this.elements, this.selectedElement.id);
    this.selectedElement = null;
    CanvasRenderer.selectedId = null;
    Properties.render(null);
    this.refresh();
  },

  moveElementInTree(elementId, targetId, position) {
    this.saveState();
    const el = findById(this.elements, elementId);
    if (!el) return;
    removeById(this.elements, elementId);

    if (position === 'inside') {
      const target = findById(this.elements, targetId);
      if (target) {
        if (!target.children) target.children = [];
        target.children.push(el);
      }
    } else {
      const target = findById(this.elements, targetId);
      const parent = findParent(this.elements, targetId);
      if (parent && parent.children) {
        const idx = parent.children.findIndex(c => c.id === targetId);
        if (position === 'before') {
          parent.children.splice(idx, 0, el);
        } else {
          parent.children.splice(idx + 1, 0, el);
        }
      } else {
        const idx = this.elements.findIndex(c => c.id === targetId);
        if (position === 'before') {
          this.elements.splice(idx, 0, el);
        } else {
          this.elements.splice(idx + 1, 0, el);
        }
      }
    }
    this.refresh();
  },

  _renderTree() {
    const tree = document.getElementById('element-tree');
    tree.innerHTML = this._renderTreeLevel(this.elements, 0);

    tree.querySelectorAll('.tree-item').forEach(item => {
      const selectFromItem = () => {
        const el = findById(this.elements, item.dataset.id);
        this.selectElement(el);
      };

      item.addEventListener('click', selectFromItem);

      let tapTimer = null;
      item.addEventListener('touchstart', (e) => {
        tapTimer = setTimeout(selectFromItem, 200);
      }, { passive: true });
      item.addEventListener('touchend', () => {
        clearTimeout(tapTimer);
      });
      item.addEventListener('touchmove', () => {
        clearTimeout(tapTimer);
      }, { passive: true });

      item.addEventListener('dragstart', (e) => {
        e.dataTransfer.setData('text/plain', item.dataset.id);
        e.dataTransfer.effectAllowed = 'move';
        DragDrop.dragData = { source: 'tree', elementId: item.dataset.id };
      });

      item.addEventListener('dragover', (e) => {
        e.preventDefault();
        const rect = item.getBoundingClientRect();
        const midY = rect.top + rect.height / 2;
        item.classList.remove('drop-before', 'drop-after', 'drop-inside');
        if (e.clientY < midY) {
          item.classList.add('drop-before');
        } else if (e.clientY > midY) {
          item.classList.add('drop-after');
        } else {
          item.classList.add('drop-inside');
        }
      });

      item.addEventListener('dragleave', () => {
        item.classList.remove('drop-before', 'drop-after', 'drop-inside');
      });

      item.addEventListener('drop', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const sourceId = e.dataTransfer.getData('text/plain');
        if (sourceId === item.dataset.id) return;

        const rect = item.getBoundingClientRect();
        const midY = rect.top + rect.height / 2;
        let pos = 'after';
        if (e.clientY < midY) pos = 'before';
        else if (e.target.classList.contains('drop-inside')) pos = 'inside';

        this.moveElementInTree(sourceId, item.dataset.id, pos);
      });

      item.addEventListener('dragend', () => {
        tree.querySelectorAll('.tree-item').forEach(i => {
          i.classList.remove('drop-before', 'drop-after', 'drop-inside');
        });
      });
    });
  },

  _renderTreeLevel(elements, depth) {
    let html = '';
    for (const el of elements) {
      const indent = '<span class="tree-indent"></span>'.repeat(depth);
      const selected = this.selectedElement && this.selectedElement.id === el.id ? ' selected' : '';
      const type = getElementType(el);
      const name = getElementDisplayName(el);
      const hasChildren = el.children && el.children.length > 0;
      html += `<div class="tree-item${selected}" data-id="${el.id}" draggable="true">
        ${indent}<span class="tree-expand">${hasChildren ? '▸' : ''}</span>
        <span>${Utils.escapeHtml(name)}</span>
        <span class="tree-type">${type}</span>
      </div>`;
      if (el.children) {
        html += this._renderTreeLevel(el.children, depth + 1);
      }
    }
    return html;
  },

  _loadTexturesFromServer() {
    const paths = [
      'textures/ui/glass_pane',
      'textures/ui/glass_pane_hover',
      'textures/ui/button_black_hover',
      'textures/green_default',
      'textures/green_hover',
      'textures/green_pressed',
      'textures/red_default_button',
      'textures/red_hover_button',
      'textures/red_pressed_button',
      'textures/background_form',
      'textures/dropdown_background2'
    ];
    for (const p of paths) {
      CanvasRenderer.loadTexture(this.textureBasePath + p + '.png');
    }
  },

  newProject() {
    if (this.elements.length > 0 && !confirm('Criar novo projeto? Elementos não salvos serão perdidos.')) return;
    UndoManager.clear();
    this.elements = [];
    this.selectedElement = null;
    this.projectName = 'Untitled';
    Properties.render(null);
    this.refresh();
  },

  saveProject() {
    const data = {
      version: 1,
      name: this.projectName,
      parentW: CanvasRenderer.parentW,
      parentH: CanvasRenderer.parentH,
      elements: this.elements
    };
    Utils.downloadFile(
      (this.projectName || 'project') + '.jsonui',
      JSON.stringify(data, null, 2),
      'application/json'
    );
  },

  importProject() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.jsonui,.json';
    input.onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      try {
        const text = await Utils.readFile(file);
        const data = JSON.parse(text);
        if (data.version && data.elements) {
          UndoManager.clear();
          this.elements = data.elements;
          this.projectName = data.name || 'Untitled';
          if (data.parentW) CanvasRenderer.parentW = data.parentW;
          if (data.parentH) CanvasRenderer.parentH = data.parentH;
        } else {
          this.elements = [];
        }
        this.selectedElement = null;
        Properties.render(null);
        this.refresh();
      } catch (err) {
        alert('Erro ao importar: ' + err.message);
      }
    };
    input.click();
  },

  importMinecraftJson() {
    const overlay = document.getElementById('modal-overlay');
    const title = document.getElementById('modal-title');
    const body = document.getElementById('modal-body');
    const footer = document.getElementById('modal-footer');

    title.textContent = 'Importar JSON do Minecraft';
    body.innerHTML = `
      <div style="margin-bottom: 8px;">
        <label>Namespace</label>
        <input type="text" id="import-namespace" value="gugunet" style="margin-top: 4px;">
      </div>
      <textarea id="import-json-input" placeholder="Cole o JSON aqui..."></textarea>
    `;
    footer.innerHTML = `
      <button id="modal-cancel">Cancelar</button>
      <button id="modal-import" style="background: var(--accent); color: #000;">Importar</button>
    `;
    overlay.classList.remove('hidden');

    document.getElementById('modal-cancel').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-close').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-import').onclick = () => {
      const json = document.getElementById('import-json-input').value;
      const ns = document.getElementById('import-namespace').value || 'gugunet';
      try {
        const parsed = JSON.parse(json);
        this.saveState();
        this.elements = Importer.fromMinecraftJson(parsed, ns);
        this.selectedElement = null;
        Properties.render(null);
        this.refresh();
        overlay.classList.add('hidden');
      } catch (err) {
        alert('JSON inválido: ' + err.message);
      }
    };
  },

  exportMinecraftJson() {
    const overlay = document.getElementById('modal-overlay');
    const title = document.getElementById('modal-title');
    const body = document.getElementById('modal-body');
    const footer = document.getElementById('modal-footer');

    const json = Exporter.toMinecraftJson(this.elements, 'gugunet');
    title.textContent = 'Exportar JSON para Minecraft';
    body.innerHTML = `<textarea id="export-json-output" readonly>${Utils.escapeHtml(json)}</textarea>`;
    footer.innerHTML = `
      <button id="modal-cancel">Fechar</button>
      <button id="modal-copy" style="background: var(--accent); color: #000;">Copiar</button>
      <button id="modal-download" style="background: var(--success); color: #000;">Download</button>
    `;
    overlay.classList.remove('hidden');

    document.getElementById('modal-cancel').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-close').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-copy').onclick = () => {
      navigator.clipboard.writeText(json).then(() => {
        document.getElementById('modal-copy').textContent = 'Copiado!';
        setTimeout(() => {
          document.getElementById('modal-copy').textContent = 'Copiar';
        }, 1500);
      });
    };
    document.getElementById('modal-download').onclick = () => {
      Utils.downloadFile('custom_minigame_menu.json', json, 'application/json');
    };
  },

  exportGugunetUI() {
    const overlay = document.getElementById('modal-overlay');
    const title = document.getElementById('modal-title');
    const body = document.getElementById('modal-body');
    const footer = document.getElementById('modal-footer');

    const data = {
      version: 1,
      meta: {
        name: this.projectName,
        author: 'Editor',
        created: new Date().toISOString().split('T')[0],
        description: 'Menu criado no Gugunet JSON UI Editor'
      },
      target: {
        file: 'custom_minigame_menu.json',
        namespace: 'gugunet',
        rootElement: 'gugunet'
      },
      elements: this.elements,
      textures: [],
      instructions: 'Implementar este menu no resource pack do servidor'
    };

    const json = JSON.stringify(data, null, 2);
    title.textContent = 'Exportar .gugunet-ui';
    body.innerHTML = `
      <p style="margin-bottom: 8px; color: var(--text-secondary);">
        Este formato é para enviar ao assistente implementar no servidor.
      </p>
      <textarea id="export-gugunet-ui" readonly>${Utils.escapeHtml(json)}</textarea>
    `;
    footer.innerHTML = `
      <button id="modal-cancel">Fechar</button>
      <button id="modal-copy" style="background: var(--accent); color: #000;">Copiar</button>
      <button id="modal-download" style="background: var(--success); color: #000;">Download .gugunet-ui</button>
    `;
    overlay.classList.remove('hidden');

    document.getElementById('modal-cancel').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-close').onclick = () => overlay.classList.add('hidden');
    document.getElementById('modal-copy').onclick = () => {
      navigator.clipboard.writeText(json).then(() => {
        document.getElementById('modal-copy').textContent = 'Copiado!';
        setTimeout(() => {
          document.getElementById('modal-copy').textContent = 'Copiar';
        }, 1500);
      });
    };
    document.getElementById('modal-download').onclick = () => {
      Utils.downloadFile(
        (this.projectName || 'menu') + '.gugunet-ui',
        json,
        'application/json'
      );
    };
  },

  validate() {
    const issues = Validator.validate(this.elements);
    Validator.renderOutput(document.getElementById('validation-output'), issues);
  }
};

document.addEventListener('DOMContentLoaded', () => App.init());

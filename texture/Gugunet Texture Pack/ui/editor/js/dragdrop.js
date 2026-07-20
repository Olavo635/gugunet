const DragDrop = {
  dragData: null,

  init() {
    this._initComponentDrag();
    this._initComponentTouch();
    this._initCanvasDrop();
    this._initTreeDrag();
  },

  _initComponentDrag() {
    const items = document.querySelectorAll('.component-item[draggable]');
    items.forEach(item => {
      item.addEventListener('dragstart', (e) => {
        this.dragData = { source: 'palette', type: item.dataset.type };
        e.dataTransfer.effectAllowed = 'copy';
        e.dataTransfer.setData('text/plain', item.dataset.type);
        item.classList.add('dragging');
      });
      item.addEventListener('dragend', () => {
        item.classList.remove('dragging');
        this.dragData = null;
      });
    });
  },

  _initComponentTouch() {
    const items = document.querySelectorAll('.component-item');
    let touchTimer = null;

    items.forEach(item => {
      item.addEventListener('touchstart', (e) => {
        touchTimer = setTimeout(() => {
          item.classList.add('long-press');
        }, 400);
      }, { passive: true });

      item.addEventListener('touchend', (e) => {
        clearTimeout(touchTimer);
        item.classList.remove('long-press');
        const type = item.dataset.type;
        if (type) {
          const canvas = document.getElementById('preview-canvas');
          const rect = canvas.getBoundingClientRect();
          App.addElementToCanvas(type, rect.width / 2, rect.height / 2);
        }
      });

      item.addEventListener('touchmove', () => {
        clearTimeout(touchTimer);
        item.classList.remove('long-press');
      }, { passive: true });
    });
  },

  _initCanvasDrop() {
    const canvas = document.getElementById('preview-canvas');
    canvas.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = this.dragData?.source === 'palette' ? 'copy' : 'move';
    });
    canvas.addEventListener('drop', (e) => {
      e.preventDefault();
      if (!this.dragData) return;
      const rect = canvas.getBoundingClientRect();
      const scaleX = canvas.width / rect.width;
      const scaleY = canvas.height / rect.height;
      const mx = (e.clientX - rect.left) * scaleX;
      const my = (e.clientY - rect.top) * scaleY;
      if (this.dragData.source === 'palette') {
        App.addElementToCanvas(this.dragData.type, mx, my);
      } else if (this.dragData.source === 'tree') {
        App.moveElement(this.dragData.elementId, mx, my);
      }
      this.dragData = null;
    });
  },

  _initTreeDrag() {
    const tree = document.getElementById('element-tree');
    tree.addEventListener('dragstart', (e) => {
      const item = e.target.closest('.tree-item');
      if (!item) return;
      this.dragData = { source: 'tree', elementId: item.dataset.id };
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', item.dataset.id);
    });
    tree.addEventListener('dragend', () => {
      this.dragData = null;
    });
  }
};

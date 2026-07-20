const CanvasRenderer = {
  canvas: null,
  ctx: null,
  zoom: 1.5,
  parentW: 272,
  parentH: 240,
  selectedId: null,
  hoveredId: null,
  textures: {},

  tool: 'select',
  transform: {
    dragging: false,
    type: null,
    startX: 0,
    startY: 0,
    origOffset: null,
    origSize: null,
    handle: null
  },
  gridSize: 8,
  snapToGrid: true,

  HANDLE_SIZE: 8,
  handles: [],

  init(canvasEl) {
    this.canvas = canvasEl;
    this.ctx = canvasEl.getContext('2d');
    this.resize();
    this._initTransformEvents();
  },

  resize() {
    const w = Math.round(this.parentW * this.zoom);
    const h = Math.round(this.parentH * this.zoom);
    this.canvas.width = w;
    this.canvas.height = h;
    this.canvas.style.width = w + 'px';
    this.canvas.style.height = h + 'px';
    document.getElementById('canvas-size').textContent = `${this.parentW} x ${this.parentH}`;
    document.getElementById('zoom-level').textContent = Math.round(this.zoom * 100) + '%';
  },

  setZoom(z) {
    this.zoom = Utils.clamp(z, 0.5, 6);
    this.resize();
  },

  setTool(tool) {
    this.tool = tool;
    this.canvas.style.cursor = {
      select: 'default',
      move: 'move',
      scale: 'nwse-resize'
    }[tool] || 'default';
  },

  snap(val) {
    if (!this.snapToGrid) return val;
    return Math.round(val / this.gridSize) * this.gridSize;
  },

  loadTexture(path, callback) {
    if (this.textures[path]) {
      if (callback) callback(this.textures[path]);
      return;
    }
    const img = new Image();
    img.onload = () => {
      this.textures[path] = img;
      if (callback) callback(img);
    };
    img.onerror = () => {
      this.textures[path] = null;
      if (callback) callback(null);
    };
    img.src = path;
  },

  render(elements) {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    ctx.save();
    ctx.scale(this.zoom, this.zoom);

    ctx.fillStyle = '#2a2a2a';
    ctx.fillRect(0, 0, this.parentW, this.parentH);

    this._drawGrid(ctx);

    this._renderChildren(ctx, elements, this.parentW, this.parentH, 0, 0);

    const selected = this.selectedId ? findById(elements, this.selectedId) : null;
    if (selected) {
      this._drawGizmo(ctx, selected);
    }

    ctx.restore();
  },

  _drawGrid(ctx) {
    if (!this.snapToGrid) return;
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.lineWidth = 0.5;
    for (let x = 0; x <= this.parentW; x += this.gridSize) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, this.parentH);
      ctx.stroke();
    }
    for (let y = 0; y <= this.parentH; y += this.gridSize) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(this.parentW, y);
      ctx.stroke();
    }
  },

  _renderChildren(ctx, elements, parentW, parentH, px, py) {
    const sorted = [...elements].sort((a, b) => (a.layer || 0) - (b.layer || 0));
    for (const el of sorted) {
      if (el.visible === false) continue;
      this._renderElement(ctx, el, parentW, parentH, px, py);
    }
  },

  _renderElement(ctx, el, parentW, parentH, px, py) {
    const type = getElementType(el);
    const size = Utils.resolveSize(el.size, parentW, parentH);
    const pos = Utils.resolvePosition(el, parentW, parentH, size.w, size.h);
    const x = px + pos.x;
    const y = py + pos.y;

    ctx.save();

    if (type === 'panel' || type === 'stack_panel') {
      ctx.fillStyle = 'rgba(50, 50, 80, 0.3)';
      ctx.fillRect(x, y, size.w, size.h);
      ctx.strokeStyle = el.id === this.selectedId ? '#4fc3f7' : 'rgba(100, 100, 160, 0.5)';
      ctx.lineWidth = el.id === this.selectedId ? 1.5 : 0.5;
      ctx.strokeRect(x, y, size.w, size.h);

      if (el.children && el.children.length > 0) {
        let childX = x, childY = y;
        for (const child of el.children) {
          if (child.visible === false) continue;
          if (type === 'stack_panel') {
            const cSize = Utils.resolveSize(child.size, size.w, size.h);
            this._renderElement(ctx, child, size.w, size.h, childX, childY);
            if (el.orientation === 'vertical') childY += cSize.h;
            else childX += cSize.w;
          } else {
            this._renderElement(ctx, child, size.w, size.h, x, y);
          }
        }
      }
    } else if (type === 'button') {
      this._renderButton(ctx, el, x, y, size.w, size.h);
    } else if (type === 'label') {
      this._renderLabel(ctx, el, x, y, size.w, size.h);
    } else if (type === 'image') {
      this._renderImage(ctx, el, x, y, size.w, size.h);
    }

    ctx.restore();
  },

  _renderButton(ctx, el, x, y, w, h) {
    const texturePath = el.$default_button_background_texture || 'textures/ui/glass_pane';
    const img = this.textures[texturePath];

    if (img && el.nineslice_size && el.base_size) {
      Utils.ninesliceDraw(ctx, img, x, y, w, h, el.nineslice_size, el.base_size);
    } else if (img && img.complete && img.naturalWidth > 0) {
      ctx.drawImage(img, x, y, w, h);
    } else {
      ctx.fillStyle = '#3a6a3a';
      ctx.fillRect(x, y, w, h);
      ctx.strokeStyle = '#5a8a5a';
      ctx.lineWidth = 1;
      ctx.strokeRect(x, y, w, h);
    }

    ctx.strokeStyle = el.id === this.selectedId ? '#4fc3f7' : 'rgba(255,255,255,0.2)';
    ctx.lineWidth = el.id === this.selectedId ? 1.5 : 1;
    ctx.strokeRect(x, y, w, h);

    const labelText = el.$label_text || el.$button_text || '';
    if (labelText) {
      const fontSize = (el.$font_size || 1.8) * 8;
      const fontType = el.$font_type || 'MinecraftTen';
      ctx.fillStyle = '#ffffff';
      ctx.font = `${fontSize}px ${fontType}, monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(labelText, x + w / 2, y + h / 2);
      ctx.textAlign = 'left';
      ctx.textBaseline = 'alphabetic';
    }
  },

  _renderLabel(ctx, el, x, y, w, h) {
    const text = el.text || '';
    if (!text) return;
    const fontSize = (el.font_scale_factor || 1) * 8;
    const fontType = el.font_type || 'MinecraftTen';
    ctx.fillStyle = el.color || '#ffffff';
    ctx.font = `${fontSize}px ${fontType}, monospace`;
    ctx.textAlign = el.text_alignment || 'left';
    ctx.textBaseline = 'top';
    const align = el.text_alignment || 'left';
    let tx = x;
    if (align === 'center') tx = x + w / 2;
    else if (align === 'right') tx = x + w;
    ctx.fillText(text, tx, y);
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';
  },

  _renderImage(ctx, el, x, y, w, h) {
    const texturePath = el.texture;
    if (!texturePath) {
      ctx.fillStyle = 'rgba(255, 150, 0, 0.3)';
      ctx.fillRect(x, y, w, h);
      ctx.strokeStyle = '#ff9800';
      ctx.lineWidth = 1;
      ctx.strokeRect(x, y, w, h);
      ctx.fillStyle = '#aaa';
      ctx.font = '10px monospace';
      ctx.textAlign = 'center';
      ctx.fillText('sem texture', x + w / 2, y + h / 2);
      ctx.textAlign = 'left';
      return;
    }
    const img = this.textures[texturePath];
    if (img && el.nineslice_size && el.base_size) {
      Utils.ninesliceDraw(ctx, img, x, y, w, h, el.nineslice_size, el.base_size);
    } else if (img && img.complete && img.naturalWidth > 0) {
      ctx.drawImage(img, x, y, w, h);
    } else {
      ctx.fillStyle = 'rgba(100, 100, 100, 0.3)';
      ctx.fillRect(x, y, w, h);
      ctx.strokeStyle = '#888';
      ctx.lineWidth = 1;
      ctx.strokeRect(x, y, w, h);
      ctx.fillStyle = '#aaa';
      ctx.font = '9px monospace';
      ctx.textAlign = 'center';
      ctx.fillText(texturePath.split('/').pop(), x + w / 2, y + h / 2);
      ctx.textAlign = 'left';
    }
  },

  _drawGizmo(ctx, el) {
    const parentW = this.parentW;
    const parentH = this.parentH;
    const size = Utils.resolveSize(el.size, parentW, parentH);
    const pos = Utils.resolvePosition(el, parentW, parentH, size.w, size.h);
    const x = pos.x;
    const y = pos.y;
    const w = size.w;
    const h = size.h;
    const hs = this.HANDLE_SIZE / this.zoom;

    ctx.strokeStyle = '#4fc3f7';
    ctx.lineWidth = 1.5 / this.zoom;
    ctx.setLineDash([4 / this.zoom, 3 / this.zoom]);
    ctx.strokeRect(x, y, w, h);
    ctx.setLineDash([]);

    this.handles = [];

    const handlePositions = {
      tl: { x: x, y: y, cursor: 'nwse-resize', axis: 'both', sign: -1 },
      tr: { x: x + w, y: y, cursor: 'nesw-resize', axis: 'x', sign: 1 },
      bl: { x: x, y: y + h, cursor: 'nesw-resize', axis: 'y', sign: -1 },
      br: { x: x + w, y: y + h, cursor: 'nwse-resize', axis: 'both', sign: 1 },
      tm: { x: x + w / 2, y: y, cursor: 'ns-resize', axis: 'y', sign: -1 },
      bm: { x: x + w / 2, y: y + h, cursor: 'ns-resize', axis: 'y', sign: 1 },
      ml: { x: x, y: y + h / 2, cursor: 'ew-resize', axis: 'x', sign: -1 },
      mr: { x: x + w, y: y + h / 2, cursor: 'ew-resize', axis: 'x', sign: 1 },
    };

    for (const [key, h] of Object.entries(handlePositions)) {
      ctx.fillStyle = '#4fc3f7';
      ctx.fillRect(h.x - hs / 2, h.y - hs / 2, hs, hs);
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 1 / this.zoom;
      ctx.strokeRect(h.x - hs / 2, h.y - hs / 2, hs, hs);
      this.handles.push({ ...h, key });
    }

    const centerX = x + w / 2;
    const centerY = y + h / 2;
    ctx.fillStyle = '#ffa726';
    ctx.beginPath();
    ctx.arc(centerX, centerY, 3 / this.zoom, 0, Math.PI * 2);
    ctx.fill();
  },

  _initTransformEvents() {
    let lastX = 0, lastY = 0;

    const getMousePos = (e) => {
      const rect = this.canvas.getBoundingClientRect();
      return {
        x: (e.clientX - rect.left) / this.zoom,
        y: (e.clientY - rect.top) / this.zoom
      };
    };

    const getTouchPos = (e) => {
      const touch = e.touches[0] || e.changedTouches[0];
      const rect = this.canvas.getBoundingClientRect();
      return {
        x: (touch.clientX - rect.left) / this.zoom,
        y: (touch.clientY - rect.top) / this.zoom
      };
    };

    const hitTestHandles = (mx, my) => {
      const hs = this.HANDLE_SIZE / this.zoom + 4 / this.zoom;
      for (const h of this.handles) {
        if (Math.abs(mx - h.x) < hs && Math.abs(my - h.y) < hs) {
          return h;
        }
      }
      return null;
    };

    const startDrag = (mx, my) => {
      if (this.tool === 'select' || this.tool === 'move' || this.tool === 'scale') {
        const hit = hitTestHandles(mx, my);
        if (hit && this.selectedId) {
          const el = findById(App.elements, this.selectedId);
          if (el) {
            const parentW = this.parentW;
            const parentH = this.parentH;
            const size = Utils.resolveSize(el.size, parentW, parentH);
            this.transform = {
              dragging: true,
              type: this.tool === 'scale' ? 'scale' : (hit.axis === 'both' || this.tool === 'scale' ? 'scale' : 'move'),
              startX: mx,
              startY: my,
              origOffset: [...(el.offset || [0, 0])],
              origSize: [...size],
              handle: hit,
              element: el
            };
            lastX = mx;
            lastY = my;
            return;
          }
        }

        if (this.tool === 'move' && this.selectedId) {
          const el = findById(App.elements, this.selectedId);
          if (el) {
            this.transform = {
              dragging: true,
              type: 'move',
              startX: mx,
              startY: my,
              origOffset: [...(el.offset || [0, 0])],
              origSize: null,
              handle: null,
              element: el
            };
            lastX = mx;
            lastY = my;
            return;
          }
        }
      }
    };

    const doDrag = (mx, my) => {
      if (!this.transform.dragging || !this.transform.element) return;
      const el = this.transform.element;
      const dx = mx - lastX;
      const dy = my - lastY;

      if (this.transform.type === 'move') {
        const newOffX = this.snap(this.transform.origOffset[0] + (mx - this.transform.startX));
        const newOffY = this.snap(this.transform.origOffset[1] + (my - this.transform.startY));
        el.offset = [newOffX, newOffY];
      } else if (this.transform.type === 'scale' && this.transform.handle) {
        const h = this.transform.handle;
        const origW = this.transform.origSize[0];
        const origH = this.transform.origSize[1];
        const diffX = mx - this.transform.startX;
        const diffY = my - this.transform.startY;

        let newW = origW;
        let newH = origH;

        if (h.axis === 'x' || h.axis === 'both') {
          newW = this.snap(Math.max(16, origW + diffX * h.sign));
        }
        if (h.axis === 'y' || h.axis === 'both') {
          newH = this.snap(Math.max(16, origH + diffY * h.sign));
        }

        if (!el.size) el.size = [100, 100];
        el.size = [newW, newH];
      }

      lastX = mx;
      lastY = my;
      App.refresh();
    };

    const endDrag = () => {
      if (this.transform.dragging) {
        this.transform.dragging = false;
        this.transform.element = null;
      }
    };

    this.canvas.addEventListener('mousedown', (e) => {
      const pos = getMousePos(e);
      startDrag(pos.x, pos.y);
    });
    this.canvas.addEventListener('mousemove', (e) => {
      const pos = getMousePos(e);
      doDrag(pos.x, pos.y);

      const handle = hitTestHandles(pos.x, pos.y);
      if (handle) {
        this.canvas.style.cursor = handle.cursor;
      } else if (!this.transform.dragging) {
        this.canvas.style.cursor = {
          select: 'default',
          move: 'move',
          scale: 'nwse-resize'
        }[this.tool] || 'default';
      }
    });
    this.canvas.addEventListener('mouseup', endDrag);
    this.canvas.addEventListener('mouseleave', endDrag);

    this.canvas.addEventListener('touchstart', (e) => {
      const pos = getTouchPos(e);
      startDrag(pos.x, pos.y);
    }, { passive: true });

    this.canvas.addEventListener('touchmove', (e) => {
      if (this.transform.dragging) {
        e.preventDefault();
        const pos = getTouchPos(e);
        doDrag(pos.x, pos.y);
      }
    }, { passive: false });

    this.canvas.addEventListener('touchend', endDrag, { passive: true });
  },

  hitTest(elements, mx, my) {
    const x = mx / this.zoom;
    const y = my / this.zoom;
    return this._hitTestRecursive(elements, this.parentW, this.parentH, 0, 0, x, y);
  },

  _hitTestRecursive(elements, parentW, parentH, px, py, mx, my) {
    const sorted = [...elements].sort((a, b) => (b.layer || 0) - (a.layer || 0));
    for (const el of sorted) {
      if (el.visible === false) continue;
      const size = Utils.resolveSize(el.size, parentW, parentH);
      const pos = Utils.resolvePosition(el, parentW, parentH, size.w, size.h);
      const x = px + pos.x;
      const y = py + pos.y;

      if (mx >= x && mx <= x + size.w && my >= y && my <= y + size.h) {
        const type = getElementType(el);
        if ((type === 'panel' || type === 'stack_panel') && el.children && el.children.length > 0) {
          const child = this._hitTestRecursive(el.children, size.w, size.h, x, y, mx, my);
          if (child) return child;
        }
        return el;
      }
    }
    return null;
  },

  loadTexturesForElements(elements) {
    for (const el of elements) {
      if (el.type === 'image' && el.texture) {
        this.loadTexture(el.texture);
      }
      if (el.$default_button_background_texture) {
        this.loadTexture(el.$default_button_background_texture);
      }
      if (el.$hover_button_background_texture) {
        this.loadTexture(el.$hover_button_background_texture);
      }
      if (el.$pressed_button_background_texture) {
        this.loadTexture(el.$pressed_button_background_texture);
      }
      if (el.children) {
        this.loadTexturesForElements(el.children);
      }
    }
  }
};

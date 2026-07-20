const Utils = {
  generateId() {
    return 'el_' + Math.random().toString(36).substr(2, 9);
  },

  deepClone(obj) {
    return JSON.parse(JSON.stringify(obj));
  },

  clamp(val, min, max) {
    return Math.max(min, Math.min(max, val));
  },

  parseSize(val) {
    if (Array.isArray(val)) return [...val];
    if (typeof val === 'string') {
      if (val === 'fill') return ['fill', 'default'];
      const m = val.match(/^([\d.]+)%$/);
      if (m) return [val, val];
    }
    return [100, 100];
  },

  formatSize(size) {
    if (!size || !Array.isArray(size)) return [100, 100];
    return size.map(v => {
      if (typeof v === 'number') return Math.round(v * 10) / 10;
      return v;
    });
  },

  parseAnchor(str) {
    const map = {
      'top_left': { x: 0, y: 0 },
      'top_middle': { x: 0.5, y: 0 },
      'top_right': { x: 1, y: 0 },
      'left': { x: 0, y: 0.5 },
      'center': { x: 0.5, y: 0.5 },
      'right': { x: 1, y: 0.5 },
      'bottom_left': { x: 0, y: 1 },
      'bottom_middle': { x: 0.5, y: 1 },
      'bottom_right': { x: 1, y: 1 }
    };
    return map[str] || map['top_left'];
  },

  resolveSize(size, parentW, parentH) {
    if (!size || !Array.isArray(size)) return { w: 100, h: 100 };
    let w = size[0], h = size[1];
    if (typeof w === 'string') {
      if (w.endsWith('%')) w = parentW * parseFloat(w) / 100;
      else if (w === 'fill') w = parentW;
      else w = parseFloat(w) || 0;
    }
    if (typeof h === 'string') {
      if (h.endsWith('%')) h = parentH * parseFloat(h) / 100;
      else if (h === 'fill') h = parentH;
      else if (h.endsWith('%c')) h = 20;
      else h = parseFloat(h) || 0;
    }
    return { w: Math.max(0, w), h: Math.max(0, h) };
  },

  resolvePosition(element, parentW, parentH, childW, childH) {
    const anchor = Utils.parseAnchor(element.anchor_from || 'top_left');
    const parentAnchor = Utils.parseAnchor(element.anchor_to || 'top_left');
    const offset = element.offset || [0, 0];
    const x = parentW * parentAnchor.x - childW * anchor.x + offset[0];
    const y = parentH * parentAnchor.y - childH * anchor.y + offset[1];
    return { x, y };
  },

  ninesliceDraw(ctx, img, x, y, w, h, nineslice, baseSize) {
    if (!nineslice || !baseSize || !img || !img.complete) {
      if (img && img.complete) ctx.drawImage(img, x, y, w, h);
      return;
    }
    let sl, st, sr, sb;
    if (typeof nineslice === 'number') {
      sl = st = sr = sb = nineslice;
    } else if (Array.isArray(nineslice)) {
      sl = nineslice[0] || 0;
      st = nineslice[1] || 0;
      sr = nineslice[2] || 0;
      sb = nineslice[3] || 0;
    } else { return; }

    const bw = Array.isArray(baseSize) ? baseSize[0] : baseSize;
    const bh = Array.isArray(baseSize) ? baseSize[1] : baseSize;

    const cols = [sl, bw - sl - sr, sr];
    const rows = [st, bh - st - sb, sb];
    const destCols = [sl, Math.max(0, w - sl - sr), sr];
    const destRows = [st, Math.max(0, h - st - sb), sb];

    let sy = 0;
    for (let r = 0; r < 3; r++) {
      if (rows[r] <= 0 || destRows[r] <= 0) { sy += rows[r]; continue; }
      let sx = 0;
      let dy = y + (r === 0 ? 0 : destRows[0] + destRows[1]);
      for (let c = 0; c < 3; c++) {
        if (cols[c] > 0 && destCols[c] > 0) {
          ctx.drawImage(img, sx, sy, cols[c], rows[c],
            x + (c === 0 ? 0 : destCols[0] + destCols[1]),
            dy, destCols[c], destRows[r]);
        }
        sx += cols[c];
      }
      sy += rows[r];
    }
  },

  escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },

  downloadFile(filename, content, type) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  },

  readFile(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsText(file);
    });
  }
};

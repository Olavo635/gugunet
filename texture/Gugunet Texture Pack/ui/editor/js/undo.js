const UndoManager = {
  undoStack: [],
  redoStack: [],
  maxHistory: 50,

  saveState(elements) {
    this.undoStack.push(Utils.deepClone(elements));
    if (this.undoStack.length > this.maxHistory) {
      this.undoStack.shift();
    }
    this.redoStack = [];
  },

  undo(elements) {
    if (this.undoStack.length === 0) return elements;
    this.redoStack.push(Utils.deepClone(elements));
    return this.undoStack.pop();
  },

  redo(elements) {
    if (this.redoStack.length === 0) return elements;
    this.undoStack.push(Utils.deepClone(elements));
    return this.redoStack.pop();
  },

  canUndo() {
    return this.undoStack.length > 0;
  },

  canRedo() {
    return this.redoStack.length > 0;
  },

  clear() {
    this.undoStack = [];
    this.redoStack = [];
  }
};

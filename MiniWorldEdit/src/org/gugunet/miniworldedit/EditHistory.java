package org.gugunet.miniworldedit;

import java.util.List;
import java.util.Stack;

public class EditHistory {
    private final Stack<List<BlockChange>> undoStack = new Stack<>();
    private final Stack<List<BlockChange>> redoStack = new Stack<>();
    private static final int MAX_HISTORY_SIZE = 15;

    public void addChange(List<BlockChange> changes) {
        if (changes == null || changes.isEmpty()) return;
        if (undoStack.size() >= MAX_HISTORY_SIZE) {
            undoStack.remove(0); // Remove oldest to conserve memory
        }
        undoStack.push(changes);
        redoStack.clear(); // Clear redo stack on a new block-setting operation
    }

    public List<BlockChange> getUndo() {
        if (undoStack.isEmpty()) return null;
        List<BlockChange> changes = undoStack.pop();
        redoStack.push(changes);
        return changes;
    }

    public List<BlockChange> getRedo() {
        if (redoStack.isEmpty()) return null;
        List<BlockChange> changes = redoStack.pop();
        undoStack.push(changes);
        return changes;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}

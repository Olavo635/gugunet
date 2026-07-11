package org.gugunet.miniworldedit;

import cn.nukkit.level.Position;
import cn.nukkit.scheduler.TaskHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerSession {
    private Position pos1;
    private Position pos2;
    private final EditHistory history = new EditHistory();
    private Clipboard clipboard;
    private TaskHandler currentTask;
    
    // Paste preview support
    private List<BlockChange> lastPreviewChanges;
    private Position pasteBase;
    private Clipboard pasteClipboard;
    
    // Adjust stick configuration
    private int adjustActionIndex = 0; // 0 = Mover, 1 = Rotacionar, 2 = Sobrescrever Ar
    private int adjustDistance = 5;
    private boolean pasteAir = true;
    
    // Debug stick selected properties map: blockId -> propertyIndex
    private final Map<String, Integer> selectedProperties = new HashMap<>();

    public Position getPos1() {
        return pos1;
    }

    public void setPos1(Position pos1) {
        this.pos1 = pos1;
    }

    public Position getPos2() {
        return pos2;
    }

    public void setPos2(Position pos2) {
        this.pos2 = pos2;
    }

    public EditHistory getHistory() {
        return history;
    }

    public Clipboard getClipboard() {
        return clipboard;
    }

    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    public TaskHandler getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(TaskHandler currentTask) {
        this.currentTask = currentTask;
    }

    public boolean isProcessing() {
        return currentTask != null && !currentTask.isCancelled();
    }

    public List<BlockChange> getLastPreviewChanges() {
        return lastPreviewChanges;
    }

    public void setLastPreviewChanges(List<BlockChange> lastPreviewChanges) {
        this.lastPreviewChanges = lastPreviewChanges;
    }

    public Position getPasteBase() {
        return pasteBase;
    }

    public void setPasteBase(Position pasteBase) {
        this.pasteBase = pasteBase;
    }

    public Clipboard getPasteClipboard() {
        return pasteClipboard;
    }

    public void setPasteClipboard(Clipboard pasteClipboard) {
        this.pasteClipboard = pasteClipboard;
    }

    public int getAdjustActionIndex() {
        return adjustActionIndex;
    }

    public void setAdjustActionIndex(int adjustActionIndex) {
        this.adjustActionIndex = adjustActionIndex;
    }

    public int getAdjustDistance() {
        return adjustDistance;
    }

    public void setAdjustDistance(int adjustDistance) {
        this.adjustDistance = adjustDistance;
    }

    public boolean isPasteAir() {
        return pasteAir;
    }

    public void setPasteAir(boolean pasteAir) {
        this.pasteAir = pasteAir;
    }

    public int getSelectedPropertyIndex(String blockId) {
        return selectedProperties.getOrDefault(blockId, 0);
    }

    public void setSelectedPropertyIndex(String blockId, int index) {
        selectedProperties.put(blockId, index);
    }
}

package org.gugunet.miniworldedit;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;

public class BlockSetTask implements Runnable {
    
    public static class PendingChange {
        public final int x, y, z;
        public final Block newBlock;
        public final CompoundTag tileNbt;

        public PendingChange(int x, int y, int z, Block newBlock) {
            this(x, y, z, newBlock, null);
        }

        public PendingChange(int x, int y, int z, Block newBlock, CompoundTag tileNbt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.newBlock = newBlock;
            this.tileNbt = tileNbt;
        }
    }

    private final Player player;
    private final Level level;
    private final List<PendingChange> pendingChanges;
    private final List<BlockChange> completedChanges = new ArrayList<>();
    private final int batchSize;
    private final boolean isUndoOrRedo;
    private final boolean isPreview;
    private final String taskName;
    
    private int currentIndex = 0;
    private TaskHandler handler;

    public BlockSetTask(Player player, Level level, List<PendingChange> pendingChanges, int batchSize, boolean isUndoOrRedo, String taskName) {
        this(player, level, pendingChanges, batchSize, isUndoOrRedo, false, taskName);
    }

    public BlockSetTask(Player player, Level level, List<PendingChange> pendingChanges, int batchSize, boolean isUndoOrRedo, boolean isPreview, String taskName) {
        this.player = player;
        this.level = level;
        this.pendingChanges = pendingChanges;
        this.batchSize = batchSize;
        this.isUndoOrRedo = isUndoOrRedo;
        this.isPreview = isPreview;
        this.taskName = taskName;
    }

    public void setHandler(TaskHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancelTask();
            return;
        }

        int limit = Math.min(currentIndex + batchSize, pendingChanges.size());
        for (int i = currentIndex; i < limit; i++) {
            PendingChange change = pendingChanges.get(i);
            
            // Capture before block state (cloned)
            Block before = level.getBlock(change.x, change.y, change.z).clone();
            
            // Close existing block entity if present at target
            BlockEntity existing = level.getBlockEntity(new cn.nukkit.math.Vector3(change.x, change.y, change.z));
            if (existing != null) {
                existing.close();
            }
            
            // Apply new block state
            level.setBlock(change.x, change.y, change.z, change.newBlock, true, true);
            
            // Restore block entity if present
            if (change.tileNbt != null) {
                CompoundTag newNbt = change.tileNbt.copy();
                newNbt.putInt("x", change.x);
                newNbt.putInt("y", change.y);
                newNbt.putInt("z", change.z);
                
                try {
                    BlockEntity.createBlockEntity(
                        newNbt.getString("id"),
                        level.getChunk(change.x >> 4, change.z >> 4),
                        newNbt
                    );
                } catch (Exception e) {
                    // Ignore failures in creating corrupted block entities
                }
            }
            
            // Save to completed changes
            completedChanges.add(new BlockChange(change.x, change.y, change.z, level, before, change.newBlock));
        }

        currentIndex = limit;

        if (currentIndex >= pendingChanges.size()) {
            // Task completed!
            PlayerSession session = MiniWorldEdit.getInstance().getSession(player);
            if (isPreview) {
                // Save directly to preview changes in player's session
                session.setLastPreviewChanges(completedChanges);
            } else if (!isUndoOrRedo) {
                // Save to undo history
                session.getHistory().addChange(completedChanges);
            }
            
            if (isPreview) {
                player.sendMessage(TextFormat.GREEN + "Preview do paste aplicado! (" + completedChanges.size() + " blocos).");
                player.sendMessage(TextFormat.AQUA + "Ajuste com o " + TextFormat.YELLOW + "//adjuststick" + TextFormat.AQUA + " ou digite " + TextFormat.YELLOW + "//confirm" + TextFormat.AQUA + " / " + TextFormat.YELLOW + "//cancel" + TextFormat.AQUA + ".");
            } else {
                player.sendMessage(TextFormat.GREEN + "Operação \"" + taskName + "\" concluída! " + completedChanges.size() + " blocos alterados.");
            }
            cancelTask();
        }
    }

    private void cancelTask() {
        if (handler != null) {
            handler.cancel();
        }
        PlayerSession session = MiniWorldEdit.getInstance().getSession(player);
        if (session.getCurrentTask() == handler) {
            session.setCurrentTask(null);
        }
    }
}

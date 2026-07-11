package org.gugunet.miniworldedit;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;

public class BlockChange {
    private final int x, y, z;
    private final Level level;
    private final Block beforeBlock;
    private final Block afterBlock;

    public BlockChange(int x, int y, int z, Level level, Block beforeBlock, Block afterBlock) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
        this.beforeBlock = beforeBlock;
        this.afterBlock = afterBlock;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Level getLevel() {
        return level;
    }

    public Block getBeforeBlock() {
        return beforeBlock;
    }

    public Block getAfterBlock() {
        return afterBlock;
    }
}

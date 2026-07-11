package org.gugunet.miniworldedit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockState;
import cn.nukkit.block.property.type.BlockPropertyType;
import cn.nukkit.nbt.tag.CompoundTag;

public class Clipboard {
    private final Block[][][] blocks;
    private final CompoundTag[][][] tiles;
    private final int width;  // X
    private final int height; // Y
    private final int length; // Z
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    public Clipboard(Block[][][] blocks, CompoundTag[][][] tiles, int offsetX, int offsetY, int offsetZ) {
        this.blocks = blocks;
        this.tiles = tiles;
        this.width = blocks.length;
        this.height = blocks[0].length;
        this.length = blocks[0][0].length;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public Block[][][] getBlocks() {
        return blocks;
    }

    public CompoundTag[][][] getTiles() {
        return tiles;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public Clipboard rotate90() {
        int newWidth = length;
        int newHeight = height;
        int newLength = width;
        Block[][][] rotatedBlocks = new Block[newWidth][newHeight][newLength];
        CompoundTag[][][] rotatedTiles = new CompoundTag[newWidth][newHeight][newLength];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int nx = z;
                    int ny = y;
                    int nz = width - 1 - x;
                    Block original = blocks[x][y][z];
                    Block rotated = rotateBlockState(original.clone());
                    rotatedBlocks[nx][ny][nz] = rotated;
                    rotatedTiles[nx][ny][nz] = tiles[x][y][z];
                }
            }
        }

        int newOffsetX = offsetZ;
        int newOffsetZ = -offsetX - (width - 1);

        return new Clipboard(rotatedBlocks, rotatedTiles, newOffsetX, offsetY, newOffsetZ);
    }

    private Block rotateBlockState(Block block) {
        String id = block.getId();
        BlockState state = block.getBlockState();
        var properties = block.getProperties();
        boolean changed = false;

        for (var prop : block.getPropertyValues()) {
            String name = prop.getPropertyType().getName();

            if (name.equals("facing_direction")) {
                int val = ((Number) prop.getValue()).intValue();
                int rotated = switch (val) {
                    case 2 -> 5;
                    case 3 -> 4;
                    case 4 -> 2;
                    case 5 -> 3;
                    default -> val;
                };
                if (rotated != val) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            } else if (name.equals("weirdo_direction")) {
                int val = ((Number) prop.getValue()).intValue();
                int rotated = (val + 1) & 3;
                if (rotated != val) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            } else if (name.equals("direction")) {
                int val = ((Number) prop.getValue()).intValue();
                int rotated = (val + 1) & 3;
                if (rotated != val) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            } else if (name.equals("ground_sign_direction")) {
                int val = ((Number) prop.getValue()).intValue();
                int rotated = (val + 4) % 16;
                if (rotated != val) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            } else if (name.equals("rotation")) {
                int val = ((Number) prop.getValue()).intValue();
                int max = prop.getPropertyType().getValidValues().size();
                int rotated = (val + (max / 4)) % max;
                if (rotated != val) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            } else if (name.equals("pillar_axis")) {
                String val = String.valueOf(prop.getValue());
                String rotated = switch (val) {
                    case "x" -> "z";
                    case "z" -> "x";
                    default -> val;
                };
                if (!rotated.equals(val)) {
                    var newProp = prop.getPropertyType().tryCreateValue(rotated);
                    state = state.setPropertyValue(properties, newProp);
                    changed = true;
                }
            }
        }

        return changed ? Block.get(state) : block;
    }
}

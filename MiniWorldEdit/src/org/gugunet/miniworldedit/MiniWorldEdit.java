package org.gugunet.miniworldedit;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiniWorldEdit extends PluginBase implements Listener {

    private final Map<UUID, PlayerSelection> selections = new HashMap<>();

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getLogger().info("MiniWorldEdit ativado com sucesso!");
    }

    public PlayerSelection getSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), k -> new PlayerSelection());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Level level = this.getServer().getDefaultLevel();
        if (level != null) {
            // Teleporta o jogador sempre para o spawn do mundo principal ao entrar
            player.teleport(level.getSpawnLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Item item = player.getInventory().getItemInHand();

        if (item != null) {
            boolean hasNbt = item.getNamedTag() != null && item.getNamedTag().contains("edittool") && item.getNamedTag().getBoolean("edittool");
            boolean hasName = item.getCustomName().equals("§6Machado de Edição");

            if (hasNbt || hasName) {
                event.setCancelled(true);

                Block block = event.getBlock();
                Position pos = new Position(block.getX(), block.getY(), block.getZ(), block.getLevel());
                PlayerSelection selection = getSelection(player);

                if (!player.isSneaking()) {
                    selection.setPos1(pos);
                    player.sendMessage(TextFormat.GREEN + "Posição 1 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ")");
                } else {
                    selection.setPos2(pos);
                    player.sendMessage(TextFormat.GREEN + "Posição 2 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ")");
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.isOp()) {
            player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("/edittool")) {
            Item tool = Item.get(Item.WOODEN_AXE);
            tool.setCustomName("§6Machado de Edição");
            
            CompoundTag tag = tool.getNamedTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.putBoolean("edittool", true);
            tool.setNamedTag(tag);

            player.getInventory().addItem(tool);
            player.sendMessage(TextFormat.GREEN + "Você recebeu o Machado de Edição (quebre blocos para definir posições; agache para pos 2)!");
            return true;
        }

        if (cmd.equals("/set")) {
            if (args.length < 1) {
                player.sendMessage(TextFormat.RED + "Uso correto: //set <nome_do_bloco>");
                return true;
            }

            PlayerSelection selection = getSelection(player);
            if (selection.getPos1() == null || selection.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Selecione as duas posições primeiro usando o Machado de Edição.");
                return true;
            }

            Position pos1 = selection.getPos1();
            Position pos2 = selection.getPos2();

            if (pos1.getLevel() != pos2.getLevel()) {
                player.sendMessage(TextFormat.RED + "As posições devem estar no mesmo mundo.");
                return true;
            }

            String blockName = args[0];
            if (!blockName.contains(":")) {
                blockName = "minecraft:" + blockName;
            }

            Block block;
            try {
                block = Block.get(blockName);
            } catch (Exception e) {
                player.sendMessage(TextFormat.RED + "Bloco inválido: " + args[0]);
                return true;
            }

            if (block == null) {
                player.sendMessage(TextFormat.RED + "Bloco inválido: " + args[0]);
                return true;
            }

            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            Level level = pos1.getLevel();
            int count = 0;

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        level.setBlock(x, y, z, block, true, true);
                        count++;
                    }
                }
            }

            player.sendMessage(TextFormat.GREEN + "Área preenchida com " + count + " blocos de " + args[0] + ".");
            return true;
        }

        return false;
    }
}

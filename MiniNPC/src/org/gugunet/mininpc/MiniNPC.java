package org.gugunet.mininpc;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.EntityFlag;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.SerializedImage;
import cn.nukkit.utils.TextFormat;

import cn.nukkit.registry.Registries;

import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class MiniNPC extends PluginBase {

    private static MiniNPC instance;
    private final Map<UUID, String> selectedNpcs = new HashMap<>();
    private final Map<String, NPCHuman> spawnedNpcs = new HashMap<>();
    private Config npcsConfig;

    public static MiniNPC getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Register the entity class using the PNX Registries system
        try {
            Registries.ENTITY.registerCustomEntity(this, NPCHuman.class);
        } catch (Exception e) {
            this.getLogger().error("Erro ao registrar a entidade NPCHuman: ", e);
        }

        // Load configuration
        this.saveDefaultConfig();
        npcsConfig = new Config(new File(this.getDataFolder(), "npcs.yml"), Config.YAML);

        this.getLogger().info("MiniNPC ativado!");

        // Spawn existing NPCs after a delay to ensure all worlds are loaded
        this.getServer().getScheduler().scheduleDelayedTask(this, this::spawnAllNpcs, 25);
    }

    @Override
    public void onDisable() {
        despawnAllNpcs();
    }

    private void spawnAllNpcs() {
        despawnAllNpcs();
        
        Set<String> keys = npcsConfig.getKeys(false);
        if (keys == null || keys.isEmpty()) return;

        this.getLogger().info("Spawnando " + keys.size() + " NPCs...");
        for (String id : keys) {
            spawnNpc(id);
        }
    }

    private void despawnAllNpcs() {
        for (NPCHuman npc : spawnedNpcs.values()) {
            if (npc != null && !npc.isClosed()) {
                npc.close();
            }
        }
        spawnedNpcs.clear();
    }

    public void spawnNpc(String id) {
        if (!npcsConfig.exists(id)) return;

        // Despawn existing if any
        NPCHuman oldNpc = spawnedNpcs.get(id);
        if (oldNpc != null && !oldNpc.isClosed()) {
            oldNpc.close();
        }

        String worldName = npcsConfig.getString(id + ".world");
        double x = npcsConfig.getDouble(id + ".x");
        double y = npcsConfig.getDouble(id + ".y");
        double z = npcsConfig.getDouble(id + ".z");
        double yaw = npcsConfig.getDouble(id + ".yaw");
        double pitch = npcsConfig.getDouble(id + ".pitch");
        String displayName = npcsConfig.getString(id + ".displayName", id);
        String pose = npcsConfig.getString(id + ".pose", "standing");

        Level level = this.getServer().getLevelByName(worldName);
        if (level == null) {
            this.getServer().loadLevel(worldName);
            level = this.getServer().getLevelByName(worldName);
        }
        if (level == null) {
            this.getLogger().warning("Mundo '" + worldName + "' nao encontrado para o NPC " + id);
            return;
        }

        Location loc = new Location(x, y, z, yaw, pitch, level);
        CompoundTag nbt = Entity.getDefaultNBT(loc, new Vector3(0, 0, 0), (float) yaw, (float) pitch);

        NPCHuman npc = new NPCHuman(loc.getChunk(), nbt);
        npc.setNpcId(id);
        
        // Load Skin
        Skin skin = loadNpcSkin(id);
        if (skin != null) {
            npc.setSkin(skin);
        }

        // Set name tag
        npc.setNameTag(translateColors(displayName));
        npc.setNameTagVisible(true);
        npc.setNameTagAlwaysVisible(true);
        npc.setImmobile(true);

        // Apply Pose
        applyPose(npc, pose);

        // Spawn to world
        npc.spawnToAll();
        spawnedNpcs.put(id, npc);
    }

    private void applyPose(NPCHuman npc, String pose) {
        npc.setDataFlag(EntityFlag.SNEAKING, false);
        npc.setDataFlag(EntityFlag.SITTING, false);
        npc.setDataFlag(EntityFlag.RIDING, false);
        npc.setDataFlag(EntityFlag.SLEEPING, false);
        npc.setDataFlag(EntityFlag.LAYING_DOWN, false);
        npc.setDataFlag(EntityFlag.CRAWLING, false);
        npc.setDataFlag(EntityFlag.SWIMMING, false);
        npc.setDataFlag(EntityFlag.GLIDING, false);

        if (pose.equalsIgnoreCase("sentado") || pose.equalsIgnoreCase("sitting")) {
            npc.setDataFlag(EntityFlag.SITTING, true);
            npc.setDataFlag(EntityFlag.RIDING, true);
        } else if (pose.equalsIgnoreCase("agachado") || pose.equalsIgnoreCase("crouching") || pose.equalsIgnoreCase("sneaking")) {
            npc.setDataFlag(EntityFlag.SNEAKING, true);
        } else if (pose.equalsIgnoreCase("deitado") || pose.equalsIgnoreCase("sleeping") || pose.equalsIgnoreCase("laying_down")) {
            npc.setDataFlag(EntityFlag.SLEEPING, true);
            npc.setDataFlag(EntityFlag.LAYING_DOWN, true);
        } else if (pose.equalsIgnoreCase("rastejando") || pose.equalsIgnoreCase("crawling")) {
            npc.setDataFlag(EntityFlag.CRAWLING, true);
        } else if (pose.equalsIgnoreCase("nadando") || pose.equalsIgnoreCase("swimming")) {
            npc.setDataFlag(EntityFlag.SWIMMING, true);
        } else if (pose.equalsIgnoreCase("planando") || pose.equalsIgnoreCase("gliding")) {
            npc.setDataFlag(EntityFlag.GLIDING, true);
        }
    }

    public void saveNpcSkin(String id, Skin skin) {
        File skinsFolder = new File(getDataFolder(), "skins");
        if (!skinsFolder.exists()) skinsFolder.mkdirs();
        Config cfg = new Config(new File(skinsFolder, id + ".json"), Config.JSON);

        cfg.set("skinId", skin.getSkinId());
        if (skin.getSkinData() != null) {
            cfg.set("skinDataWidth", skin.getSkinData().width);
            cfg.set("skinDataHeight", skin.getSkinData().height);
            cfg.set("skinDataBytes", Base64.getEncoder().encodeToString(skin.getSkinData().data));
        }
        cfg.set("geometryData", skin.getGeometryData());
        cfg.set("skinResourcePatch", skin.getSkinResourcePatch());
        cfg.set("capeId", skin.getCapeId());
        if (skin.getCapeData() != null && skin.getCapeData().data != null) {
            cfg.set("capeDataWidth", skin.getCapeData().width);
            cfg.set("capeDataHeight", skin.getCapeData().height);
            cfg.set("capeDataBytes", Base64.getEncoder().encodeToString(skin.getCapeData().data));
        }
        cfg.save();
    }

    public Skin loadNpcSkin(String id) {
        File file = new File(getDataFolder(), "skins/" + id + ".json");
        if (!file.exists()) return null;
        Config cfg = new Config(file, Config.JSON);

        Skin skin = new Skin();
        skin.setSkinId(cfg.getString("skinId", ""));

        if (cfg.exists("skinDataBytes")) {
            int width = cfg.getInt("skinDataWidth", 64);
            int height = cfg.getInt("skinDataHeight", 64);
            byte[] data = Base64.getDecoder().decode(cfg.getString("skinDataBytes", ""));
            skin.setSkinData(new SerializedImage(width, height, data));
        }

        skin.setGeometryData(cfg.getString("geometryData", ""));
        skin.setSkinResourcePatch(cfg.getString("skinResourcePatch", ""));
        skin.setCapeId(cfg.getString("capeId", ""));

        if (cfg.exists("capeDataBytes") && !cfg.getString("capeDataBytes").isEmpty()) {
            int width = cfg.getInt("capeDataWidth", 0);
            int height = cfg.getInt("capeDataHeight", 0);
            byte[] data = Base64.getDecoder().decode(cfg.getString("capeDataBytes"));
            skin.setCapeData(new SerializedImage(width, height, data));
        }

        return skin;
    }

    public void handleNpcRightClick(Player player, NPCHuman npc) {
        String id = npc.getNpcId();
        if (id == null) return;

        // Auto-select for OP players in Creative
        if (player.isOp() && player.getGamemode() == Player.CREATIVE) {
            selectedNpcs.put(player.getUniqueId(), id);
            player.sendMessage(TextFormat.GREEN + "[MiniNPC] NPC '" + id + "' selecionado.");
        }

        String command = npcsConfig.getString(id + ".rightClickCommand", "");
        if (!command.isEmpty()) {
            executeCommand(player, command);
        }
    }

    public void handleNpcLeftClick(Player player, NPCHuman npc) {
        String id = npc.getNpcId();
        if (id == null) return;

        // Auto-select for OP players in Creative
        if (player.isOp() && player.getGamemode() == Player.CREATIVE) {
            selectedNpcs.put(player.getUniqueId(), id);
            player.sendMessage(TextFormat.GREEN + "[MiniNPC] NPC '" + id + "' selecionado.");
        }

        String command = npcsConfig.getString(id + ".leftClickCommand", "");
        if (!command.isEmpty()) {
            executeCommand(player, command);
        }
    }

    private void executeCommand(Player player, String command) {
        String cmd = command;
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        cmd = cmd.replace("{player}", player.getName());

        if (cmd.startsWith("console:")) {
            String consoleCmd = cmd.substring("console:".length()).trim();
            getServer().executeCommand(getServer().getConsoleSender(), consoleCmd);
        } else {
            getServer().executeCommand(player, cmd);
        }
    }

    private String translateColors(String message) {
        return TextFormat.colorize('&', message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();
        if (cmdName.startsWith("/")) {
            cmdName = cmdName.substring(1);
        }

        if (!cmdName.equals("npc")) {
            return false;
        }

        if (!sender.isOp()) {
            sender.sendMessage(TextFormat.RED + "Você não tem permissão para gerenciar NPCs.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(TextFormat.RED + "Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create": {
                if (args.length < 2) {
                    player.sendMessage(TextFormat.RED + "Uso: /npc create <id>");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (npcsConfig.exists(id)) {
                    player.sendMessage(TextFormat.RED + "Já existe um NPC com o ID '" + id + "'.");
                    return true;
                }

                Location loc = player.getLocation();
                npcsConfig.set(id + ".world", loc.getLevel().getFolderName());
                npcsConfig.set(id + ".x", loc.getX());
                npcsConfig.set(id + ".y", loc.getY());
                npcsConfig.set(id + ".z", loc.getZ());
                npcsConfig.set(id + ".yaw", loc.getYaw());
                npcsConfig.set(id + ".pitch", loc.getPitch());
                npcsConfig.set(id + ".displayName", id);
                npcsConfig.set(id + ".pose", "standing");
                npcsConfig.set(id + ".rightClickCommand", "");
                npcsConfig.set(id + ".leftClickCommand", "");
                npcsConfig.save();

                // Save skin (Default to Steve skin, fallback to player skin)
                Skin defaultSkin = getSteveSkin();
                if (defaultSkin != null) {
                    saveNpcSkin(id, defaultSkin);
                } else {
                    saveNpcSkin(id, player.getSkin());
                }

                spawnNpc(id);
                selectedNpcs.put(player.getUniqueId(), id);

                player.sendMessage(TextFormat.GREEN + "NPC '" + id + "' criado e selecionado com sucesso!");
                return true;
            }

            case "select": {
                if (args.length < 2) {
                    player.sendMessage(TextFormat.RED + "Uso: /npc select <id>");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (!npcsConfig.exists(id)) {
                    player.sendMessage(TextFormat.RED + "NPC '" + id + "' não existe.");
                    return true;
                }
                selectedNpcs.put(player.getUniqueId(), id);
                player.sendMessage(TextFormat.GREEN + "NPC '" + id + "' selecionado.");
                return true;
            }

            default: {
                // All other subcommands require a selected NPC
                String id = selectedNpcs.get(player.getUniqueId());
                if (id == null || !npcsConfig.exists(id)) {
                    player.sendMessage(TextFormat.RED + "Nenhum NPC selecionado! Use /npc select <id> primeiro.");
                    return true;
                }

                NPCHuman npcEntity = spawnedNpcs.get(id);

                switch (sub) {
                    case "skin": {
                        if (args.length < 2) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc skin <jogador>");
                            return true;
                        }
                        String targetPlayer = args[1];
                        Skin targetSkin = loadPlayerSkin(targetPlayer);
                        if (targetSkin == null) {
                            player.sendMessage(TextFormat.RED + "Não foi possível encontrar a skin do jogador '" + targetPlayer + "'.");
                            return true;
                        }

                        saveNpcSkin(id, targetSkin);
                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "Skin do NPC '" + id + "' atualizada para a de '" + targetPlayer + "'!");
                        return true;
                    }

                    case "move": {
                        Location loc = player.getLocation();
                        npcsConfig.set(id + ".world", loc.getLevel().getFolderName());
                        npcsConfig.set(id + ".x", loc.getX());
                        npcsConfig.set(id + ".y", loc.getY());
                        npcsConfig.set(id + ".z", loc.getZ());
                        npcsConfig.set(id + ".yaw", loc.getYaw());
                        npcsConfig.set(id + ".pitch", loc.getPitch());
                        npcsConfig.save();

                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "NPC '" + id + "' movido para sua posição!");
                        return true;
                    }

                    case "center": {
                        if (npcEntity == null || npcEntity.isClosed()) {
                            player.sendMessage(TextFormat.RED + "A entidade do NPC não está spawnada.");
                            return true;
                        }
                        double cx = Math.floor(npcEntity.getX()) + 0.5;
                        double cz = Math.floor(npcEntity.getZ()) + 0.5;
                        double cy = npcEntity.getY();

                        npcsConfig.set(id + ".x", cx);
                        npcsConfig.set(id + ".y", cy);
                        npcsConfig.set(id + ".z", cz);
                        npcsConfig.save();

                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "NPC '" + id + "' centralizado no bloco!");
                        return true;
                    }

                    case "rot": {
                        if (args.length < 3) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc rot <yaw> <pitch>");
                            return true;
                        }
                        double yaw;
                        double pitch;
                        try {
                            yaw = Double.parseDouble(args[1]);
                            pitch = Double.parseDouble(args[2]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(TextFormat.RED + "Valores de yaw e pitch inválidos.");
                            return true;
                        }

                        npcsConfig.set(id + ".yaw", yaw);
                        npcsConfig.set(id + ".pitch", pitch);
                        npcsConfig.save();

                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "Rotação do NPC '" + id + "' atualizada!");
                        return true;
                    }

                    case "display": {
                        if (args.length < 2) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc display <display name...>");
                            return true;
                        }
                        StringBuilder sbName = new StringBuilder();
                        for (int i = 1; i < args.length; i++) {
                            sbName.append(args[i]).append(" ");
                        }
                        String dispName = sbName.toString().trim();

                        npcsConfig.set(id + ".displayName", dispName);
                        npcsConfig.save();

                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "Display name do NPC '" + id + "' alterado para: " + translateColors(dispName));
                        return true;
                    }

                    case "delete": {
                        if (npcEntity != null && !npcEntity.isClosed()) {
                            npcEntity.close();
                        }
                        spawnedNpcs.remove(id);
                        selectedNpcs.remove(player.getUniqueId());

                        npcsConfig.remove(id);
                        npcsConfig.save();

                        // Clean skin file
                        File skinFile = new File(getDataFolder(), "skins/" + id + ".json");
                        if (skinFile.exists()) {
                            skinFile.delete();
                        }

                        player.sendMessage(TextFormat.GREEN + "NPC '" + id + "' deletado com sucesso!");
                        return true;
                    }

                    case "pose": {
                        if (args.length < 2) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc pose <sentado|agachado|deitado|normal|etc...>");
                            return true;
                        }
                        String pose = args[1].toLowerCase();
                        npcsConfig.set(id + ".pose", pose);
                        npcsConfig.save();

                        spawnNpc(id);
                        player.sendMessage(TextFormat.GREEN + "Pose do NPC '" + id + "' alterada para: " + pose);
                        return true;
                    }

                    case "rightclick": {
                        if (args.length < 2) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc rightclick <comando>");
                            player.sendMessage(TextFormat.GRAY + "Dica: use 'console:comando' para rodar como console. Use '{player}' para o nome do jogador.");
                            return true;
                        }
                        StringBuilder sbCmd = new StringBuilder();
                        for (int i = 1; i < args.length; i++) {
                            sbCmd.append(args[i]).append(" ");
                        }
                        String clickCmd = sbCmd.toString().trim();

                        npcsConfig.set(id + ".rightClickCommand", clickCmd);
                        npcsConfig.save();

                        player.sendMessage(TextFormat.GREEN + "Comando de botão direito definido!");
                        return true;
                    }

                    case "leftclick": {
                        if (args.length < 2) {
                            player.sendMessage(TextFormat.RED + "Uso: /npc leftclick <comando>");
                            player.sendMessage(TextFormat.GRAY + "Dica: use 'console:comando' para rodar como console. Use '{player}' para o nome do jogador.");
                            return true;
                        }
                        StringBuilder sbCmd = new StringBuilder();
                        for (int i = 1; i < args.length; i++) {
                            sbCmd.append(args[i]).append(" ");
                        }
                        String clickCmd = sbCmd.toString().trim();

                        npcsConfig.set(id + ".leftClickCommand", clickCmd);
                        npcsConfig.save();

                        player.sendMessage(TextFormat.GREEN + "Comando de botão esquerdo definido!");
                        return true;
                    }

                    default:
                        sendHelp(player);
                        return true;
                }
            }
        }
    }

    public Skin getSteveSkin() {
        try {
            File steveFile = new File("/home/olavo/Downloads/gugunet/texture/ResourcePack_v26.30.0/textures/entity/steve.png");
            if (!steveFile.exists()) {
                return null;
            }
            BufferedImage img = ImageIO.read(steveFile);
            int width = img.getWidth();
            int height = img.getHeight();
            byte[] skinData = new byte[width * height * 4];
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = img.getRGB(x, y);
                    skinData[index++] = (byte) ((argb >> 16) & 0xFF); // R
                    skinData[index++] = (byte) ((argb >> 8) & 0xFF);  // G
                    skinData[index++] = (byte) (argb & 0xFF);         // B
                    skinData[index++] = (byte) ((argb >> 24) & 0xFF); // A
                }
            }
            Skin skin = new Skin();
            skin.setSkinId("Standard_Custom");
            skin.setSkinData(new SerializedImage(width, height, skinData));
            skin.setGeometryName("geometry.humanoid.custom");
            skin.setGeometryData("");
            return skin;
        } catch (Exception e) {
            getServer().getLogger().error("Erro ao carregar skin do Steve: ", e);
        }
        return null;
    }

    public Skin loadPlayerSkin(String playerName) {
        // Try online player first
        Player target = getServer().getPlayer(playerName);
        if (target != null && target.getSkin() != null) {
            return target.getSkin();
        }

        // Try YML file
        File ymlFile = new File(getDataFolder(), "skins/" + playerName + ".yml");
        if (ymlFile.exists()) {
            return loadSkinFromFile(ymlFile, Config.YAML);
        }

        // Try JSON file
        File jsonFile = new File(getDataFolder(), "skins/" + playerName + ".json");
        if (jsonFile.exists()) {
            return loadSkinFromFile(jsonFile, Config.JSON);
        }

        // Try lowercase name YML/JSON
        File ymlFileLower = new File(getDataFolder(), "skins/" + playerName.toLowerCase() + ".yml");
        if (ymlFileLower.exists()) {
            return loadSkinFromFile(ymlFileLower, Config.YAML);
        }
        File jsonFileLower = new File(getDataFolder(), "skins/" + playerName.toLowerCase() + ".json");
        if (jsonFileLower.exists()) {
            return loadSkinFromFile(jsonFileLower, Config.JSON);
        }

        return null;
    }

    private Skin loadSkinFromFile(File file, int type) {
        Config cfg = new Config(file, type);
        Skin skin = new Skin();
        skin.setSkinId(cfg.getString("skinId", ""));

        if (cfg.exists("skinDataBytes")) {
            int width = cfg.getInt("skinDataWidth", 64);
            int height = cfg.getInt("skinDataHeight", 64);
            byte[] data = Base64.getDecoder().decode(cfg.getString("skinDataBytes", ""));
            skin.setSkinData(new SerializedImage(width, height, data));
        } else if (cfg.exists("skinData")) {
            String rawData = cfg.getString("skinData", "");
            byte[] data = Base64.getDecoder().decode(rawData);
            int width = 64;
            int height = 64;
            if (data.length == 8192) {
                width = 64;
                height = 32;
            } else if (data.length == 16384) {
                width = 64;
                height = 64;
            } else if (data.length == 65536) {
                width = 128;
                height = 128;
            }
            skin.setSkinData(new SerializedImage(width, height, data));
        }

        skin.setGeometryData(cfg.getString("geometryData", ""));
        skin.setSkinResourcePatch(cfg.getString("skinResourcePatch", ""));
        skin.setCapeId(cfg.getString("capeId", ""));

        if (cfg.exists("capeDataBytes") && !cfg.getString("capeDataBytes").isEmpty()) {
            int width = cfg.getInt("capeDataWidth", 0);
            int height = cfg.getInt("capeDataHeight", 0);
            byte[] data = Base64.getDecoder().decode(cfg.getString("capeDataBytes"));
            skin.setCapeData(new SerializedImage(width, height, data));
        } else if (cfg.exists("capeData") && !cfg.getString("capeData").isEmpty()) {
            String rawData = cfg.getString("capeData", "");
            byte[] data = Base64.getDecoder().decode(rawData);
            int width = 64;
            int height = 32;
            skin.setCapeData(new SerializedImage(width, height, data));
        }

        return skin;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§e§l--- COMANDOS DE NPC ---");
        player.sendMessage("§b/npc create <id> §7- Cria um NPC com o seu skin e ID");
        player.sendMessage("§b/npc select <id> §7- Seleciona um NPC para edição");
        player.sendMessage("§b/npc move §7- Move o NPC selecionado para sua posição");
        player.sendMessage("§b/npc center §7- Centraliza o NPC selecionado no bloco");
        player.sendMessage("§b/npc rot <yaw> <pitch> §7- Define a rotação do NPC selecionado");
        player.sendMessage("§b/npc display <nome...> §7- Define o display name do NPC selecionado (suporta cores)");
        player.sendMessage("§b/npc skin <jogador> §7- Define a skin do NPC para a de um jogador");
        player.sendMessage("§b/npc pose <pose> §7- Define a pose do NPC (sentado, agachado, deitado, normal, etc)");
        player.sendMessage("§b/npc rightclick <comando> §7- Executa comando ao clicar com botão direito");
        player.sendMessage("§b/npc leftclick <comando> §7- Executa comando ao bater");
        player.sendMessage("§b/npc delete §7- Deleta o NPC selecionado");
        String selected = selectedNpcs.get(player.getUniqueId());
        player.sendMessage("§aNPC Selecionado atualmente: §e" + (selected != null ? selected : "Nenhum"));
    }
}

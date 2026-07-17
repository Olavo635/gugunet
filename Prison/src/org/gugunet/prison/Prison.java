package org.gugunet.prison;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.player.PlayerFoodLevelChangeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.form.window.CustomForm;
import cn.nukkit.form.window.SimpleForm;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class Prison extends PluginBase implements Listener {

    // ======================================================
    //  INNER CLASS
    // ======================================================
    private static class PrisonerData {
        long releaseAt;
        boolean perpetual;
        boolean active;
        boolean canMine;

        PrisonerData(long releaseAt, boolean perpetual, boolean canMine) {
            this.releaseAt = releaseAt;
            this.perpetual = perpetual;
            this.active = true;
            this.canMine = canMine;
        }
    }

    // ======================================================
    //  CONSTANTS
    // ======================================================
    private static final String[] ORE_TYPES = {
        "minecraft:coal_ore",
        "minecraft:copper_ore",
        "minecraft:gold_ore",
        "minecraft:iron_ore",
        "minecraft:diamond_ore",
        "minecraft:emerald_ore"
    };
    private static final int[] ORE_WEIGHTS = {35, 25, 18, 13, 6, 3};

    private static final Map<String, Integer> REGEN_TIMES = new HashMap<>();
    private static final Map<String, Long> SENTENCE_REDUCTION = new HashMap<>();

    static {
        REGEN_TIMES.put("minecraft:coal_ore", 30 * 20);
        REGEN_TIMES.put("minecraft:copper_ore", 60 * 20);
        REGEN_TIMES.put("minecraft:gold_ore", 120 * 20);
        REGEN_TIMES.put("minecraft:iron_ore", 180 * 20);
        REGEN_TIMES.put("minecraft:diamond_ore", 600 * 20);
        REGEN_TIMES.put("minecraft:emerald_ore", 1800 * 20);

        SENTENCE_REDUCTION.put("minecraft:coal_ore", 5000L);
        SENTENCE_REDUCTION.put("minecraft:copper_ore", 10000L);
        SENTENCE_REDUCTION.put("minecraft:gold_ore", 15000L);
        SENTENCE_REDUCTION.put("minecraft:iron_ore", 30000L);
        SENTENCE_REDUCTION.put("minecraft:diamond_ore", 60000L);
        SENTENCE_REDUCTION.put("minecraft:emerald_ore", 120000L);
    }

    private static final long INTERACT_COOLDOWN_MS = 500;

    // ======================================================
    //  STATE
    // ======================================================
    private static Prison instance;
    private final Map<UUID, PrisonerData> prisoners = new HashMap<>();
    private final Set<UUID> visitors = new HashSet<>();
    private final Set<UUID> inMiningArea = new HashSet<>();

    private Location prisonSpawn;
    private Location visitorSpawn;
    private Vector3 prisonMin, prisonMax;
    private Vector3 visitorMin, visitorMax;
    private Vector3 miningMin, miningMax;

    private final Set<String> oreGenerators = new HashSet<>();
    private final Map<String, String> activeOres = new HashMap<>();
    private final Map<String, TaskHandler> regenTasks = new HashMap<>();

    private final Map<UUID, String> setupMode = new HashMap<>();
    private final Map<UUID, Vector3> setupPos1 = new HashMap<>();

    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private final Map<UUID, Long> lastBoundaryCheck = new HashMap<>();

    private TaskHandler sentenceCheckTask;
    private TaskHandler boundaryCheckTask;
    private TaskHandler autoSaveTask;

    // ======================================================
    //  LIFECYCLE
    // ======================================================
    public static Prison getInstance() { return instance; }

    @Override
    public void onLoad() { instance = this; }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadPrisonConfig();
        getServer().getScheduler().scheduleDelayedTask(this, this::initializeOreGenerators, 20);
        startSentenceCheckTask();
        startBoundaryCheckTask();
        startAutoSaveTask();
        getLogger().info("Prison ativado!");
    }

    @Override
    public void onDisable() {
        if (sentenceCheckTask != null) sentenceCheckTask.cancel();
        if (boundaryCheckTask != null) boundaryCheckTask.cancel();
        if (autoSaveTask != null) autoSaveTask.cancel();
        for (TaskHandler task : regenTasks.values()) task.cancel();
        regenTasks.clear();
        savePrisonConfig();
    }

    // ======================================================
    //  CONFIG LOAD / SAVE
    // ======================================================
    private void loadPrisonConfig() {
        Config cfg = getConfig();

        if (cfg.exists("prison-spawn.world")) {
            Level lvl = getServer().getLevelByName(cfg.getString("prison-spawn.world", "world"));
            if (lvl != null) prisonSpawn = new Location(
                cfg.getDouble("prison-spawn.x"), cfg.getDouble("prison-spawn.y"),
                cfg.getDouble("prison-spawn.z"), cfg.getDouble("prison-spawn.yaw"),
                cfg.getDouble("prison-spawn.pitch"), lvl);
        }

        if (cfg.exists("visitor-spawn.world")) {
            Level lvl = getServer().getLevelByName(cfg.getString("visitor-spawn.world", "world"));
            if (lvl != null) visitorSpawn = new Location(
                cfg.getDouble("visitor-spawn.x"), cfg.getDouble("visitor-spawn.y"),
                cfg.getDouble("visitor-spawn.z"), cfg.getDouble("visitor-spawn.yaw"),
                cfg.getDouble("visitor-spawn.pitch"), lvl);
        }

        if (cfg.exists("prison-limits.min.x")) {
            prisonMin = new Vector3(cfg.getDouble("prison-limits.min.x"), cfg.getDouble("prison-limits.min.y"), cfg.getDouble("prison-limits.min.z"));
            prisonMax = new Vector3(cfg.getDouble("prison-limits.max.x"), cfg.getDouble("prison-limits.max.y"), cfg.getDouble("prison-limits.max.z"));
        }

        if (cfg.exists("visitor-limits.min.x")) {
            visitorMin = new Vector3(cfg.getDouble("visitor-limits.min.x"), cfg.getDouble("visitor-limits.min.y"), cfg.getDouble("visitor-limits.min.z"));
            visitorMax = new Vector3(cfg.getDouble("visitor-limits.max.x"), cfg.getDouble("visitor-limits.max.y"), cfg.getDouble("visitor-limits.max.z"));
        }

        if (cfg.exists("mining-area.min.x")) {
            miningMin = new Vector3(cfg.getDouble("mining-area.min.x"), cfg.getDouble("mining-area.min.y"), cfg.getDouble("mining-area.min.z"));
            miningMax = new Vector3(cfg.getDouble("mining-area.max.x"), cfg.getDouble("mining-area.max.y"), cfg.getDouble("mining-area.max.z"));
        }

        oreGenerators.clear();
        List<Map<?, ?>> gens = cfg.getMapList("ore-generators");
        for (Map<?, ?> g : gens) {
            try {
                int gx = ((Number) g.get("x")).intValue();
                int gy = ((Number) g.get("y")).intValue();
                int gz = ((Number) g.get("z")).intValue();
                oreGenerators.add(gx + "," + gy + "," + gz);
            } catch (Exception ignored) {}
        }

        prisoners.clear();
        List<Map<?, ?>> prisList = cfg.getMapList("prisoners");
        for (Map<?, ?> p : prisList) {
            try {
                UUID uid = UUID.fromString(String.valueOf(p.get("uuid")));
                long releaseAt = ((Number) p.get("release-at")).longValue();
                boolean perpetual = Boolean.TRUE.equals(p.get("perpetual"));
                boolean canMine = !Boolean.FALSE.equals(p.get("can-mine"));
                PrisonerData data = new PrisonerData(releaseAt, perpetual, canMine);
                if (perpetual || System.currentTimeMillis() < releaseAt) {
                    prisoners.put(uid, data);
                }
            } catch (Exception ignored) {}
        }
    }

    private void savePrisonConfig() {
        Config cfg = getConfig();

        if (prisonSpawn != null) {
            cfg.set("prison-spawn.world", prisonSpawn.getLevel().getFolderName());
            cfg.set("prison-spawn.x", prisonSpawn.getX());
            cfg.set("prison-spawn.y", prisonSpawn.getY());
            cfg.set("prison-spawn.z", prisonSpawn.getZ());
            cfg.set("prison-spawn.yaw", prisonSpawn.getYaw());
            cfg.set("prison-spawn.pitch", prisonSpawn.getPitch());
        }

        if (visitorSpawn != null) {
            cfg.set("visitor-spawn.world", visitorSpawn.getLevel().getFolderName());
            cfg.set("visitor-spawn.x", visitorSpawn.getX());
            cfg.set("visitor-spawn.y", visitorSpawn.getY());
            cfg.set("visitor-spawn.z", visitorSpawn.getZ());
            cfg.set("visitor-spawn.yaw", visitorSpawn.getYaw());
            cfg.set("visitor-spawn.pitch", visitorSpawn.getPitch());
        }

        if (prisonMin != null && prisonMax != null) {
            cfg.set("prison-limits.min.x", prisonMin.x);
            cfg.set("prison-limits.min.y", prisonMin.y);
            cfg.set("prison-limits.min.z", prisonMin.z);
            cfg.set("prison-limits.max.x", prisonMax.x);
            cfg.set("prison-limits.max.y", prisonMax.y);
            cfg.set("prison-limits.max.z", prisonMax.z);
        }

        if (visitorMin != null && visitorMax != null) {
            cfg.set("visitor-limits.min.x", visitorMin.x);
            cfg.set("visitor-limits.min.y", visitorMin.y);
            cfg.set("visitor-limits.min.z", visitorMin.z);
            cfg.set("visitor-limits.max.x", visitorMax.x);
            cfg.set("visitor-limits.max.y", visitorMax.y);
            cfg.set("visitor-limits.max.z", visitorMax.z);
        }

        if (miningMin != null && miningMax != null) {
            cfg.set("mining-area.min.x", miningMin.x);
            cfg.set("mining-area.min.y", miningMin.y);
            cfg.set("mining-area.min.z", miningMin.z);
            cfg.set("mining-area.max.x", miningMax.x);
            cfg.set("mining-area.max.y", miningMax.y);
            cfg.set("mining-area.max.z", miningMax.z);
        }

        List<Map<String, Object>> genList = new ArrayList<>();
        for (String key : oreGenerators) {
            String[] parts = key.split(",");
            Map<String, Object> m = new HashMap<>();
            m.put("x", Integer.parseInt(parts[0]));
            m.put("y", Integer.parseInt(parts[1]));
            m.put("z", Integer.parseInt(parts[2]));
            genList.add(m);
        }
        cfg.set("ore-generators", genList);

        List<Map<String, Object>> prisList = new ArrayList<>();
        for (Map.Entry<UUID, PrisonerData> entry : prisoners.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("uuid", entry.getKey().toString());
            m.put("release-at", entry.getValue().releaseAt);
            m.put("perpetual", entry.getValue().perpetual);
            m.put("can-mine", entry.getValue().canMine);
            prisList.add(m);
        }
        cfg.set("prisoners", prisList);

        cfg.save();
    }

    // ======================================================
    //  COMMANDS
    // ======================================================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("prision")) return false;

        if (args.length == 0) {
            sender.sendMessage(TextFormat.YELLOW + "/prision <setspawn|setvisitorspawn|setlimits|visitorlimits|miningarea|hammer|visit|setores|list|release|status>");
            return true;
        }

        if (!(sender instanceof Player)) { sender.sendMessage("Apenas jogadores!"); return true; }
        Player p = (Player) sender;

        switch (args[0].toLowerCase()) {
            case "setspawn":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                prisonSpawn = p.getLocation();
                savePrisonConfig();
                p.sendMessage(TextFormat.GREEN + "Spawn da prisao definido!");
                break;

            case "setvisitorspawn":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                visitorSpawn = p.getLocation();
                savePrisonConfig();
                p.sendMessage(TextFormat.GREEN + "Spawn de visitantes definido!");
                break;

            case "setlimits":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                setupMode.put(p.getUniqueId(), "prisonlimits");
                setupPos1.remove(p.getUniqueId());
                p.sendMessage(TextFormat.AQUA + "Modo de configuracao: Limites da Prisao");
                p.sendMessage(TextFormat.GRAY + "Quebre um bloco = Pos1 | Interaja com bloco = Pos2");
                break;

            case "visitorlimits":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                setupMode.put(p.getUniqueId(), "visitorlimits");
                setupPos1.remove(p.getUniqueId());
                p.sendMessage(TextFormat.AQUA + "Modo de configuracao: Limites dos Visitantes");
                p.sendMessage(TextFormat.GRAY + "Quebre um bloco = Pos1 | Interaja com bloco = Pos2");
                break;

            case "miningarea":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                setupMode.put(p.getUniqueId(), "miningarea");
                setupPos1.remove(p.getUniqueId());
                p.sendMessage(TextFormat.AQUA + "Modo de configuracao: Area de Mineracao");
                p.sendMessage(TextFormat.GRAY + "Quebre um bloco = Pos1 | Interaja com bloco = Pos2");
                break;

            case "hammer":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                giveHammer(p);
                break;

            case "visit":
                if (isPrisoner(p.getUniqueId())) {
                    p.sendMessage(TextFormat.RED + "Prisioneiros nao podem visitar!");
                    return true;
                }
                if (visitorSpawn == null) {
                    p.sendMessage(TextFormat.RED + "Spawn de visitantes nao configurado!");
                    return true;
                }
                visitors.add(p.getUniqueId());
                p.teleport(visitorSpawn);
                p.sendMessage(TextFormat.GREEN + "Voce esta visitando a prisao!");
                p.sendMessage(TextFormat.GRAY + "Sair da area de visita te teleportara ao lobby.");
                break;

            case "setores":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                giveSetoresStick(p);
                break;

            case "list":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                listPrisoners(p);
                break;

            case "release":
                if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }
                if (args.length < 2) { p.sendMessage(TextFormat.RED + "Uso: /prision release <jogador>"); return true; }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) { p.sendMessage(TextFormat.RED + "Jogador nao encontrado!"); return true; }
                if (!isPrisoner(target.getUniqueId())) { p.sendMessage(TextFormat.RED + "Este jogador nao esta preso!"); return true; }
                releasePrisoner(target.getUniqueId());
                p.sendMessage(TextFormat.GREEN + target.getName() + " foi liberado!");
                break;

            case "status":
                if (isPrisoner(p.getUniqueId())) {
                    PrisonerData data = prisoners.get(p.getUniqueId());
                    if (data.perpetual) {
                        p.sendMessage(TextFormat.RED + "Voce esta preso! Pena: PERPETUA");
                    } else {
                        long remaining = data.releaseAt - System.currentTimeMillis();
                        p.sendMessage(TextFormat.YELLOW + "Pena restante: " + formatTime(remaining));
                    }
                    p.sendMessage(data.canMine ? TextFormat.GREEN + "Mineracao: Permitida" : TextFormat.RED + "Mineracao: Bloqueada");
                } else if (visitors.contains(p.getUniqueId())) {
                    p.sendMessage(TextFormat.AQUA + "Voce esta visitando a prisao.");
                } else {
                    p.sendMessage(TextFormat.GREEN + "Voce nao esta preso nem visitando.");
                }
                break;

            default:
                p.sendMessage(TextFormat.RED + "Subcomando desconhecido.");
                break;
        }
        return true;
    }

    // ======================================================
    //  PRISON COMMANDS
    // ======================================================
    private void giveHammer(Player admin) {
        Item hammer = Item.get("minecraft:mace", 0, 1);
        hammer.setCustomName(TextFormat.RED + "" + TextFormat.BOLD + "Martelo da Prisao");
        CompoundTag tag = hammer.getNamedTag() != null ? hammer.getNamedTag() : new CompoundTag();
        tag.putBoolean("prisonHammer", true);
        hammer.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add(TextFormat.GRAY + "Atinja um jogador para");
        lore.add(TextFormat.GRAY + "envia-lo para a prisao.");
        hammer.setLore(lore.toArray(new String[0]));
        admin.getInventory().addItem(hammer);
        admin.sendMessage(TextFormat.GREEN + "Martelo da Prisao recebido!");
    }

    private void giveSetoresStick(Player admin) {
        Item stick = Item.get("minecraft:stick", 0, 1);
        stick.setCustomName(TextFormat.GOLD + "Graveto de Ore Generator");
        CompoundTag tag = stick.getNamedTag() != null ? stick.getNamedTag() : new CompoundTag();
        tag.putBoolean("prisonSetoresStick", true);
        stick.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add(TextFormat.GRAY + "Quebre pedra = Criar generator");
        lore.add(TextFormat.GRAY + "Botao direito = Remover/Config");
        stick.setLore(lore.toArray(new String[0]));
        admin.getInventory().addItem(stick);
        setupMode.put(admin.getUniqueId(), "setores");
        admin.sendMessage(TextFormat.GREEN + "Graveto de Ore Generator recebido!");
        admin.sendMessage(TextFormat.GRAY + "Quebre pedra para criar generator.");
        admin.sendMessage(TextFormat.GRAY + "Botao direito em generator para remover.");
        admin.sendMessage(TextFormat.GRAY + "Botao direito sem generator para configurar.");
    }

    private void listPrisoners(Player admin) {
        if (prisoners.isEmpty()) {
            admin.sendMessage(TextFormat.YELLOW + "Nenhum prisioneiro no momento.");
            return;
        }
        admin.sendMessage(TextFormat.AQUA + "--- Prisioneiros ---");
        for (Map.Entry<UUID, PrisonerData> entry : prisoners.entrySet()) {
            PrisonerData data = entry.getValue();
            Player p = getPlayer(entry.getKey());
            String name = p != null ? p.getName() : entry.getKey().toString().substring(0, 8);
            String mineStatus = data.canMine ? TextFormat.GREEN + "[Minerando]" : TextFormat.RED + "[Sem mineracao]";
            if (data.perpetual) {
                admin.sendMessage(TextFormat.RED + name + " - PERPETUA " + mineStatus);
            } else {
                long remaining = data.releaseAt - System.currentTimeMillis();
                if (remaining <= 0) {
                    admin.sendMessage(TextFormat.GREEN + name + " - Liberto " + mineStatus);
                } else {
                    admin.sendMessage(TextFormat.YELLOW + name + " - " + formatTime(remaining) + " " + mineStatus);
                }
            }
        }
    }

    // ======================================================
    //  PRISON CORE
    // ======================================================
    private void sendToPrison(Player target, long sentenceMs, boolean canMine) {
        UUID uid = target.getUniqueId();

        removeFromMinigames(target);

        boolean perpetual = sentenceMs == -1;
        long releaseAt = perpetual ? -1 : System.currentTimeMillis() + sentenceMs;
        PrisonerData data = new PrisonerData(releaseAt, perpetual, canMine);
        prisoners.put(uid, data);
        inMiningArea.remove(uid);

        target.getInventory().clearAll();
        target.setGamemode(Player.SURVIVAL);

        if (prisonSpawn != null) {
            target.teleport(prisonSpawn);
        }

        target.sendMessage(TextFormat.RED + "" + TextFormat.BOLD + "Voce foi preso!");
        if (perpetual) {
            target.sendMessage(TextFormat.RED + "Pena: " + TextFormat.BOLD + "PERPETUA");
        } else {
            target.sendMessage(TextFormat.YELLOW + "Pena: " + formatTime(sentenceMs));
        }
        if (canMine) {
            target.sendMessage(TextFormat.GREEN + "Mineracao: " + TextFormat.BOLD + "PERMITIDA");
            target.sendMessage(TextFormat.GRAY + "Entre na area de mineracao para receber a picareta.");
        } else {
            target.sendMessage(TextFormat.RED + "Mineracao: " + TextFormat.BOLD + "BLOQUEADA");
        }

        savePrisonConfig();
    }

    private void releasePrisoner(UUID uid) {
        prisoners.remove(uid);
        inMiningArea.remove(uid);
        Player p = getPlayer(uid);
        if (p != null) {
            p.getInventory().clearAll();
            p.sendMessage(TextFormat.GREEN + "" + TextFormat.BOLD + "Voce foi liberado da prisao!");
            teleportMainLobby(p);
        }
        savePrisonConfig();
    }

    public boolean isPrisoner(UUID uid) {
        PrisonerData data = prisoners.get(uid);
        return data != null && data.active;
    }

    private boolean prisonerCanMine(UUID uid) {
        PrisonerData data = prisoners.get(uid);
        return data != null && data.canMine;
    }

    private void removeFromMinigames(Player target) {
        try {
            Object br = Class.forName("org.gugunet.br.BattleRoyale")
                .getMethod("getInstance").invoke(null);
            if (br != null) {
                br.getClass().getMethod("forceRemovePlayer", Player.class).invoke(br, target);
            }
        } catch (Exception ignored) {}
    }

    private void giveMiningPickaxe(Player prisoner) {
        prisoner.getInventory().clearAll();
        Item pickaxe = Item.get("minecraft:iron_pickaxe", 0, 1);
        pickaxe.setCustomName(TextFormat.GOLD + "Picareta de Mineracao");
        CompoundTag tag = pickaxe.getNamedTag() != null ? pickaxe.getNamedTag() : new CompoundTag();
        tag.putBoolean("prisonPickaxe", true);
        pickaxe.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add(TextFormat.GRAY + "Quebra apenas minerios");
        lore.add(TextFormat.GRAY + "na area de mineracao.");
        pickaxe.setLore(lore.toArray(new String[0]));
        prisoner.getInventory().setItem(0, pickaxe);
    }

    private void removeMiningPickaxe(Player prisoner) {
        prisoner.getInventory().clearAll();
    }

    // ======================================================
    //  MINING SYSTEM
    // ======================================================
    private void processMining(Player player, Block block) {
        String blockId = block.getId();
        int x = block.getFloorX(), y = block.getFloorY(), z = block.getFloorZ();
        String key = x + "," + y + "," + z;
        Level level = block.getLevel();

        level.setBlock(x, y, z, Block.get("minecraft:stone"), true, true);

        int regenTime = REGEN_TIMES.getOrDefault(blockId, 60 * 20);
        TaskHandler task = getServer().getScheduler().scheduleDelayedTask(this, () -> {
            String oreId = pickRandomOre();
            level.setBlock(x, y, z, Block.get(oreId), true, true);
            activeOres.put(key, oreId);
            regenTasks.remove(key);
        }, regenTime);
        regenTasks.put(key, task);
        activeOres.remove(key);

        UUID uid = player.getUniqueId();
        PrisonerData data = prisoners.get(uid);
        if (data != null && !data.perpetual) {
            long reduction = SENTENCE_REDUCTION.getOrDefault(blockId, 5000L);
            data.releaseAt -= reduction;
            if (data.releaseAt < System.currentTimeMillis()) {
                data.releaseAt = System.currentTimeMillis();
            }

            long remaining = data.releaseAt - System.currentTimeMillis();
            if (remaining <= 0) {
                player.sendMessage(TextFormat.GREEN + "Mineracao concluida! Pena cumprida!");
                releasePrisoner(uid);
            } else {
                player.sendActionBar(TextFormat.GREEN + "Minerado! Pena: " + formatTime(remaining));
            }
            savePrisonConfig();
        } else if (data != null && data.perpetual) {
            player.sendActionBar(TextFormat.YELLOW + "Minerado! Pena: PERPETUA (nao diminui)");
        }

        playMineSound(player);
    }

    private String pickRandomOre() {
        int totalWeight = 0;
        for (int w : ORE_WEIGHTS) totalWeight += w;
        int roll = new Random().nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < ORE_TYPES.length; i++) {
            cumulative += ORE_WEIGHTS[i];
            if (roll < cumulative) return ORE_TYPES[i];
        }
        return ORE_TYPES[0];
    }

    private boolean isOre(String blockId) {
        for (String ore : ORE_TYPES) {
            if (ore.equals(blockId)) return true;
        }
        return false;
    }

    // ======================================================
    //  ORE GENERATORS
    // ======================================================
    private void initializeOreGenerators() {
        Level world = getServer().getLevelByName("world");
        if (world == null) return;

        for (String key : oreGenerators) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            Block block = world.getBlock(x, y, z);
            if (!isOre(block.getId())) {
                String oreId = pickRandomOre();
                world.setBlock(x, y, z, Block.get(oreId), true, true);
                activeOres.put(key, oreId);
            } else {
                activeOres.put(key, block.getId());
            }
        }
        getLogger().info("Ore generators inicializados: " + oreGenerators.size());
    }

    private void setOreGenerator(Block block) {
        int x = block.getFloorX(), y = block.getFloorY(), z = block.getFloorZ();
        String key = x + "," + y + "," + z;
        oreGenerators.add(key);
        String oreId = pickRandomOre();
        block.getLevel().setBlock(x, y, z, Block.get(oreId), true, true);
        activeOres.put(key, oreId);
        savePrisonConfig();
    }

    private void removeOreGenerator(Block block) {
        int x = block.getFloorX(), y = block.getFloorY(), z = block.getFloorZ();
        String key = x + "," + y + "," + z;
        oreGenerators.remove(key);
        activeOres.remove(key);
        TaskHandler task = regenTasks.remove(key);
        if (task != null) task.cancel();
        block.getLevel().setBlock(x, y, z, Block.get("minecraft:stone"), true, true);
        savePrisonConfig();
    }

    // ======================================================
    //  BOUNDARY, SENTENCE & AUTOSAVE TASKS
    // ======================================================
    private void startAutoSaveTask() {
        autoSaveTask = getServer().getScheduler().scheduleRepeatingTask(this, this::savePrisonConfig, 60 * 20);
    }

    private void startSentenceCheckTask() {
        sentenceCheckTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, PrisonerData> entry : new HashMap<>(prisoners).entrySet()) {
                PrisonerData data = entry.getValue();
                if (!data.active || data.perpetual) continue;
                if (now >= data.releaseAt) {
                    releasePrisoner(entry.getKey());
                    Player p = getPlayer(entry.getKey());
                    if (p != null) {
                        p.sendTitle(TextFormat.GREEN + "Liberado!", TextFormat.GRAY + "Sua pena acabou.", 10, 60, 10);
                    }
                }
            }
        }, 20);
    }

    private void startBoundaryCheckTask() {
        boundaryCheckTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (Map.Entry<UUID, PrisonerData> entry : new HashMap<>(prisoners).entrySet()) {
                PrisonerData data = entry.getValue();
                if (!data.active) continue;
                Player p = getPlayer(entry.getKey());
                if (p == null) continue;
                UUID uid = entry.getKey();

                if (prisonSpawn == null || prisonMin == null || prisonMax == null) continue;
                if (!p.isOp() && !isInsideBounds(p.getLocation(), prisonMin, prisonMax)) {
                    p.teleport(prisonSpawn);
                    p.sendMessage(TextFormat.RED + "Voce saiu dos limites da prisao!");
                }

                boolean insideMining = miningMin != null && miningMax != null && isInsideBounds(p.getLocation(), miningMin, miningMax);
                boolean wasInside = inMiningArea.contains(uid);

                if (insideMining && !wasInside) {
                    inMiningArea.add(uid);
                    if (data.canMine) {
                        giveMiningPickaxe(p);
                        p.sendMessage(TextFormat.GREEN + "Voce entrou na area de mineracao. Picareta recebida!");
                    } else {
                        p.sendMessage(TextFormat.RED + "Voce entrou na area de mineracao, mas nao tem permissao para minerar.");
                    }
                } else if (!insideMining && wasInside) {
                    inMiningArea.remove(uid);
                    if (p.getInventory().getItemInHand() != null &&
                        p.getInventory().getItemInHand().getNamedTag() != null &&
                        p.getInventory().getItemInHand().getNamedTag().getBoolean("prisonPickaxe")) {
                        removeMiningPickaxe(p);
                        p.sendMessage(TextFormat.YELLOW + "Voce saiu da area de mineracao. Picareta removida.");
                    }
                }
            }

            for (UUID uid : new ArrayList<>(visitors)) {
                Player p = getPlayer(uid);
                if (p == null) { visitors.remove(uid); continue; }
                if (visitorMin == null || visitorMax == null) {
                    visitors.remove(uid);
                    teleportMainLobby(p);
                    continue;
                }
                if (!isInsideBounds(p.getLocation(), visitorMin, visitorMax)) {
                    visitors.remove(uid);
                    teleportMainLobby(p);
                    p.sendMessage(TextFormat.YELLOW + "Voce saiu da area de visita e voltou ao lobby.");
                }
            }
        }, 10);
    }

    // ======================================================
    //  EVENT HANDLERS
    // ======================================================
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        Long last = lastBoundaryCheck.get(uid);
        long now = System.currentTimeMillis();
        if (last != null && (now - last) < 500) return;

        PrisonerData data = prisoners.get(uid);
        if (data != null && data.active && !player.isOp()) {
            if (prisonMin != null && prisonMax != null && prisonSpawn != null) {
                if (!isInsideBounds(event.getTo(), prisonMin, prisonMax)) {
                    player.teleport(prisonSpawn);
                    player.sendMessage(TextFormat.RED + "Voce saiu dos limites da prisao!");
                    lastBoundaryCheck.put(uid, now);
                    return;
                }
            }
        }

        if (visitors.contains(uid)) {
            if (visitorMin != null && visitorMax != null) {
                if (!isInsideBounds(event.getTo(), visitorMin, visitorMax)) {
                    visitors.remove(uid);
                    teleportMainLobby(player);
                    player.sendMessage(TextFormat.YELLOW + "Voce saiu da area de visita e voltou ao lobby.");
                    lastBoundaryCheck.put(uid, now);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isPrisoner(player.getUniqueId()) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "Voce nao pode falar estando preso!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        String mode = setupMode.get(uid);
        if (mode != null && player.isOp()) {
            event.setCancelled(true);
            if (mode.equals("setores")) {
                Block block = event.getBlock();
                if (block.getId().equals("minecraft:stone")) {
                    setOreGenerator(block);
                    player.sendMessage(TextFormat.GREEN + "Ore generator criado!");
                } else {
                    player.sendMessage(TextFormat.RED + "Quebre pedra para criar um ore generator.");
                }
            } else {
                Block block = event.getBlock();
                setupPos1.put(uid, new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                player.sendMessage(TextFormat.GREEN + "Pos1 definida: (" + block.getFloorX() + "," + block.getFloorY() + "," + block.getFloorZ() + ")");
            }
            return;
        }

        if (player.isOp()) return;

        if (!isPrisoner(uid)) return;

        event.setCancelled(true);

        Block block = event.getBlock();
        String blockId = block.getId();

        if (!prisonerCanMine(uid)) return;

        if (miningMin != null && miningMax != null && isInsideBounds(block.getLocation(), miningMin, miningMax)) {
            if (isOre(blockId) && isOreGenerator(block.getFloorX(), block.getFloorY(), block.getFloorZ())) {
                Item held = player.getInventory().getItemInHand();
                if (held != null && held.getNamedTag() != null && held.getNamedTag().getBoolean("prisonPickaxe")) {
                    processMining(player, block);
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isPrisoner(player.getUniqueId()) && !player.isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isPrisoner(player.getUniqueId()) && !player.isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (event.getInventory().getHolder() instanceof Player) {
            Player p = (Player) event.getInventory().getHolder();
            if (isPrisoner(p.getUniqueId()) && !p.isOp()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        Item item = damager.getInventory().getItemInHand();
        if (item != null && item.getNamedTag() != null && item.getNamedTag().getBoolean("prisonHammer")) {
            event.setCancelled(true);
            if (damager.isOp()) {
                if (isPrisoner(target.getUniqueId())) {
                    openExistingPrisonerHammerForm(damager, target);
                } else {
                    openHammerForm(damager, target);
                }
            }
            return;
        }

        if (isPrisoner(damager.getUniqueId()) && !damager.isOp()) {
            event.setCancelled(true);
            return;
        }

        if (isPrisoner(target.getUniqueId()) && !isPrisoner(damager.getUniqueId())) {
            return;
        }
    }

    @EventHandler
    public void onFoodLevelChange(PlayerFoodLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (isPrisoner(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Item item = event.getItem();

        String mode = setupMode.get(uid);
        if (mode != null && player.isOp()) {
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK ||
                event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
                if (checkCooldown(player)) return;

                if (mode.equals("setores")) {
                    event.setCancelled(true);
                    if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                        Block block = event.getBlock();
                        String key = block.getFloorX() + "," + block.getFloorY() + "," + block.getFloorZ();
                        if (oreGenerators.contains(key)) {
                            removeOreGenerator(block);
                            player.sendMessage(TextFormat.RED + "Ore generator removido!");
                        } else {
                            openSetoresDisableForm(player);
                        }
                    } else {
                        openSetoresDisableForm(player);
                    }
                    return;
                } else {
                    if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                        Block block = event.getBlock();
                        Vector3 pos1 = setupPos1.get(uid);
                        if (pos1 != null) {
                            event.setCancelled(true);
                            Vector3 pos2 = new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ());
                            saveSetup(player, mode, pos1, pos2);
                            setupPos1.remove(uid);
                            setupMode.remove(uid);
                        } else {
                            player.sendMessage(TextFormat.RED + "Defina a Pos1 primeiro (quebre um bloco).");
                        }
                    }
                }
            }
            return;
        }

        if (isPrisoner(uid) && !player.isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        PrisonerData data = prisoners.get(uid);
        if (data != null && data.active) {
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (player.isOnline()) {
                    if (prisonSpawn != null) {
                        player.teleport(prisonSpawn);
                    }
                    player.setGamemode(Player.SURVIVAL);
                    player.getInventory().clearAll();
                    inMiningArea.remove(uid);
                    player.sendMessage(TextFormat.RED + "Voce ainda esta preso!");
                    if (data.perpetual) {
                        player.sendMessage(TextFormat.RED + "Pena: PERPETUA");
                    } else {
                        long remaining = data.releaseAt - System.currentTimeMillis();
                        if (remaining > 0) {
                            player.sendMessage(TextFormat.YELLOW + "Pena restante: " + formatTime(remaining));
                        } else {
                            releasePrisoner(uid);
                        }
                    }
                    if (data.canMine) {
                        player.sendMessage(TextFormat.GREEN + "Mineracao: PERMITIDA - Entre na area de mineracao.");
                    } else {
                        player.sendMessage(TextFormat.RED + "Mineracao: BLOQUEADA");
                    }
                }
            }, 5);
        }
        savePrisonConfig();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        visitors.remove(uid);
        inMiningArea.remove(uid);
        setupMode.remove(uid);
        setupPos1.remove(uid);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (isPrisoner(uid) && prisonSpawn != null) {
            event.setRespawnPosition(it.unimi.dsi.fastutil.Pair.of((cn.nukkit.level.Position) new Position(prisonSpawn.getX(), prisonSpawn.getY(), prisonSpawn.getZ(), prisonSpawn.getLevel()), cn.nukkit.network.protocol.types.SpawnPointType.WORLD));
        }
    }

    // ======================================================
    //  FORMS
    // ======================================================
    private void openHammerForm(Player admin, Player target) {
        List<String> units = Arrays.asList("Segundos", "Minutos", "Horas", "Dias", "Meses", "Anos", "Perpetua");
        new CustomForm(TextFormat.RED + "Martelo - Nova Pena")
            .addDropdown("Unidade de Tempo", units, 0)
            .addSlider("Quantidade", 1, 100, 1, 1)
            .addToggle("Pode minerar", true)
            .onSubmit((p, resp) -> {
                int unitIdx = resp.getDropdownResponse(0).elementId();
                int amount = (int) resp.getSliderResponse(1);
                boolean canMine = resp.getToggleResponse(2);

                long sentenceMs;
                if (unitIdx == 6) {
                    sentenceMs = -1;
                } else {
                    switch (unitIdx) {
                        case 0: sentenceMs = amount * 1000L; break;
                        case 1: sentenceMs = amount * 60_000L; break;
                        case 2: sentenceMs = amount * 3_600_000L; break;
                        case 3: sentenceMs = amount * 86_400_000L; break;
                        case 4: sentenceMs = amount * 2_592_000_000L; break;
                        case 5: sentenceMs = amount * 31_536_000_000L; break;
                        default: sentenceMs = amount * 60_000L;
                    }
                }

                sendToPrison(target, sentenceMs, canMine);
                p.sendMessage(TextFormat.GREEN + target.getName() + " foi preso!");
                if (sentenceMs == -1) {
                    p.sendMessage(TextFormat.YELLOW + "Pena: PERPETUA");
                } else {
                    p.sendMessage(TextFormat.YELLOW + "Pena: " + formatTime(sentenceMs));
                }
                p.sendMessage(canMine ? TextFormat.GREEN + "Mineracao: Permitida" : TextFormat.RED + "Mineracao: Bloqueada");
            })
            .send(admin);
    }

    private void openExistingPrisonerHammerForm(Player admin, Player target) {
        PrisonerData data = prisoners.get(target.getUniqueId());
        String currentSentence = data.perpetual ? "PERPETUA" : formatTime(data.releaseAt - System.currentTimeMillis());
        String mineStatus = data.canMine ? TextFormat.GREEN + "Sim" : TextFormat.RED + "Nao";

        new SimpleForm(TextFormat.RED + "Gerenciar Prisioneiro: " + target.getName(),
            TextFormat.GRAY + "Pena atual: " + TextFormat.YELLOW + currentSentence + "\n" +
            TextFormat.GRAY + "Pode minerar: " + mineStatus)
            .addButton(TextFormat.RED + "Aumentar Pena\n+ Tirar mineracao", p -> {
                openIncreaseSentenceForm(p, target, false);
            })
            .addButton(TextFormat.YELLOW + "Aumentar Pena\n+ Manter mineracao", p -> {
                openIncreaseSentenceForm(p, target, true);
            })
            .addButton(TextFormat.GREEN + "Diminuir Pena\n+ Dar mineracao", p -> {
                openDecreaseSentenceForm(p, target, true);
            })
            .addButton(TextFormat.GREEN + "Diminuir Pena\n+ Sem mineracao", p -> {
                openDecreaseSentenceForm(p, target, false);
            })
            .addButton(TextFormat.AQUA + "Soltar da Prisao", p -> {
                releasePrisoner(target.getUniqueId());
                p.sendMessage(TextFormat.GREEN + target.getName() + " foi solto da prisao!");
            })
            .send(admin);
    }

    private void openIncreaseSentenceForm(Player admin, Player target, boolean keepMine) {
        List<String> units = Arrays.asList("Segundos", "Minutos", "Horas", "Dias", "Meses", "Anos", "Perpetua");
        new CustomForm(TextFormat.RED + "Aumentar Pena de " + target.getName())
            .addDropdown("Unidade de Tempo", units, 1)
            .addSlider("Quantidade", 1, 100, 1, 1)
            .onSubmit((p, resp) -> {
                int unitIdx = resp.getDropdownResponse(0).elementId();
                int amount = (int) resp.getSliderResponse(1);

                long addMs;
                if (unitIdx == 6) {
                    addMs = -1;
                } else {
                    switch (unitIdx) {
                        case 0: addMs = amount * 1000L; break;
                        case 1: addMs = amount * 60_000L; break;
                        case 2: addMs = amount * 3_600_000L; break;
                        case 3: addMs = amount * 86_400_000L; break;
                        case 4: addMs = amount * 2_592_000_000L; break;
                        case 5: addMs = amount * 31_536_000_000L; break;
                        default: addMs = amount * 60_000L;
                    }
                }

                PrisonerData data = prisoners.get(target.getUniqueId());
                if (data == null) return;

                if (addMs == -1) {
                    data.perpetual = true;
                    data.releaseAt = -1;
                } else if (!data.perpetual) {
                    data.releaseAt += addMs;
                }

                data.canMine = keepMine;
                inMiningArea.remove(target.getUniqueId());

                if (!keepMine) {
                    removeMiningPickaxe(target);
                    target.getInventory().clearAll();
                }

                target.sendMessage(TextFormat.RED + "Sua pena foi aumentada!");
                if (data.perpetual) {
                    target.sendMessage(TextFormat.RED + "Pena: PERPETUA");
                } else {
                    target.sendMessage(TextFormat.YELLOW + "Pena restante: " + formatTime(data.releaseAt - System.currentTimeMillis()));
                }
                target.sendMessage(data.canMine ? TextFormat.GREEN + "Mineracao: Permitida" : TextFormat.RED + "Mineracao: Bloqueada");

                p.sendMessage(TextFormat.GREEN + "Pena de " + target.getName() + " aumentada!");
                savePrisonConfig();
            })
            .send(admin);
    }

    private void openDecreaseSentenceForm(Player admin, Player target, boolean giveMine) {
        List<String> units = Arrays.asList("Segundos", "Minutos", "Horas", "Dias", "Meses", "Anos");
        new CustomForm(TextFormat.GREEN + "Diminuir Pena de " + target.getName())
            .addDropdown("Unidade de Tempo", units, 1)
            .addSlider("Quantidade", 1, 100, 1, 1)
            .onSubmit((p, resp) -> {
                int unitIdx = resp.getDropdownResponse(0).elementId();
                int amount = (int) resp.getSliderResponse(1);

                long subMs;
                switch (unitIdx) {
                    case 0: subMs = amount * 1000L; break;
                    case 1: subMs = amount * 60_000L; break;
                    case 2: subMs = amount * 3_600_000L; break;
                    case 3: subMs = amount * 86_400_000L; break;
                    case 4: subMs = amount * 2_592_000_000L; break;
                    case 5: subMs = amount * 31_536_000_000L; break;
                    default: subMs = amount * 60_000L;
                }

                PrisonerData data = prisoners.get(target.getUniqueId());
                if (data == null) return;

                if (data.perpetual) {
                    data.perpetual = false;
                    data.releaseAt = System.currentTimeMillis() + subMs;
                } else {
                    data.releaseAt -= subMs;
                }

                data.canMine = giveMine;

                if (giveMine && inMiningArea.contains(target.getUniqueId())) {
                    giveMiningPickaxe(target);
                }

                target.sendMessage(TextFormat.GREEN + "Sua pena foi reduzida!");
                if (data.perpetual) {
                    target.sendMessage(TextFormat.RED + "Pena: PERPETUA");
                } else {
                    long remaining = data.releaseAt - System.currentTimeMillis();
                    if (remaining <= 0) {
                        p.sendMessage(TextFormat.GREEN + target.getName() + " cumpriu toda a pena!");
                        releasePrisoner(target.getUniqueId());
                        return;
                    }
                    target.sendMessage(TextFormat.YELLOW + "Pena restante: " + formatTime(remaining));
                }
                target.sendMessage(data.canMine ? TextFormat.GREEN + "Mineracao: Permitida" : TextFormat.RED + "Mineracao: Bloqueada");

                p.sendMessage(TextFormat.GREEN + "Pena de " + target.getName() + " reduzida!");
                savePrisonConfig();
            })
            .send(admin);
    }

    private void openSetoresDisableForm(Player player) {
        SimpleForm form = new SimpleForm(TextFormat.GOLD + "Ore Generator", "Selecione uma opcao:");
        form.addButton(TextFormat.RED + "Sair do Modo Ore Generator", p -> {
            setupMode.remove(p.getUniqueId());
            p.getInventory().remove(p.getInventory().getItemInHand());
            p.sendMessage(TextFormat.YELLOW + "Modo Ore Generator desativado!");
        });
        form.addButton(TextFormat.GRAY + "Cancelar", p -> {});
        form.send(player);
    }

    private void saveSetup(Player player, String mode, Vector3 p1, Vector3 p2) {
        Vector3 min = new Vector3(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.min(p1.z, p2.z));
        Vector3 max = new Vector3(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y), Math.max(p1.z, p2.z));
        switch (mode) {
            case "prisonlimits":
                prisonMin = min;
                prisonMax = max;
                player.sendMessage(TextFormat.GREEN + "Limites da prisao configurados!");
                break;
            case "visitorlimits":
                visitorMin = min;
                visitorMax = max;
                player.sendMessage(TextFormat.GREEN + "Limites dos visitantes configurados!");
                break;
            case "miningarea":
                miningMin = min;
                miningMax = max;
                player.sendMessage(TextFormat.GREEN + "Area de mineracao configurada!");
                break;
        }
        savePrisonConfig();
    }

    // ======================================================
    //  UTILITIES
    // ======================================================
    private Player getPlayer(UUID uuid) {
        return getServer().getPlayer(uuid).orElse(null);
    }

    private void teleportMainLobby(Player player) {
        try {
            Config cfg = new Config(new File(getServer().getDataPath(), "plugins/GugunetCore/config.yml"), Config.YAML);
            if (cfg.exists("loginpos.world")) {
                Level lvl = getServer().getLevelByName(cfg.getString("loginpos.world"));
                if (lvl != null) {
                    player.teleport(new Location(cfg.getDouble("loginpos.x"), cfg.getDouble("loginpos.y"),
                        cfg.getDouble("loginpos.z"), cfg.getDouble("loginpos.yaw"),
                        cfg.getDouble("loginpos.pitch"), lvl));
                    return;
                }
            }
        } catch (Exception ignored) {}
        Level main = getServer().getDefaultLevel();
        if (main != null) player.teleport(main.getSpawnLocation().getLocation());
    }

    private boolean isInsideBounds(Position pos, Vector3 min, Vector3 max) {
        if (min == null || max == null) return false;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double minX = Math.min(min.x, max.x), maxX = Math.max(min.x, max.x);
        double minY = Math.min(min.y, max.y), maxY = Math.max(min.y, max.y);
        double minZ = Math.min(min.z, max.z), maxZ = Math.max(min.z, max.z);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private boolean isOreGenerator(int x, int y, int z) {
        return oreGenerators.contains(x + "," + y + "," + z);
    }

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = interactCooldown.get(uid);
        if (last != null && (now - last) < INTERACT_COOLDOWN_MS) return true;
        interactCooldown.put(uid, now);
        return false;
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0s";
        long totalSec = ms / 1000;
        long years = totalSec / 31536000;
        long months = (totalSec % 31536000) / 2592000;
        long days = (totalSec % 2592000) / 86400;
        long hours = (totalSec % 86400) / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append("a ");
        if (months > 0) sb.append(months).append("mes ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("min ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private void playMineSound(Player player) {
        try {
            cn.nukkit.network.protocol.PlaySoundPacket pk = new cn.nukkit.network.protocol.PlaySoundPacket();
            pk.name = "dig.stone";
            pk.volume = 1.0f;
            pk.pitch = 1.0f;
            pk.x = (int) player.getX();
            pk.y = (int) player.getY();
            pk.z = (int) player.getZ();
            player.dataPacket(pk);
        } catch (Exception ignored) {}
    }
}

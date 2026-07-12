package org.gugunet.br;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.effect.Effect;
import cn.nukkit.entity.effect.EffectType;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerFoodLevelChangeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.form.window.CustomForm;
import cn.nukkit.form.window.SimpleForm;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.HugeExplodeParticle;
import cn.nukkit.level.particle.PortalParticle;
import cn.nukkit.level.particle.RedstoneParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.PlaySoundPacket;
import cn.nukkit.network.protocol.types.SpawnPointType;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class BattleRoyale extends PluginBase implements Listener {

    public enum GameState { IDLE, LOBBY, STARTING, ACTIVE, ENDING }
    public enum ChestType  { NONE, TRASH, NORMAL, GOOD, EPIC, LEGENDARY, RANDOM }

    private static final int MIN_PLAYERS          = 2;
    private static final int COUNTDOWN_SECS       = 20;
    private static final int PVP_GRACE_TICKS      = 5  * 20;
    private static final int FLOOR_RESTORE_TICKS  = 10 * 20;
    private static final int GAS_START_TICKS      = 60 * 20;
    private static final int GAS_INTERVAL_TICKS   = 50 * 20;
    private static final double GAS_SHRINK        = 3.0;
    private static final int WINNER_SPEC_TICKS    = 25 * 20;
    private static final int PARTICLE_TICKS       = 10;
    private static final int DAMAGE_TICKS         = 20;
    private static final int ACTIONBAR_TICKS      = 20;
    private static final int MAX_FLYING_CHESTS    = 5;

    private static BattleRoyale instance;
    private GameState gameState = GameState.IDLE;

    private final List<UUID> lobbyPlayers     = new ArrayList<>();
    private final List<UUID> alivePlayers     = new ArrayList<>();
    private final List<UUID> spectatorPlayers = new ArrayList<>();
    private final Map<UUID, List<Item>> pendingSpectator = new HashMap<>();

    private String pvpWorldName = "pvp";
    private Location lobbySpawn   = null;
    private Vector3  floorMin     = null, floorMax    = null;
    private Vector3  spectatorMin = null, spectatorMax = null;
    private Vector3  gasMin       = null, gasMax       = null;
    private Vector3  lobbyMin     = null, lobbyMax    = null;
    private final List<ChestEntry> registeredChests = new ArrayList<>();

    private final Map<String, Block>     savedFloor     = new HashMap<>();
    private final Set<String>            activeChests   = new HashSet<>();
    private final Map<String, ChestType> chestTypes     = new HashMap<>();
    private final Map<String, Long>      gasChestTimers = new HashMap<>();
    private final Set<String>            deathChestKeys = new HashSet<>();
    private final Map<UUID, Location>    deathLocations = new HashMap<>();

    private double gasCenterX, gasCenterZ, gasRadius, gasMaxRadius;
    private boolean pvpAllowed = false;
    private boolean gasActive  = false;
    private final Map<UUID, Integer> gasDmgLevel = new HashMap<>();
    private final Map<UUID, Long>    lastGasDmg  = new HashMap<>();

    private final Map<UUID, String>  setupMode = new HashMap<>();
    private final Map<UUID, Vector3> setupPos1 = new HashMap<>();

    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 500;

    private final Map<UUID, Long> superShockCooldown = new HashMap<>();
    private final Map<UUID, Long> speedShardCooldown = new HashMap<>();
    private final Map<UUID, Long> dashShardCooldown = new HashMap<>();
    private final Map<UUID, Long> knockbackStickCooldown = new HashMap<>();
    private final Map<UUID, Integer> toxicHatPoison = new HashMap<>();
    private final Set<UUID> dashImmortal = new HashSet<>();
    private final Set<UUID> projectileShieldActive = new HashSet<>();
    private final Set<UUID> reverseStoneActive = new HashSet<>();
    private final Map<UUID, Integer> reverseStoneTier = new HashMap<>();

    private final Set<String> flyingChestAreas = new HashSet<>();
    private final List<String> activeFlyingChests = new ArrayList<>();
    private int flyingChestsThisMatch = 0;
    private final Map<String, TaskHandler> flyingChestTasks = new HashMap<>();
    private final Map<UUID, Vector3> flyingChestSetupPos = new HashMap<>();

    private TaskHandler countdownTask, gasTask, particleTask, damageTask, actionBarTask;
    private int countdownVal = COUNTDOWN_SECS;

    private static class ChestEntry {
        Vector3 pos; String levelName; int chance; ChestType type;
        ChestEntry(Vector3 pos, String levelName, int chance, ChestType type) {
            this.pos = pos; this.levelName = levelName; this.chance = chance; this.type = type;
        }
    }

    public static BattleRoyale getInstance() { return instance; }

    @Override public void onLoad()    { instance = this; }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadBrConfig();
        getLogger().info("BattleRoyale ativado!");
    }

    @Override
    public void onDisable() { cancelAllTasks(); cleanWorldItems(); }

    private Player getPlayer(UUID uuid) {
        return getServer().getPlayer(uuid).orElse(null);
    }

    private void loadBrConfig() {
        Config cfg = getConfig();
        pvpWorldName = cfg.getString("pvp-world", "pvp");

        if (cfg.exists("lobby-spawn.world")) {
            Level lvl = getServer().getLevelByName(cfg.getString("lobby-spawn.world", pvpWorldName));
            if (lvl != null) lobbySpawn = new Location(
                cfg.getDouble("lobby-spawn.x"), cfg.getDouble("lobby-spawn.y"),
                cfg.getDouble("lobby-spawn.z"), cfg.getDouble("lobby-spawn.yaw"),
                cfg.getDouble("lobby-spawn.pitch"), lvl);
        }
        if (cfg.exists("floor.min.x")) {
            floorMin = new Vector3(cfg.getDouble("floor.min.x"), cfg.getDouble("floor.min.y"), cfg.getDouble("floor.min.z"));
            floorMax = new Vector3(cfg.getDouble("floor.max.x"), cfg.getDouble("floor.max.y"), cfg.getDouble("floor.max.z"));
        }
        if (cfg.exists("spectator.min.x")) {
            spectatorMin = new Vector3(cfg.getDouble("spectator.min.x"), cfg.getDouble("spectator.min.y"), cfg.getDouble("spectator.min.z"));
            spectatorMax = new Vector3(cfg.getDouble("spectator.max.x"), cfg.getDouble("spectator.max.y"), cfg.getDouble("spectator.max.z"));
        }
        if (cfg.exists("gas.min.x")) {
            gasMin = new Vector3(cfg.getDouble("gas.min.x"), cfg.getDouble("gas.min.y"), cfg.getDouble("gas.min.z"));
            gasMax = new Vector3(cfg.getDouble("gas.max.x"), cfg.getDouble("gas.max.y"), cfg.getDouble("gas.max.z"));
        }
        if (cfg.exists("lobby-limits.min.x")) {
            lobbyMin = new Vector3(cfg.getDouble("lobby-limits.min.x"), cfg.getDouble("lobby-limits.min.y"), cfg.getDouble("lobby-limits.min.z"));
            lobbyMax = new Vector3(cfg.getDouble("lobby-limits.max.x"), cfg.getDouble("lobby-limits.max.y"), cfg.getDouble("lobby-limits.max.z"));
        }
        registeredChests.clear();
        List<Map<?, ?>> chests = cfg.getMapList("chests");
        for (Map<?, ?> c : chests) {
            try {
                double cx = ((Number)c.get("x")).doubleValue();
                double cy = ((Number)c.get("y")).doubleValue();
                double cz = ((Number)c.get("z")).doubleValue();
                Object lvlObj = c.get("level"); String cl = lvlObj != null ? String.valueOf(lvlObj) : pvpWorldName;
                Object chObj = c.get("chance"); int ch = chObj != null ? ((Number)chObj).intValue() : 80;
                Object tObj = c.get("type"); ChestType t = tObj != null ? ChestType.valueOf(String.valueOf(tObj)) : ChestType.NORMAL;
                registeredChests.add(new ChestEntry(new Vector3(cx, cy, cz), cl, ch, t));
            } catch (Exception ignored) {}
        }
        flyingChestAreas.clear();
        List<String> fcAreas = cfg.getStringList("flying-chest-areas");
        if (fcAreas != null) flyingChestAreas.addAll(fcAreas);
    }

    private void saveBrConfig() {
        Config cfg = getConfig();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChestEntry e : registeredChests) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", e.pos.x); m.put("y", e.pos.y); m.put("z", e.pos.z);
            m.put("level", e.levelName); m.put("chance", e.chance); m.put("type", e.type.name());
            list.add(m);
        }
        cfg.set("chests", list);
        cfg.set("flying-chest-areas", new ArrayList<>(flyingChestAreas));
        cfg.save();
    }

    private void savePosConfig(String prefix, Vector3 min, Vector3 max) {
        Config cfg = getConfig();
        cfg.set(prefix + ".min.x", min.x); cfg.set(prefix + ".min.y", min.y); cfg.set(prefix + ".min.z", min.z);
        cfg.set(prefix + ".max.x", max.x); cfg.set(prefix + ".max.y", max.y); cfg.set(prefix + ".max.z", max.z);
        cfg.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("pvp")) return false;
        if (args.length == 0) { sender.sendMessage(TextFormat.YELLOW + "/pvp <enter|setlobbyspawn|setlobby floor|setspectatorlimits|setmapgasrange|setchestpos|setlobbylimits|setflyingchest>"); return true; }

        if (args[0].equalsIgnoreCase("enter")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Apenas jogadores!"); return true; }
            joinLobby((Player) sender); return true;
        }

        if (!(sender instanceof Player)) { sender.sendMessage("Apenas jogadores!"); return true; }
        Player p = (Player) sender;
        if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }

        switch (args[0].toLowerCase()) {
            case "setlobbyspawn":      setLobbySpawn(p);   break;
            case "setlobby":
                if (args.length > 1 && args[1].equalsIgnoreCase("floor")) enterSetupMode(p, "floor");
                else p.sendMessage(TextFormat.RED + "Uso: /pvp setlobby floor");
                break;
            case "setspectatorlimits": enterSetupMode(p, "spectator"); break;
            case "setmapgasrange":     enterSetupMode(p, "gas");       break;
            case "setchestpos":        giveChestStick(p);              break;
            case "setlobbylimits":     enterSetupMode(p, "lobbylimits"); break;
            case "setflyingchest":     giveFlyingChestStick(p);        break;
            default: p.sendMessage(TextFormat.RED + "Subcomando desconhecido.");
        }
        return true;
    }

    private void setLobbySpawn(Player p) {
        lobbySpawn = p.getLocation();
        Config cfg = getConfig();
        cfg.set("lobby-spawn.world", p.getLevel().getFolderName());
        cfg.set("lobby-spawn.x", p.getX()); cfg.set("lobby-spawn.y", p.getY()); cfg.set("lobby-spawn.z", p.getZ());
        cfg.set("lobby-spawn.yaw", p.getYaw()); cfg.set("lobby-spawn.pitch", p.getPitch());
        cfg.save();
        p.sendMessage(TextFormat.GREEN + "Spawn do lobby Battle Royale definido!");
    }

    private void enterSetupMode(Player p, String mode) {
        setupMode.put(p.getUniqueId(), mode);
        setupPos1.remove(p.getUniqueId());
        String desc = switch (mode) {
            case "floor"       -> "chao do lobby (vidro)";
            case "spectator"   -> "limites do espectador";
            case "gas"         -> "area do mapa/gas";
            case "lobbylimits" -> "limites do lobby";
            default            -> mode;
        };
        p.sendMessage(TextFormat.AQUA + "Modo de configuracao: §e" + desc);
        p.sendMessage(TextFormat.GRAY + "Quebre um bloco = Pos1 | Interaja com bloco = Pos2 (auto-confirma)");
    }

    private void giveChestStick(Player p) {
        Item stick = Item.get("minecraft:stick", 0, 1);
        stick.setCustomName("§6Graveto de Baú §7(BR)");
        CompoundTag tag = stick.getNamedTag() != null ? stick.getNamedTag() : new CompoundTag();
        tag.putBoolean("brChestStick", true);
        stick.setNamedTag(tag);
        p.getInventory().addItem(stick);
        setupMode.put(p.getUniqueId(), "chest");
        showRegisteredChests(true);
        p.sendMessage(TextFormat.GREEN + "Graveto recebido! §7Quebre um bloco para registrar um baú ACIMA dele. §eInteraja para configurar.");
    }

    private void giveFlyingChestStick(Player p) {
        Item stick = Item.get("minecraft:stick", 0, 1);
        stick.setCustomName("§6Graveto de Baú Voador §7(BR)");
        CompoundTag tag = stick.getNamedTag() != null ? stick.getNamedTag() : new CompoundTag();
        tag.putBoolean("flyingChestStick", true);
        stick.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Esquerda: selecionar posição");
        lore.add("§7Direita: abrir menu");
        stick.setLore(lore.toArray(new String[0]));
        p.getInventory().addItem(stick);
        p.sendMessage(TextFormat.GREEN + "Graveto de Baú Voador recebido!");
        p.sendMessage(TextFormat.GRAY + "Quebre um bloco para selecionar posição, depois clique direito no graveto.");
    }

    private void joinLobby(Player player) {
        if (gameState == GameState.ENDING) {
            player.sendMessage(TextFormat.RED + "Partida finalizando! Aguarde.");
            return;
        }
        ensurePvpWorldLoaded();
        if (lobbySpawn == null) {
            player.sendMessage(TextFormat.RED + "Spawn do lobby não configurado! Admin: /pvp setlobbyspawn"); return;
        }
        UUID uuid = player.getUniqueId();
        lobbyPlayers.remove(uuid); alivePlayers.remove(uuid); spectatorPlayers.remove(uuid);

        player.teleport(lobbySpawn);

        if (gameState == GameState.ACTIVE || gameState == GameState.STARTING) {
            lobbyPlayers.add(uuid);
            player.setGamemode(Player.CREATIVE);
            player.getInventory().clearAll();
            setSpectatorItems(player, false);
            player.addEffect(Effect.get(EffectType.INVISIBILITY).setDuration(36000 * 20).setAmbient(true));
            player.sendMessage(TextFormat.YELLOW + "Você entrou no lobby como espectador!");
            broadcastAll(TextFormat.YELLOW + player.getName() + " entrou no lobby como espectador!");
            updateActionBar();
            return;
        }

        lobbyPlayers.add(uuid);
        player.setGamemode(Player.SURVIVAL);
        player.getInventory().clearAll();
        player.getInventory().setItem(8, makeDye("§cSair do Lobby", "exitLobby", false));

        if (gameState == GameState.IDLE) { gameState = GameState.LOBBY; startActionBarTask(); }

        broadcastAll(TextFormat.YELLOW + player.getName() + " entrou! §7(" + lobbyPlayers.size() + "/" + MIN_PLAYERS + ")");
        if (lobbyPlayers.size() >= MIN_PLAYERS && gameState == GameState.LOBBY) startCountdown();
        updateActionBar();
    }

    private void leaveLobby(Player player) {
        UUID uuid = player.getUniqueId();
        lobbyPlayers.remove(uuid);
        player.getInventory().clearAll();
        player.setGamemode(Player.ADVENTURE);
        teleportMainLobby(player);
        if (gameState == GameState.STARTING && lobbyPlayers.size() < MIN_PLAYERS) cancelCountdown();
        if (lobbyPlayers.isEmpty() && gameState == GameState.LOBBY) { gameState = GameState.IDLE; cancelAllTasks(); }
        updateActionBar();
    }

    private void startCountdown() {
        if (countdownTask != null) return;
        gameState = GameState.STARTING;
        countdownVal = COUNTDOWN_SECS;

        countdownTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            if (lobbyPlayers.size() < MIN_PLAYERS) { cancelCountdown(); return; }
            if (countdownVal <= 0) { startGame(); return; }

            String color = countdownVal <= 3 ? "§c§l" : (countdownVal <= 10 ? "§e§l" : "§a§l");
            String cstr  = color + countdownVal;

            for (UUID uid : new ArrayList<>(lobbyPlayers)) {
                Player p = getPlayer(uid);
                if (p == null) continue;
                p.sendTitle("§6§lBATTLE ROYALE", "§fComeça em: " + cstr, 0, 25, 5);
                playNoteSound(p, countdownVal);
            }
            countdownVal--;
        }, 20);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        gameState = GameState.LOBBY;
        countdownVal = COUNTDOWN_SECS;
        for (UUID uid : lobbyPlayers) { Player p = getPlayer(uid); if (p != null) p.sendTitle("", "", 0, 1, 0); }
        broadcastLobby(TextFormat.RED + "Contagem cancelada. Aguardando jogadores (" + lobbyPlayers.size() + "/" + MIN_PLAYERS + ")");
    }

    private void startGame() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        gameState = GameState.ACTIVE;
        pvpAllowed = false;
        flyingChestsThisMatch = 0;

        alivePlayers.clear();
        alivePlayers.addAll(lobbyPlayers);
        lobbyPlayers.clear();

        for (UUID uid : new ArrayList<>(alivePlayers)) {
            Player p = getPlayer(uid);
            if (p == null) { alivePlayers.remove(uid); continue; }
            p.getInventory().clearAll();
            p.setGamemode(Player.SURVIVAL);
            p.sendTitle("§c§lBATTLE ROYALE!", "§eBoa sorte! O vidro vai ceder...", 10, 50, 10);
            p.addEffect(Effect.get(EffectType.SPEED).setDuration(15 * 20).setAmplifier(1));
            p.addEffect(Effect.get(EffectType.SLOW_FALLING).setDuration(15 * 20).setAmplifier(0));
        }

        Level pvpWorld = getServer().getLevelByName(pvpWorldName);
        if (pvpWorld != null && floorMin != null) saveAndBreakFloor(pvpWorld);

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            Level w = getServer().getLevelByName(pvpWorldName);
            if (w != null) restoreFloor(w);
        }, FLOOR_RESTORE_TICKS);

        getServer().getScheduler().scheduleDelayedTask(this, () -> pvpAllowed = true, PVP_GRACE_TICKS);

        spawnMapChests();
        startParticleTask();
        startDamageTask();

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (gameState == GameState.ACTIVE) startGas();
        }, GAS_START_TICKS);

        for (int i = 0; i < MAX_FLYING_CHESTS; i++) {
            final int delay = GAS_START_TICKS + ((i + 1) * 30 * 20);
            getServer().getScheduler().scheduleDelayedTask(this, this::spawnFlyingChest, delay);
        }

        updateActionBar();
    }

    private void saveAndBreakFloor(Level level) {
        savedFloor.clear();
        int x1=(int)Math.min(floorMin.x,floorMax.x), x2=(int)Math.max(floorMin.x,floorMax.x);
        int y1=(int)Math.min(floorMin.y,floorMax.y), y2=(int)Math.max(floorMin.y,floorMax.y);
        int z1=(int)Math.min(floorMin.z,floorMax.z), z2=(int)Math.max(floorMin.z,floorMax.z);
        for (int x=x1;x<=x2;x++) for (int y=y1;y<=y2;y++) for (int z=z1;z<=z2;z++) {
            Block b = level.getBlock(x,y,z).clone();
            if (!b.getId().equals("minecraft:air")) {
                savedFloor.put(x+","+y+","+z, b);
                level.setBlock(x,y,z, Block.get("minecraft:air"),true,true);
            }
        }
    }

    private void restoreFloor(Level level) {
        for (Map.Entry<String,Block> e : savedFloor.entrySet()) {
            String[] c=e.getKey().split(",");
            level.setBlock(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]),e.getValue(),true,true);
        }
    }

    private void startGas() {
        if (gasMin == null || gasMax == null) return;
        gasActive = true;
        gasCenterX = (gasMin.x + gasMax.x) / 2.0;
        gasCenterZ = (gasMin.z + gasMax.z) / 2.0;
        double halfW = Math.abs(gasMax.x - gasMin.x) / 2.0;
        double halfD = Math.abs(gasMax.z - gasMin.z) / 2.0;
        gasMaxRadius = Math.min(halfW, halfD);
        gasRadius = gasMaxRadius;
        broadcastAll(TextFormat.RED + "O gás está começando a fechar!");
        gasTask = getServer().getScheduler().scheduleRepeatingTask(this, this::shrinkGas, GAS_INTERVAL_TICKS);
    }

    private void shrinkGas() {
        if (gasRadius <= 3.0) return;
        gasRadius = Math.max(3.0, gasRadius - GAS_SHRINK);
        broadcastAll(TextFormat.RED + "O gás está se fechando!");
    }

    private void startParticleTask() {
        if (particleTask != null) return;
        particleTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            Level w = getServer().getLevelByName(pvpWorldName);
            if (w == null) return;
            if (gasActive) drawGasBorder(w);
            drawChestAuras(w);
            if (gasActive) checkGasChestBreak(w);
        }, PARTICLE_TICKS);
    }

    private void drawGasBorder(Level level) {
        if (gasRadius <= 2.0) return;
        double baseY = gasMin != null ? Math.min(gasMin.y,gasMax.y) : 64;
        int numPoints = Math.max(12, (int)(gasRadius * 1.5));
        double angleStep = 2.0 * Math.PI / numPoints;

        for (int i = 0; i < numPoints; i++) {
            double angle = i * angleStep;
            double bx = gasCenterX + Math.cos(angle) * gasRadius;
            double bz = gasCenterZ + Math.sin(angle) * gasRadius;
            for (int yo = 0; yo < 4; yo++) {
                level.addParticle(new HugeExplodeParticle(new Vector3(bx, baseY + yo, bz)));
            }
        }
    }

    private void drawChestAuras(Level level) {
        for (String key : activeChests) {
            Vector3 pos = posFromKey(key);
            ChestType t = chestTypes.getOrDefault(key, ChestType.NORMAL);
            if (t == ChestType.EPIC) {
                level.addParticle(new RedstoneParticle(pos.add(0.5, 1.5, 0.5), 5));
            } else if (t == ChestType.LEGENDARY) {
                level.addParticle(new HugeExplodeParticle(pos.add(0.5, 1.0, 0.5)));
                level.addParticle(new HugeExplodeParticle(pos.add(0.5, 1.5, 0.5)));
            }
        }
        for (String key : deathChestKeys) {
            Vector3 pos = posFromKey(key);
            level.addParticle(new RedstoneParticle(pos.add(0.5, 1.2, 0.5), 5));
        }
    }

    private void startDamageTask() {
        if (damageTask != null) return;
        damageTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            if (!gasActive) return;
            long now = System.currentTimeMillis();
            for (UUID uid : new ArrayList<>(alivePlayers)) {
                Player p = getPlayer(uid);
                if (p == null) continue;
                double dx = p.getX() - gasCenterX;
                double dz = p.getZ() - gasCenterZ;
                boolean inGas = Math.sqrt(dx * dx + dz * dz) > gasRadius;
                if (inGas) {
                    p.addEffect(Effect.get(EffectType.BLINDNESS).setDuration(40).setAmplifier(0));
                    p.addEffect(Effect.get(EffectType.SLOWNESS).setDuration(60).setAmplifier(1));
                    int lvl = gasDmgLevel.getOrDefault(uid, 0);
                    Long last = lastGasDmg.get(uid);
                    if (last != null && (now - last) >= 1500) {
                        lvl = Math.min(lvl + 1, 8);
                        gasDmgLevel.put(uid, lvl);
                    }
                    double dmg = 2 + lvl * 2;
                    p.attack(new cn.nukkit.event.entity.EntityDamageEvent(p, cn.nukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC, (float) dmg));
                    lastGasDmg.put(uid, now);
                    if (!gasDmgLevel.containsKey(uid)) gasDmgLevel.put(uid, 0);
                } else {
                    gasDmgLevel.remove(uid);
                    lastGasDmg.remove(uid);
                }
            }
        }, DAMAGE_TICKS);
    }

    private void spawnMapChests() {
        activeChests.clear(); chestTypes.clear(); gasChestTimers.clear();
        Level w = getServer().getLevelByName(pvpWorldName);
        if (w == null) return;
        Random rand = new Random();
        for (ChestEntry e : registeredChests) {
            int bx=(int)e.pos.x, by=(int)e.pos.y, bz=(int)e.pos.z;
            if (e.type == ChestType.NONE || rand.nextInt(100) >= e.chance) {
                w.setBlock(bx,by,bz, Block.get("minecraft:air"),true,true);
                continue;
            }
            ChestType finalType = e.type;
            if (e.type == ChestType.RANDOM) {
                finalType = pickRandomChestType(rand);
            }
            w.setBlock(bx,by,bz, Block.get("minecraft:chest"),true,true);
            String key = posKey(e.pos);
            activeChests.add(key);
            chestTypes.put(key, finalType);
            final ChestType type = finalType;
            final Vector3 pos = e.pos.clone();
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                BlockEntity be = w.getBlockEntity(pos);
                if (be instanceof BlockEntityChest) fillChest((BlockEntityChest) be, type);
            }, 2);
        }
    }

    private void fillChest(BlockEntityChest chest, ChestType type) {
        for (Item item : makeLoot(type)) chest.getInventory().addItem(item);
    }

    private List<Item> makeLoot(ChestType type) {
        Random r = new Random();
        List<Item> loot = new ArrayList<>();
        switch (type) {
            case TRASH:
                if (r.nextBoolean()) loot.add(Item.get("minecraft:stick", 0, 1 + r.nextInt(3)));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:dried_kelp", 0, 1 + r.nextInt(3)));
                if (r.nextInt(10) == 0) loot.add(createToxicHat());
                if (r.nextInt(12) == 0) loot.add(createKnockbackStick(1));
                if (r.nextInt(20) == 0) loot.add(createReverseStone(1));
                break;
            case NORMAL:
                loot.add(Item.get("minecraft:wooden_sword", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:arrow", 0, 8 + r.nextInt(9)));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:leather_helmet", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:leather_chestplate", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:leather_leggings", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:leather_boots", 0, 1));
                loot.add(createKnockbackStick(1));
                if (r.nextInt(3) == 0) loot.add(createKnockbackStick(2));
                if (r.nextInt(5) == 0) loot.add(createReverseStone(1));
                break;
            case GOOD:
                loot.add(Item.get("minecraft:stone_sword", 0, 1));
                loot.add(Item.get("minecraft:bow", 0, 1));
                loot.add(Item.get("minecraft:arrow", 0, 32 + r.nextInt(33)));
                loot.add(Item.get("minecraft:bread", 0, 16 + r.nextInt(17)));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:iron_helmet", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:iron_chestplate", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:iron_leggings", 0, 1));
                loot.add(createSpeedShard(1));
                if (r.nextInt(12) == 0) loot.add(Item.get("minecraft:ender_pearl", 0, 1));
                if (r.nextInt(4) == 0) loot.add(createDashShard(1));
                loot.add(createKnockbackStick(1));
                loot.add(createKnockbackStick(2));
                if (r.nextInt(4) == 0) loot.add(createKnockbackStick(3));
                if (r.nextInt(3) == 0) loot.add(createReverseStone(1));
                if (r.nextInt(10) == 0) loot.add(createReverseStone(2));
                if (r.nextInt(3) == 0) loot.add(createProjectileShield());
                break;
            case EPIC:
                loot.add(r.nextInt(100) < 40 ? Item.get("minecraft:diamond_sword", 0, 1) : Item.get("minecraft:iron_sword", 0, 1));
                loot.add(Item.get("minecraft:golden_apple", 0, 8 + r.nextInt(9)));
                loot.add(Item.get("minecraft:golden_carrot", 0, 32 + r.nextInt(33)));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:iron_helmet", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:iron_chestplate", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:iron_leggings", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:diamond_helmet", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:diamond_chestplate", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:totem_of_undying", 0, 1));
                loot.add(createSpeedShard(1 + r.nextInt(5)));
                if (r.nextInt(5) == 0) loot.add(Item.get("minecraft:ender_pearl", 0, 1));
                loot.add(createDashShard(1 + r.nextInt(3)));
                loot.add(createKnockbackStick(2));
                loot.add(createKnockbackStick(3));
                if (r.nextInt(5) == 0) loot.add(createKnockbackStick(4));
                if (r.nextInt(5) < 2) loot.add(createReverseStone(2));
                if (r.nextInt(5) < 2) loot.add(createReverseStone(3));
                if (r.nextInt(10) < 3) loot.add(createProjectileShield());
                break;
            case LEGENDARY:
                Item legSword = r.nextInt(100) < 40 ? Item.get("minecraft:netherite_sword", 0, 1) : Item.get("minecraft:diamond_sword", 0, 1);
                loot.add(legSword);
                if (r.nextBoolean()) loot.add(Item.get("minecraft:diamond_helmet", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:diamond_chestplate", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:diamond_leggings", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:diamond_boots", 0, 1));
                if (r.nextBoolean()) loot.add(Item.get("minecraft:netherite_helmet", 0, 1));
                if (r.nextInt(3) == 0) loot.add(Item.get("minecraft:netherite_chestplate", 0, 1));
                if (r.nextInt(100) < 40) loot.add(Item.get("minecraft:netherite_boots", 0, 1));
                loot.add(Item.get("minecraft:golden_apple", 0, 32 + r.nextInt(33)));
                if (r.nextInt(100) < 40) loot.add(Item.get("minecraft:enchanted_golden_apple", 0, 1));
                if (r.nextInt(100) < 70) loot.add(Item.get("minecraft:enchanted_golden_apple", 0, 2));
                loot.add(Item.get("minecraft:golden_carrot", 0, 64 + r.nextInt(65)));
                if (r.nextInt(5) < 3) loot.add(Item.get("minecraft:totem_of_undying", 0, 1 + r.nextInt(3)));
                if (r.nextInt(100) < 90) loot.add(createSuperShock());
                loot.add(createSpeedShard(1 + r.nextInt(15)));
                loot.add(Item.get("minecraft:ender_pearl", 0, 1 + r.nextInt(4)));
                loot.add(createDashShard(1 + r.nextInt(5)));
                loot.add(createKnockbackStick(3));
                loot.add(createKnockbackStick(4));
                loot.add(createReverseStone(3));
                loot.add(createReverseStone(4));
                if (r.nextInt(10) < 7) loot.add(createProjectileShield());
                break;
            default: break;
        }
        return loot;
    }

    private Item createSuperShock() {
        Item rod = Item.get("minecraft:fishing_rod", 0, 1);
        rod.setCustomName("§d§lSuper Choque");
        rod.addEnchantment(Enchantment.get(Enchantment.ID_DURABILITY).setLevel(3));
        rod.addEnchantment(Enchantment.get(Enchantment.ID_MENDING).setLevel(1));
        CompoundTag tag = rod.getNamedTag() != null ? rod.getNamedTag() : new CompoundTag();
        tag.putBoolean("superShock", true);
        tag.putString("ench", "super_shock");
        rod.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§5§oUm raio poderoso atinge seus inimigos!");
        lore.add("§c§lGrande dano à distância!");
        rod.setLore(lore.toArray(new String[0]));
        return rod;
    }

    private Item createSpeedShard(int count) {
        Item shard = Item.get("minecraft:amethyst_shard", 0, count);
        shard.setCustomName("§b§lSpeed Shard");
        CompoundTag tag = shard.getNamedTag() != null ? shard.getNamedTag() : new CompoundTag();
        tag.putBoolean("speedShard", true);
        shard.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Use para comer.");
        lore.add("§aCura 4 corações");
        lore.add("§bVelocidade II por 2s");
        shard.setLore(lore.toArray(new String[0]));
        return shard;
    }

    private Item createDashShard(int count) {
        Item rod = Item.get("minecraft:blaze_rod", 0, count);
        rod.setCustomName("§c§lDash Shard");
        CompoundTag tag = rod.getNamedTag() != null ? rod.getNamedTag() : new CompoundTag();
        tag.putBoolean("dashShard", true);
        rod.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Dash para frente!");
        lore.add("§cImortal por 0.5s (gas ainda causa dano)");
        lore.add("§eCooldown: 3 segundos");
        rod.setLore(lore.toArray(new String[0]));
        return rod;
    }

    private Item createKnockbackStick(int tier) {
        if (tier < 1 || tier > 4) tier = 1;
        String[] names = {"§a§lKnockback Stick", "§b§lKnockback Stick+", "§d§lKnockback Stick++", "§6§lKnockback Stick MAX"};
        int[] cooldowns = {2000, 1500, 1000, 500};
        double[] force = {1.2, 1.8, 2.5, 3.5};
        double[] upForce = {0.2, 0.3, 0.4, 0.6};

        Item stick = Item.get("minecraft:stick", 0, 1);
        stick.setCustomName(names[tier - 1]);
        CompoundTag tag = stick.getNamedTag() != null ? stick.getNamedTag() : new CompoundTag();
        tag.putBoolean("knockbackStick", true);
        tag.putInt("kbTier", tier);
        stick.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Força: " + force[tier-1]);
        lore.add("§eCooldown: " + (cooldowns[tier-1] / 1000.0) + "s");
        stick.setLore(lore.toArray(new String[0]));
        return stick;
    }

    private Item createReverseStone(int tier) {
        if (tier < 1 || tier > 4) tier = 1;
        String[] names = {"§a§lReverse Stone", "§b§lReverse Stone+", "§d§lReverse Stone++", "§6§lReverse Stone MAX"};
        int[] durations = {15, 12, 10, 8};
        double[] multipliers = {1.2, 1.0, 0.8, 0.6};

        Item btn = Item.get("minecraft:stone_button", 0, 1);
        btn.setCustomName(names[tier - 1]);
        CompoundTag tag = btn.getNamedTag() != null ? btn.getNamedTag() : new CompoundTag();
        tag.putBoolean("reverseStone", true);
        tag.putInt("revTier", tier);
        btn.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Reflete knockback por " + durations[tier-1] + "s");
        lore.add("§7Multiplicador: x" + multipliers[tier-1]);
        btn.setLore(lore.toArray(new String[0]));
        return btn;
    }

    private Item createProjectileShield() {
        Item shield = Item.get("minecraft:nether_star", 0, 1);
        shield.setCustomName("§b§lProjectile Shield");
        CompoundTag tag = shield.getNamedTag() != null ? shield.getNamedTag() : new CompoundTag();
        tag.putBoolean("projectileShield", true);
        shield.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Ativa escudo anti-projétil por 10s");
        lore.add("§7Imortal a flechas, tridentes e ender pearls");
        shield.setLore(lore.toArray(new String[0]));
        return shield;
    }

    private Item createToxicHat() {
        Item hat = Item.get("minecraft:netherite_helmet", 0, 1);
        hat.setCustomName("§a§lToxic Hat");
        CompoundTag tag = hat.getNamedTag() != null ? hat.getNamedTag() : new CompoundTag();
        tag.putBoolean("toxicHat", true);
        hat.setNamedTag(tag);
        List<String> lore = new ArrayList<>();
        lore.add("§7Acertar players envenena por 1s.");
        lore.add("§7Acumula até 5 segundos!");
        hat.setLore(lore.toArray(new String[0]));
        return hat;
    }

    private void spawnFlyingChest() {
        if (flyingChestsThisMatch >= MAX_FLYING_CHESTS) return;
        if (flyingChestAreas.isEmpty()) return;

        Level w = getServer().getLevelByName(pvpWorldName);
        if (w == null) return;

        Random rand = new Random();
        List<String> areas = new ArrayList<>(flyingChestAreas);

        for (int attempt = 0; attempt < 10; attempt++) {
            String area = areas.get(rand.nextInt(areas.size()));
            Vector3 targetPos = posFromKey(area);

            if (gasActive) {
                double dx = targetPos.x - gasCenterX;
                double dz = targetPos.z - gasCenterZ;
                if (Math.sqrt(dx * dx + dz * dz) <= gasRadius) continue;
            }

            int landingY = (int) targetPos.y;
            int spawnY = landingY + 10;
            flyingChestsThisMatch++;

            w.setBlock((int) targetPos.x, spawnY, (int) targetPos.z, Block.get("minecraft:chest"), true, true);

            Entity fireworks = Entity.createEntity("minecraft:fireworks", new Location(targetPos.x, spawnY + 1, targetPos.z, w));
            if (fireworks != null) fireworks.spawnToAll();

            broadcastAll(TextFormat.AQUA + "Um baú voador está caindo no mapa!");

            final int[] currentY = {spawnY};
            final String chestKey = posKey(new Vector3((int) targetPos.x, landingY, (int) targetPos.z));
            final Vector3 landPos = new Vector3((int) targetPos.x, landingY, (int) targetPos.z);

            TaskHandler task = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
                if (currentY[0] <= landingY) {
                    TaskHandler self = flyingChestTasks.remove(chestKey);
                    if (self != null) self.cancel();

                    for (int i = 0; i < 3; i++) {
                        w.addParticle(new HugeExplodeParticle(new Vector3(landPos.x + 0.5, landPos.y + 0.5 + i * 0.5, landPos.z + 0.5)));
                    }
                    playExplosionSound(w, landPos);

                    BlockEntity be = w.getBlockEntity(landPos);
                    if (be instanceof BlockEntityChest) {
                        ChestType lootType;
                        do { lootType = pickRandomChestType(new Random()); } while (lootType == ChestType.NONE);
                        fillChest((BlockEntityChest) be, lootType);
                        chestTypes.put(chestKey, lootType);
                    }
                    activeChests.add(chestKey);
                    activeFlyingChests.add(chestKey);
                    return;
                }

                w.setBlock((int) targetPos.x, currentY[0], (int) targetPos.z, Block.get("minecraft:air"), true, true);
                currentY[0]--;
                w.setBlock((int) targetPos.x, currentY[0], (int) targetPos.z, Block.get("minecraft:chest"), true, true);
                w.addParticle(new RedstoneParticle(new Vector3(targetPos.x + 0.5, currentY[0] + 1, targetPos.z + 0.5), 5));
            }, 4);

            flyingChestTasks.put(chestKey, task);
            return;
        }
    }

    private void openFlyingChestForm(Player player) {
        Vector3 pos = flyingChestSetupPos.get(player.getUniqueId());
        if (pos == null) {
            player.sendMessage(TextFormat.RED + "Selecione uma posição primeiro (quebre um bloco com o graveto).");
            return;
        }

        String posStr = "(" + (int)pos.x + "," + (int)pos.y + "," + (int)pos.z + ")";
        new SimpleForm("§6Baús Voadores", "Posição: " + posStr)
            .addButton("Definir como área disponível", p -> {
                String key = posKey(pos);
                flyingChestAreas.add(key);
                saveBrConfig();
                p.sendMessage(TextFormat.GREEN + "Área de baú voador definida em " + posStr + "!");
                flyingChestSetupPos.remove(p.getUniqueId());
            })
            .addButton("Remover área disponível", p -> {
                String key = posKey(pos);
                if (flyingChestAreas.remove(key)) {
                    saveBrConfig();
                    p.sendMessage(TextFormat.RED + "Área removida!");
                } else {
                    p.sendMessage(TextFormat.YELLOW + "Nenhuma área encontrada nesta posição.");
                }
                flyingChestSetupPos.remove(p.getUniqueId());
            })
            .addButton("Fechar modo", p -> {
                flyingChestSetupPos.remove(p.getUniqueId());
                p.sendMessage(TextFormat.YELLOW + "Modo de baú voador finalizado.");
            })
            .send(player);
    }

    private void checkGasChestBreak(Level level) {
        List<String> remove = new ArrayList<>();
        for (String key : new ArrayList<>(activeChests)) {
            Vector3 pos = posFromKey(key);
            double dx = pos.x - gasCenterX + 0.5;
            double dz = pos.z - gasCenterZ + 0.5;
            boolean inGas = Math.sqrt(dx * dx + dz * dz) > gasRadius;
            if (inGas) {
                Long entered = gasChestTimers.computeIfAbsent(key, k -> System.currentTimeMillis());
                if (System.currentTimeMillis() - entered >= 5000) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof BlockEntityChest)
                        for (Item item : ((BlockEntityChest)be).getInventory().getContents().values())
                            level.dropItem(pos.add(0.5,0.5,0.5), item);
                    level.setBlock((int)pos.x,(int)pos.y,(int)pos.z, Block.get("minecraft:air"),true,true);
                    remove.add(key); gasChestTimers.remove(key);
                }
            } else {
                gasChestTimers.remove(key);
            }
        }
        activeChests.removeAll(remove);
        activeFlyingChests.removeAll(remove);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.getLevel().getFolderName().equals(pvpWorldName)) return;
        if (!alivePlayers.contains(player.getUniqueId())) return;

        List<Item> saved = new ArrayList<>();
        for (Item item : player.getInventory().getContents().values())
            if (item != null && item.getCount() > 0) saved.add(item.clone());

        event.setDrops(new Item[0]);
        event.setDeathMessage("");
        event.setKeepInventory(true);

        alivePlayers.remove(player.getUniqueId());
        pendingSpectator.put(player.getUniqueId(), saved);
        deathLocations.put(player.getUniqueId(), player.getLocation().clone());

        spawnDeathChest(player.getLocation(), player.getLevel(), saved);
        broadcastAll(TextFormat.RED + "☠ " + player.getName() + " foi eliminado! §7(" + alivePlayers.size() + " restantes)");
        checkWinCondition();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<Item> savedItems = pendingSpectator.remove(player.getUniqueId());
        if (savedItems == null) return;

        Location dloc = deathLocations.remove(player.getUniqueId());
        Location respawnPos = dloc != null ? dloc : (lobbySpawn != null ? lobbySpawn : player.getLevel().getSpawnLocation().getLocation());
        event.setRespawnPosition(it.unimi.dsi.fastutil.Pair.of((cn.nukkit.level.Position) respawnPos, SpawnPointType.WORLD));

        spectatorPlayers.add(player.getUniqueId());

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            player.getInventory().clearAll();
            player.setGamemode(Player.CREATIVE);
            setSpectatorItems(player, false);
            player.addEffect(Effect.get(EffectType.INVISIBILITY).setDuration(36000 * 20).setAmbient(true));
            player.sendTitle("§c§lELIMINADO!", "§7Modo criativo - assista a partida!", 10, 40, 10);
        }, 2);
    }

    private void spawnDeathChest(Location deathLoc, Level level, List<Item> items) {
        int x=(int)deathLoc.x, y=(int)deathLoc.y, z=(int)deathLoc.z;

        for (int i=0; i<5; i++) { if (level.getBlock(x,y,z).getId().equals("minecraft:air")) break; y++; }

        Block below = level.getBlock(x, y-1, z);
        if (below.getId().equals("minecraft:air")) {
            for (int dy=0; dy<25; dy++) {
                if (!level.getBlock(x,y-dy-1,z).getId().equals("minecraft:air")) { y = y-dy; break; }
            }
        }

        final int fx=x, fy=y, fz=z;
        level.setBlock(fx,fy,fz, Block.get("minecraft:chest"),true,true);
        String key = fx+","+fy+","+fz;
        deathChestKeys.add(key);

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            BlockEntity be = level.getBlockEntity(new Vector3(fx,fy,fz));
            if (be instanceof BlockEntityChest && !items.isEmpty())
                for (Item it : items) ((BlockEntityChest)be).getInventory().addItem(it);
        }, 2);
    }

    private void checkWinCondition() {
        List<UUID> alive = alivePlayers.stream()
            .filter(uid -> getPlayer(uid) != null)
            .collect(Collectors.toList());

        if (alive.size() > 1) return;

        gameState = GameState.ENDING;
        cancelAllTasks();

        UUID winnerUid = alive.isEmpty() ? null : alive.get(0);
        Player winner = winnerUid != null ? getPlayer(winnerUid) : null;

        if (winner != null) {
            alivePlayers.remove(winnerUid);
            spectatorPlayers.add(winnerUid);
            winner.getInventory().clearAll();
            winner.setGamemode(Player.CREATIVE);
            setSpectatorItems(winner, true);
            winner.addEffect(Effect.get(EffectType.INVISIBILITY).setDuration(36000 * 20).setAmbient(true));
            winner.sendTitle("§6§lVITÓRIA!", "§eVocê venceu o Battle Royale!", 10, 80, 20);
            broadcastAll("§6§l" + winner.getName() + " venceu o Battle Royale!");
        } else {
            broadcastAll("§7Empate! Ninguém sobreviveu.");
        }

        getServer().getScheduler().scheduleDelayedTask(this, this::resetAndReturn, WINNER_SPEC_TICKS);
    }

    private void resetAndReturn() {
        Level pvpWorld = getServer().getLevelByName(pvpWorldName);

        for (UUID uid : new ArrayList<>(spectatorPlayers)) {
            Player p = getPlayer(uid);
            if (p != null) {
                p.setGamemode(Player.ADVENTURE);
                p.getInventory().clearAll();
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFood(20);
                for (var effect : p.getEffects().values()) p.removeEffect(effect.getType());
                teleportMainLobby(p);
            }
        }
        for (UUID uid : new ArrayList<>(alivePlayers)) {
            Player p = getPlayer(uid);
            if (p != null) {
                p.setGamemode(Player.ADVENTURE);
                p.getInventory().clearAll();
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFood(20);
                for (var effect : p.getEffects().values()) p.removeEffect(effect.getType());
                teleportMainLobby(p);
            }
        }
        for (UUID uid : new ArrayList<>(lobbyPlayers)) {
            Player p = getPlayer(uid);
            if (p != null) {
                p.setGamemode(Player.ADVENTURE);
                p.getInventory().clearAll();
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFood(20);
                for (var effect : p.getEffects().values()) p.removeEffect(effect.getType());
                teleportMainLobby(p);
            }
        }

        cleanWorldItems();
        if (pvpWorld != null && !savedFloor.isEmpty()) restoreFloor(pvpWorld);

        spectatorPlayers.clear(); alivePlayers.clear(); lobbyPlayers.clear();
        savedFloor.clear(); activeChests.clear(); chestTypes.clear();
        gasChestTimers.clear(); gasDmgLevel.clear(); lastGasDmg.clear();
        deathChestKeys.clear(); deathLocations.clear(); superShockCooldown.clear();
        speedShardCooldown.clear();         dashShardCooldown.clear(); knockbackStickCooldown.clear();
        dashImmortal.clear(); toxicHatPoison.clear();
        projectileShieldActive.clear(); reverseStoneActive.clear(); reverseStoneTier.clear();
        activeFlyingChests.clear(); flyingChestsThisMatch = 0;
        gasActive = false; pvpAllowed = false;
        gameState = GameState.IDLE;
    }

    private void cleanWorldItems() {
        Level pvpWorld = getServer().getLevelByName(pvpWorldName);
        if (pvpWorld == null) return;
        for (cn.nukkit.entity.Entity e : pvpWorld.getEntities())
            if (e instanceof cn.nukkit.entity.item.EntityItem) e.kill();
        Set<String> allChests = new HashSet<>(activeChests);
        allChests.addAll(deathChestKeys);
        for (String key : allChests) {
            Vector3 pos = posFromKey(key);
            pvpWorld.setBlock((int)pos.x,(int)pos.y,(int)pos.z, Block.get("minecraft:air"),true,true);
        }
        for (String key : activeFlyingChests) {
            Vector3 pos = posFromKey(key);
            pvpWorld.setBlock((int)pos.x,(int)pos.y,(int)pos.z, Block.get("minecraft:air"),true,true);
        }
        for (ChestEntry e : registeredChests) {
            pvpWorld.setBlock((int)e.pos.x, (int)e.pos.y, (int)e.pos.z, Block.get("minecraft:air"), true, true);
        }
    }

    private void setSpectatorItems(Player player, boolean isWinner) {
        player.getInventory().clearAll();
        player.getInventory().setItem(8, makeDye("§cSair", "exitGame", false));
        if (isWinner) {
            player.getInventory().setItem(0, makeDye("§aJogar Denovo", "playAgain", true));
        } else {
            Item clock = Item.get("minecraft:clock", 0, 1);
            clock.setCustomName("§eEspectadores");
            CompoundTag t = clock.getNamedTag() != null ? clock.getNamedTag() : new CompoundTag();
            t.putBoolean("spectClock", true);
            clock.setNamedTag(t);
            player.getInventory().setItem(0, clock);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Level level = player.getLevel();
        UUID uid = player.getUniqueId();

        if (player.isOp() && level.getFolderName().equals(pvpWorldName)) {
            Item held = player.getInventory().getItemInHand();
            if (held != null && held.getNamedTag() != null && held.getNamedTag().contains("flyingChestStick")) {
                event.setCancelled(true);
                Block block = event.getBlock();
                flyingChestSetupPos.put(uid, new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                player.sendMessage(TextFormat.GREEN + "Posição selecionada: " + fmtPos(block) + " §7(Clique direito no graveto)");
                return;
            }
        }

        String mode = setupMode.get(uid);
        if (mode != null && player.isOp() && level.getFolderName().equals(pvpWorldName)) {
            event.setCancelled(true);
            Block block = event.getBlock();
            if (mode.equals("chest")) {
                Item held = player.getInventory().getItemInHand();
                if (isBrStick(held)) {
                    int cx = block.getFloorX();
                    int cy = block.getFloorY() + 1;
                    int cz = block.getFloorZ();
                    level.setBlock(cx, cy, cz, Block.get("minecraft:chest"), true, true);
                    registerChest(player, new Vector3(cx, cy, cz), level);
                } else {
                    if (block.getId().contains("chest") || block.getId().contains("barrel")) {
                        Vector3 pos = new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ());
                        String key = posKey(pos);
                        ChestEntry entry = registeredChests.stream().filter(e -> posKey(e.pos).equals(key)).findFirst().orElse(null);
                        if (entry != null) {
                            registeredChests.remove(entry);
                            saveBrConfig();
                            level.setBlock(block.getFloorX(), block.getFloorY(), block.getFloorZ(), Block.get("minecraft:air"), true, true);
                            player.sendMessage(TextFormat.RED + "✓ Baú removido do registro!");
                        } else {
                            player.sendMessage(TextFormat.YELLOW + "Este baú não está registrado.");
                        }
                    } else {
                        player.sendMessage(TextFormat.YELLOW + "Você está no modo de edição de baús. Quebre um baú sem a vara para removê-lo.");
                    }
                }
            } else {
                setupPos1.put(uid, new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                player.sendMessage(TextFormat.GREEN + "Pos1 definida: " + fmtPos(block));
            }
            return;
        }

        if (!level.getFolderName().equals(pvpWorldName)) return;
        if (lobbyPlayers.contains(uid) || gameState == GameState.STARTING ||
            alivePlayers.contains(uid) || spectatorPlayers.contains(uid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!p.getLevel().getFolderName().equals(pvpWorldName)) return;
        UUID uid = p.getUniqueId();
        if (lobbyPlayers.contains(uid) || alivePlayers.contains(uid) || spectatorPlayers.contains(uid))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (!victim.getLevel().getFolderName().equals(pvpWorldName)) return;
        UUID victimUid = victim.getUniqueId();

        if (lobbyPlayers.contains(victimUid)) { event.setCancelled(true); return; }

        if (spectatorPlayers.contains(victimUid) && event.getCause() != DamageCause.MAGIC) {
            event.setCancelled(true);
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ede = (EntityDamageByEntityEvent) event;

            if (projectileShieldActive.contains(victimUid)) {
                if (ede.getDamager() instanceof EntityArrow || ede.getDamager() instanceof EntityProjectile) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (!(ede.getDamager() instanceof Player)) return;
            Player damager = (Player) ede.getDamager();

            if (spectatorPlayers.contains(damager.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            Item held = damager.getInventory().getItemInHand();
            if (held != null && held.getNamedTag() != null && held.getNamedTag().contains("knockbackStick")) {
                int tier = held.getNamedTag().getInt("kbTier");
                if (tier < 1 || tier > 4) tier = 1;
                long cooldown = new long[]{2000, 1500, 1000, 500}[tier - 1];
                double kbForce = new double[]{1.2, 1.8, 2.5, 3.5}[tier - 1];
                double kbUp = new double[]{0.2, 0.3, 0.4, 0.6}[tier - 1];

                long now = System.currentTimeMillis();
                long lastUse = knockbackStickCooldown.getOrDefault(damager.getUniqueId(), 0L);
                if (now - lastUse < cooldown) {
                    damager.sendMessage(TextFormat.RED + "Knockback Stick em cooldown!");
                    event.setCancelled(true);
                    return;
                }
                knockbackStickCooldown.put(damager.getUniqueId(), now);
                event.setCancelled(true);
                Vector3 knockDir = victim.getLocation().subtract(damager.getLocation()).normalize();
                victim.setMotion(new Vector3(knockDir.x * kbForce, kbUp, knockDir.z * kbForce));
                damager.sendMessage(TextFormat.GREEN + "Knockback Tier " + tier + " em " + victim.getName() + "!");
                playXpSound(damager);

                if (reverseStoneActive.contains(victimUid)) {
                    int revTier = reverseStoneTier.getOrDefault(victimUid, 1);
                    if (revTier < 1 || revTier > 4) revTier = 1;
                    double reflectMult = new double[]{1.2, 1.0, 0.8, 0.6}[revTier - 1];
                    Vector3 reverseDir = damager.getLocation().subtract(victim.getLocation()).normalize();
                    damager.setMotion(new Vector3(reverseDir.x * kbForce * reflectMult, kbUp * reflectMult, reverseDir.z * kbForce * reflectMult));
                    victim.sendMessage(TextFormat.GREEN + "Knockback refletido!");
                    damager.sendMessage(TextFormat.YELLOW + "Knockback refletido por " + victim.getName() + "!");
                }
                return;
            }

            Item helmet = damager.getInventory().getHelmet();
            if (helmet != null && helmet.getNamedTag() != null && helmet.getNamedTag().contains("toxicHat")) {
                int current = toxicHatPoison.getOrDefault(victimUid, 0);
                int newTicks = Math.min(current + 20, 100);
                toxicHatPoison.put(victimUid, newTicks);
                victim.addEffect(Effect.get(EffectType.POISON).setDuration(newTicks).setAmplifier(0));
                victim.sendMessage(TextFormat.GREEN + "Você foi envenenado! (" + (newTicks / 20) + "s)");
                damager.sendMessage(TextFormat.GREEN + victim.getName() + " envenenado por " + (newTicks / 20) + "s!");
            }

            if (dashImmortal.contains(victimUid) && event.getCause() != DamageCause.MAGIC) {
                event.setCancelled(true);
                return;
            }

            if (!pvpAllowed || gameState != GameState.ACTIVE) {
                event.setCancelled(true);
            }
        } else {
            if (dashImmortal.contains(victimUid) && event.getCause() != DamageCause.MAGIC) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFood(PlayerFoodLevelChangeEvent event) {
        Player p = event.getPlayer();
        if (!p.getLevel().getFolderName().equals(pvpWorldName)) return;
        UUID uid = p.getUniqueId();
        if (lobbyPlayers.contains(uid) || gameState == GameState.LOBBY || gameState == GameState.STARTING)
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Level level = player.getLevel();
        Item item = event.getItem();
        UUID uid = player.getUniqueId();

        String mode = setupMode.get(uid);
        if (mode != null && player.isOp() && level.getFolderName().equals(pvpWorldName)) {
            if (checkCooldown(player)) return;
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
                Block block = event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK ? event.getBlock() : null;
                if (mode.equals("chest")) {
                    if (isBrStick(item)) {
                        event.setCancelled(true);
                        if (block != null && (block.getId().contains("chest") || block.getId().contains("barrel"))) {
                            openChestForm(player, new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
                        } else {
                            openAdminStickForm(player);
                        }
                    }
                } else if (block != null) {
                    Vector3 pos1 = setupPos1.get(uid);
                    if (pos1 != null) {
                        event.setCancelled(true);
                        Vector3 pos2 = new Vector3(block.getFloorX(), block.getFloorY(), block.getFloorZ());
                        saveSetup(player, mode, pos1, pos2);
                        setupPos1.remove(uid); setupMode.remove(uid);
                    } else {
                        player.sendMessage(TextFormat.RED + "Defina a Pos1 primeiro (quebre um bloco).");
                    }
                }
            }
            return;
        }

        if (!level.getFolderName().equals(pvpWorldName)) return;
        if (item == null) return;

        if (player.isOp() && item.getNamedTag() != null && item.getNamedTag().contains("flyingChestStick")) {
            event.setCancelled(true);
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                openFlyingChestForm(player);
            }
            return;
        }

        if (spectatorPlayers.contains(uid)) {
            CompoundTag sTag = item.getNamedTag();
            if (sTag == null || (!sTag.contains("exitGame") && !sTag.contains("playAgain") && !sTag.contains("spectClock"))) {
                event.setCancelled(true);
                return;
            }
        }

        CompoundTag tag = item.getNamedTag();
        if (tag != null && tag.contains("speedShard")) {
            event.setCancelled(true);
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                long now = System.currentTimeMillis();
                long lastUse = speedShardCooldown.getOrDefault(uid, 0L);
                if (now - lastUse < 1000) {
                    player.sendMessage(TextFormat.RED + "Speed Shard em cooldown! Aguarde " + String.format("%.1f", (1000 - (now - lastUse)) / 1000.0) + "s.");
                    return;
                }
                speedShardCooldown.put(uid, now);

                float maxHp = player.getMaxHealth();
                float newHp = Math.min(maxHp, player.getHealth() + 8.0f);
                player.setHealth(newHp);

                player.addEffect(Effect.get(EffectType.SPEED).setDuration(2 * 20).setAmplifier(1));
                playEatSound(player);

                int count = item.getCount();
                if (count > 1) {
                    item.setCount(count - 1);
                    player.getInventory().setItemInHand(item);
                } else {
                    player.getInventory().setItemInHand(Item.get("minecraft:air", 0, 0));
                }

                player.sendMessage(TextFormat.AQUA + "Speed Shard! (+4 corações, Velocidade 2s)");
            }
            return;
        }

        if (tag != null && tag.contains("dashShard")) {
            event.setCancelled(true);
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                long now = System.currentTimeMillis();
                long lastUse = dashShardCooldown.getOrDefault(uid, 0L);
                if (now - lastUse < 3000) {
                    player.sendMessage(TextFormat.RED + "Dash Shard em cooldown! Aguarde " + String.format("%.1f", (3000 - (now - lastUse)) / 1000.0) + "s.");
                    return;
                }
                dashShardCooldown.put(uid, now);

                cn.nukkit.math.Vector3 dir = player.getDirectionVector();
                player.setMotion(new cn.nukkit.math.Vector3(dir.getX() * 3.0, 0.5, dir.getZ() * 3.0));

                dashImmortal.add(uid);
                getServer().getScheduler().scheduleDelayedTask(this, () -> dashImmortal.remove(uid), 10);

                int count = item.getCount();
                if (count > 1) {
                    item.setCount(count - 1);
                    player.getInventory().setItemInHand(item);
                } else {
                    player.getInventory().setItemInHand(Item.get("minecraft:air", 0, 0));
                }

                player.sendMessage(TextFormat.AQUA + "Dash! Imortal por 0.5s (gas ainda causa dano)");
            }
            return;
        }

        if (tag != null && tag.contains("projectileShield") && alivePlayers.contains(uid) && gameState == GameState.ACTIVE) {
            event.setCancelled(true);
            if (projectileShieldActive.contains(uid)) {
                player.sendMessage(TextFormat.RED + "Projectile Shield já ativo!");
                return;
            }
            projectileShieldActive.add(uid);
            player.addEffect(Effect.get(EffectType.ABSORPTION).setDuration(10 * 20).setAmplifier(2));
            playEatSound(player);
            int count = item.getCount();
            if (count > 1) { item.setCount(count - 1); player.getInventory().setItemInHand(item); }
            else player.getInventory().setItemInHand(Item.get("minecraft:air", 0, 0));
            player.sendMessage(TextFormat.AQUA + "Projectile Shield ativado por 10s!");
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                projectileShieldActive.remove(uid);
                if (player.isOnline()) player.sendMessage(TextFormat.YELLOW + "Projectile Shield expirou!");
            }, 10 * 20);
            return;
        }

        if (tag != null && tag.contains("reverseStone") && alivePlayers.contains(uid) && gameState == GameState.ACTIVE) {
            event.setCancelled(true);
            int tier = tag.getInt("revTier");
            if (tier < 1 || tier > 4) tier = 1;
            int[] durations = {15, 12, 10, 8};

            if (reverseStoneActive.contains(uid)) {
                player.sendMessage(TextFormat.RED + "Reverse Stone já ativo!");
                return;
            }
            reverseStoneActive.add(uid);
            reverseStoneTier.put(uid, tier);
            playEatSound(player);
            int count = item.getCount();
            if (count > 1) { item.setCount(count - 1); player.getInventory().setItemInHand(item); }
            else player.getInventory().setItemInHand(Item.get("minecraft:air", 0, 0));
            player.sendMessage(TextFormat.AQUA + "Reverse Stone ativado por " + durations[tier-1] + "s!");
            player.addEffect(Effect.get(EffectType.NIGHT_VISION).setDuration(durations[tier-1] * 20).setAmplifier(0));
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                reverseStoneActive.remove(uid);
                reverseStoneTier.remove(uid);
                if (player.isOnline()) player.sendMessage(TextFormat.YELLOW + "Reverse Stone expirou!");
            }, durations[tier-1] * 20);
            return;
        }

        if (tag == null) return;
        if (checkCooldown(player)) return;

        if (tag.contains("exitLobby")) { event.setCancelled(true); leaveLobby(player); return; }
        if (tag.contains("exitGame")) {
            event.setCancelled(true);
            spectatorPlayers.remove(uid);
            lobbyPlayers.remove(uid);
            player.setGamemode(Player.ADVENTURE);
            player.getInventory().clearAll();
            teleportMainLobby(player);
            return;
        }
        if (tag.contains("playAgain")) {
            event.setCancelled(true);
            spectatorPlayers.remove(uid);
            joinLobby(player);
            return;
        }
        if (tag.contains("spectClock")) {
            event.setCancelled(true);
            openSpectatorForm(player);
        }
        if (tag.contains("superShock") && alivePlayers.contains(uid) && gameState == GameState.ACTIVE) {
            event.setCancelled(true);

            long now = System.currentTimeMillis();
            long lastUse = superShockCooldown.getOrDefault(uid, 0L);
            if (now - lastUse < 5000) {
                player.sendMessage(TextFormat.RED + "O Super Choque está em cooldown! Aguarde " + String.format("%.1f", (5000 - (now - lastUse)) / 1000.0) + "s.");
                return;
            }
            superShockCooldown.put(uid, now);

            cn.nukkit.math.Vector3 lookDir = player.getDirectionVector();
            Player target = null;
            double bestDot = -1;
            for (UUID auid : alivePlayers) {
                Player ap = getPlayer(auid);
                if (ap == null || ap == player) continue;
                double dist = ap.distance(player);
                if (dist > 50) continue;
                cn.nukkit.math.Vector3 toTarget = new cn.nukkit.math.Vector3(
                    ap.getX() - player.getX(),
                    (ap.getY() + 1.0) - (player.getY() + 1.0),
                    ap.getZ() - player.getZ()
                ).normalize();
                double dot = lookDir.dot(toTarget);
                if (dot > bestDot && dot > 0.95) {
                    bestDot = dot;
                    target = ap;
                }
            }

            if (target != null) {
                Location tloc = target.getLocation();
                Entity lightning = cn.nukkit.entity.Entity.createEntity("minecraft:lightning_bolt", tloc);
                if (lightning != null) lightning.spawnToAll();

                double dmg = 15.0;
                EntityDamageByEntityEvent ev = new EntityDamageByEntityEvent(player, target, DamageCause.LIGHTNING, (float) dmg);
                target.attack(ev);

                playXpSound(player);
                player.sendMessage(TextFormat.GREEN + "Super Choque atingiu " + target.getName() + "!");
            } else {
                Block targetBlock = player.getTargetBlock(50);
                if (targetBlock != null && !targetBlock.getId().equals("minecraft:air")) {
                    Location tloc = targetBlock.getLocation();
                    Entity lightning = cn.nukkit.entity.Entity.createEntity("minecraft:lightning_bolt", tloc);
                    if (lightning != null) lightning.spawnToAll();
                    player.sendMessage(TextFormat.YELLOW + "Super Choque disparado no chão!");
                } else {
                    player.sendMessage(TextFormat.RED + "Muito longe para disparar o raio!");
                }
            }
            return;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getLevel().getFolderName().equals(pvpWorldName)) return;
        UUID uid = p.getUniqueId();

        if ((gameState == GameState.LOBBY || gameState == GameState.STARTING) && lobbyPlayers.contains(uid)) {
            if (lobbyMin != null && lobbyMax != null) {
                double px = p.getX(), py = p.getY(), pz = p.getZ();
                double minX = Math.min(lobbyMin.x, lobbyMax.x);
                double maxX = Math.max(lobbyMin.x, lobbyMax.x);
                double minY = Math.min(lobbyMin.y, lobbyMax.y);
                double maxY = Math.max(lobbyMin.y, lobbyMax.y);
                double minZ = Math.min(lobbyMin.z, lobbyMax.z);
                double maxZ = Math.max(lobbyMin.z, lobbyMax.z);

                if (px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ) {
                    if (lobbySpawn != null) {
                        p.teleport(lobbySpawn);
                        p.sendMessage(TextFormat.RED + "Você saiu dos limites do lobby e foi teleportado de volta.");
                    }
                }
            }
        }

        if (spectatorPlayers.contains(uid)) {
            if (spectatorMin == null) return;
            double px = p.getX(), py = p.getY(), pz = p.getZ();
            double minX = Math.min(spectatorMin.x, spectatorMax.x);
            double maxX = Math.max(spectatorMin.x, spectatorMax.x);
            double minY = Math.min(spectatorMin.y, spectatorMax.y);
            double maxY = Math.max(spectatorMin.y, spectatorMax.y);
            double minZ = Math.min(spectatorMin.z, spectatorMax.z);
            double maxZ = Math.max(spectatorMin.z, spectatorMax.z);
            if (px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ)
                event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        String mode = setupMode.get(uid);
        if (mode != null && mode.equals("chest")) {
            Item item = event.getItem();
            if (isBrStick(item)) {
                showRegisteredChests(true);
            } else {
                checkAndHideChests();
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        String mode = setupMode.get(uid);
        if (mode != null && mode.equals("chest")) {
            Item item = event.getItem();
            if (isBrStick(item)) {
                getServer().getScheduler().scheduleDelayedTask(this, this::checkAndHideChests, 1);
            }
        }
    }

    private void checkAndHideChests() {
        boolean anyEditing = false;
        for (Player p : getServer().getOnlinePlayers().values()) {
            String mode = setupMode.get(p.getUniqueId());
            if (mode != null && mode.equals("chest") && isBrStick(p.getInventory().getItemInHand())) {
                anyEditing = true;
                break;
            }
        }
        if (!anyEditing && gameState != GameState.ACTIVE) {
            showRegisteredChests(false);
        }
    }

    @EventHandler
    public void onProjectileHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (!victim.getLevel().getFolderName().equals(pvpWorldName)) return;
        if (event.getDamager() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) event.getDamager();
            if (arrow.shootingEntity instanceof Player) {
                Player shooter = (Player) arrow.shootingEntity;
                if (alivePlayers.contains(shooter.getUniqueId())) {
                    playXpSound(shooter);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uid = p.getUniqueId();
        if (!p.getLevel().getFolderName().equals(pvpWorldName)) return;

        boolean wasActive = gameState == GameState.ACTIVE && alivePlayers.contains(uid);

        lobbyPlayers.remove(uid); alivePlayers.remove(uid); spectatorPlayers.remove(uid);
        setupMode.remove(uid); setupPos1.remove(uid); pendingSpectator.remove(uid);
        checkAndHideChests();

        if (wasActive) {
            List<Item> saved = new ArrayList<>();
            for (Item item : p.getInventory().getContents().values())
                if (item != null && item.getCount() > 0) saved.add(item.clone());
            if (!saved.isEmpty()) spawnDeathChest(p.getLocation(), p.getLevel(), saved);
            broadcastAll(TextFormat.RED + "! " + p.getName() + " desistiu! §7(" + alivePlayers.size() + " restantes)");
            checkWinCondition();
        }
        if (gameState == GameState.STARTING && lobbyPlayers.size() < MIN_PLAYERS) cancelCountdown();
        if (gameState == GameState.ACTIVE) checkWinCondition();
        if (lobbyPlayers.isEmpty() && gameState == GameState.LOBBY) { gameState = GameState.IDLE; cancelAllTasks(); }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        if (spectatorPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (event.getInventory().getHolder() instanceof Player) {
            Player p = (Player) event.getInventory().getHolder();
            if (spectatorPlayers.contains(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    private void openChestForm(Player player, Vector3 pos) {
        String key = posKey(pos);
        ChestEntry entry = registeredChests.stream().filter(e -> posKey(e.pos).equals(key)).findFirst().orElse(null);
        if (entry == null) { player.sendMessage(TextFormat.RED + "Baú não registrado nesta posição."); return; }
        final ChestEntry fe = entry;
        List<String> types = Arrays.asList("NONE","TRASH","NORMAL","GOOD","EPIC","LEGENDARY","RANDOM");
        int typeIdx = Math.max(0, types.indexOf(entry.type.name()));
        new CustomForm("§6Configurar Baú BR")
            .addSlider("Chance de aparecer (%)", 0f, 100f, 1, (float)fe.chance)
            .addDropdown("Tipo", types, typeIdx)
            .addToggle("Remover este Baú", false)
            .addToggle("Desativar Modo de Edição (Confirmar)", false)
            .onSubmit((p, resp) -> {
                boolean remove = resp.getToggleResponse(2);
                boolean exit = resp.getToggleResponse(3);

                if (remove) {
                    registeredChests.remove(fe);
                    saveBrConfig();
                    Level lvl = getServer().getLevelByName(pvpWorldName);
                    if (lvl != null) {
                        lvl.setBlock((int)fe.pos.x, (int)fe.pos.y, (int)fe.pos.z, Block.get("minecraft:air"), true, true);
                    }
                    p.sendMessage(TextFormat.RED + "✓ Baú removido do registro!");
                } else {
                    fe.chance = (int)resp.getSliderResponse(0);
                    fe.type = ChestType.valueOf(types.get(resp.getDropdownResponse(1).elementId()));
                    saveBrConfig();
                    p.sendMessage(TextFormat.GREEN + "✓ Baú: " + fe.chance + "% | " + fe.type.name());
                }

                if (exit) {
                    setupMode.remove(p.getUniqueId());
                    p.getInventory().remove(p.getInventory().getItemInHand());
                    p.sendMessage(TextFormat.YELLOW + "✓ Modo de edição de baús finalizado!");
                    checkAndHideChests();
                }
            })
            .send(player);
    }

    private void openAdminStickForm(Player player) {
        SimpleForm form = new SimpleForm("§6Painel de Controle de Baús", "Selecione uma opção:");
        
        form.addButton("Sair do Modo de Edição (Confirmar)", p -> {
            setupMode.remove(p.getUniqueId());
            p.getInventory().remove(p.getInventory().getItemInHand());
            p.sendMessage(TextFormat.YELLOW + "✓ Modo de edição de baús finalizado!");
            checkAndHideChests();
        });
        
        form.addButton("Destruir TODOS os baús REGISTRADOS", p -> {
            Level level = getServer().getLevelByName(pvpWorldName);
            if (level != null) {
                for (ChestEntry e : registeredChests) {
                    level.setBlock((int)e.pos.x, (int)e.pos.y, (int)e.pos.z, Block.get("minecraft:air"), true, true);
                }
            }
            registeredChests.clear();
            saveBrConfig();
            p.sendMessage(TextFormat.RED + "✓ Todos os baús registrados foram excluídos e destruídos!");
        });
        
        form.addButton("Destruir todos os baús NÃO registrados", p -> {
            Level level = p.getLevel();
            int count = 0;
            for (BlockEntity be : new ArrayList<>(level.getBlockEntities().values())) {
                if (be instanceof BlockEntityChest) {
                    Vector3 pos = new Vector3(be.getFloorX(), be.getFloorY(), be.getFloorZ());
                    boolean isReg = registeredChests.stream().anyMatch(e -> posKey(e.pos).equals(posKey(pos)));
                    if (!isReg) {
                        level.setBlock(be.getFloorX(), be.getFloorY(), be.getFloorZ(), Block.get("minecraft:air"), true, true);
                        count++;
                    }
                }
            }
            p.sendMessage(TextFormat.GREEN + "✓ " + count + " baús não registrados foram destruídos!");
        });
        
        form.send(player);
    }

    private void openSpectatorForm(Player player) {
        List<String> names = alivePlayers.stream()
            .map(uid -> { Player p = getPlayer(uid); return p != null ? p.getName() : null; })
            .filter(Objects::nonNull).collect(Collectors.toList());
        if (names.isEmpty()) { player.sendMessage(TextFormat.YELLOW + "Nenhum jogador vivo."); return; }

        SimpleForm form = new SimpleForm("§eEspectadores - Teleportar", "Escolha quem espectatar:");
        for (String n : names) {
            form.addButton(n, p -> {
                Player t = getServer().getPlayer(n);
                if (t != null) { p.teleport(t.getLocation()); p.sendMessage(TextFormat.YELLOW + "Teleportado para " + n); }
            });
        }
        form.send(player);
    }

    private void registerChest(Player player, Vector3 pos, Level level) {
        String key = posKey(pos);
        boolean exists = registeredChests.stream().anyMatch(e -> posKey(e.pos).equals(key));
        if (exists) { player.sendMessage(TextFormat.YELLOW + "Baú já registrado nesta posição. Interaja para editar."); return; }
        registeredChests.add(new ChestEntry(pos, level.getFolderName(), 80, ChestType.NORMAL));
        saveBrConfig();
        player.sendMessage(TextFormat.GREEN + "✓ Baú registrado acima do bloco em (" + (int)pos.x + "," + (int)pos.y + "," + (int)pos.z + ") (80%, NORMAL). Interaja para configurar.");
    }

    private void showRegisteredChests(boolean show) {
        Level level = getServer().getLevelByName(pvpWorldName);
        if (level == null) return;
        for (ChestEntry e : registeredChests) {
            int bx = (int)e.pos.x, by = (int)e.pos.y, bz = (int)e.pos.z;
            if (show) {
                level.setBlock(bx, by, bz, Block.get("minecraft:chest"), true, true);
            } else {
                level.setBlock(bx, by, bz, Block.get("minecraft:air"), true, true);
            }
        }
    }

    private void saveSetup(Player player, String mode, Vector3 p1, Vector3 p2) {
        Vector3 min = new Vector3(Math.min(p1.x,p2.x), Math.min(p1.y,p2.y), Math.min(p1.z,p2.z));
        Vector3 max = new Vector3(Math.max(p1.x,p2.x), Math.max(p1.y,p2.y), Math.max(p1.z,p2.z));
        switch (mode) {
            case "floor":
                floorMin=min; floorMax=max; savePosConfig("floor",min,max);
                player.sendMessage(TextFormat.GREEN + "Área do chão configurada!");
                break;
            case "spectator":
                spectatorMin=min; spectatorMax=max; savePosConfig("spectator",min,max);
                player.sendMessage(TextFormat.GREEN + "Limites do espectador configurados!");
                break;
            case "gas":
                gasMin=min; gasMax=max; savePosConfig("gas",min,max);
                player.sendMessage(TextFormat.GREEN + "Área do gás configurada!");
                break;
            case "lobbylimits":
                lobbyMin=min; lobbyMax=max; savePosConfig("lobby-limits",min,max);
                player.sendMessage(TextFormat.GREEN + "✓ Limites do lobby configurados!");
                break;
        }
    }

    private void startActionBarTask() {
        if (actionBarTask != null) return;
        actionBarTask = getServer().getScheduler().scheduleRepeatingTask(this, this::updateActionBar, ACTIONBAR_TICKS);
    }

    private void updateActionBar() {
        String msg;
        if (gameState == GameState.LOBBY || gameState == GameState.STARTING) {
            msg = TextFormat.YELLOW + "Jogadores: §f" + lobbyPlayers.size() + "§e/" + MIN_PLAYERS;
        } else if (gameState == GameState.ACTIVE) {
            msg = TextFormat.RED + "Vivos: §f" + alivePlayers.size();
        } else return;
        for (UUID uid : lobbyPlayers) { Player p=getPlayer(uid); if(p!=null) p.sendActionBar(msg); }
        for (UUID uid : alivePlayers) { Player p=getPlayer(uid); if(p!=null) p.sendActionBar(msg); }
        for (UUID uid : spectatorPlayers) { Player p=getPlayer(uid); if(p!=null) p.sendActionBar(msg); }
    }

    private void broadcastLobby(String msg) {
        lobbyPlayers.forEach(uid -> { Player p=getPlayer(uid); if(p!=null) p.sendMessage(msg); });
    }

    private void broadcastAll(String msg) {
        lobbyPlayers.forEach(uid -> { Player p=getPlayer(uid); if(p!=null) p.sendMessage(msg); });
        alivePlayers.forEach(uid -> { Player p=getPlayer(uid); if(p!=null) p.sendMessage(msg); });
        spectatorPlayers.forEach(uid -> { Player p=getPlayer(uid); if(p!=null) p.sendMessage(msg); });
    }

    private void cancelAllTasks() {
        if (countdownTask!=null){countdownTask.cancel();countdownTask=null;}
        if (gasTask!=null){gasTask.cancel();gasTask=null;}
        if (particleTask!=null){particleTask.cancel();particleTask=null;}
        if (damageTask!=null){damageTask.cancel();damageTask=null;}
        if (actionBarTask!=null){actionBarTask.cancel();actionBarTask=null;}
        for (TaskHandler th : flyingChestTasks.values()) th.cancel();
        flyingChestTasks.clear();
    }

    private void teleportMainLobby(Player player) {
        try {
            Config cfg = new Config(new File(getServer().getDataPath(), "plugins/GugunetCore/config.yml"), Config.YAML);
            if (cfg.exists("loginpos.world")) {
                Level lvl = getServer().getLevelByName(cfg.getString("loginpos.world"));
                if (lvl != null) {
                    player.teleport(new Location(cfg.getDouble("loginpos.x"), cfg.getDouble("loginpos.y"),
                        cfg.getDouble("loginpos.z"), cfg.getDouble("loginpos.yaw"), cfg.getDouble("loginpos.pitch"), lvl));
                    return;
                }
            }
        } catch (Exception ignored) {}
        Level main = getServer().getDefaultLevel();
        if (main != null) player.teleport(main.getSpawnLocation().getLocation());
    }

    private void ensurePvpWorldLoaded() {
        if (getServer().getLevelByName(pvpWorldName) == null) getServer().loadLevel(pvpWorldName);
    }

    private void playNoteSound(Player player, int countdown) {
        try {
            PlaySoundPacket pk = new PlaySoundPacket();
            pk.name = "note.harp";
            pk.volume = 1.0f;
            pk.pitch = 0.8f + (COUNTDOWN_SECS - countdown) * 0.05f;
            pk.x = (int) player.getX();
            pk.y = (int) player.getY();
            pk.z = (int) player.getZ();
            player.dataPacket(pk);
        } catch (Exception ignored) {}
    }

    private void playEatSound(Player player) {
        try {
            PlaySoundPacket pk = new PlaySoundPacket();
            pk.name = "random.eat";
            pk.volume = 1.0f;
            pk.pitch = 1.0f;
            pk.x = (int) player.getX();
            pk.y = (int) player.getY();
            pk.z = (int) player.getZ();
            player.dataPacket(pk);
        } catch (Exception ignored) {}
    }

    private void playXpSound(Player player) {
        try {
            PlaySoundPacket pk = new PlaySoundPacket();
            pk.name = "random.levelup";
            pk.volume = 1.0f;
            pk.pitch = 1.5f;
            pk.x = (int) player.getX();
            pk.y = (int) player.getY();
            pk.z = (int) player.getZ();
            player.dataPacket(pk);
        } catch (Exception ignored) {}
    }

    private void playExplosionSound(Level level, Vector3 pos) {
        try {
            PlaySoundPacket pk = new PlaySoundPacket();
            pk.name = "random.explode";
            pk.volume = 1.0f;
            pk.pitch = 1.0f;
            pk.x = (int) pos.x;
            pk.y = (int) pos.y;
            pk.z = (int) pos.z;
            for (Player p : level.getPlayers().values()) {
                p.dataPacket(pk);
            }
        } catch (Exception ignored) {}
    }

    private Item makeDye(String name, String tagKey, boolean lime) {
        Item item = Item.get(lime ? "minecraft:lime_dye" : "minecraft:red_dye", 0, 1);
        item.setCustomName(name);
        CompoundTag t = item.getNamedTag() != null ? item.getNamedTag() : new CompoundTag();
        t.putBoolean(tagKey, true);
        item.setNamedTag(t);
        return item;
    }

    private boolean isBrStick(Item item) {
        if (item == null) return false;
        CompoundTag t = item.getNamedTag();
        return t != null && t.contains("brChestStick") && t.getBoolean("brChestStick");
    }

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = interactCooldown.get(uid);
        if (last != null && (now - last) < INTERACT_COOLDOWN_MS) return true;
        interactCooldown.put(uid, now);
        return false;
    }

    private String posKey(Vector3 v) { return (int)v.x+","+(int)v.y+","+(int)v.z; }
    private Vector3 posFromKey(String k) {
        String[] p=k.split(",");
        return new Vector3(Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]));
    }
    private String fmtPos(Block b) { return "("+b.getFloorX()+","+b.getFloorY()+","+b.getFloorZ()+")"; }

    private ChestType pickRandomChestType(Random rand) {
        int roll = rand.nextInt(100);
        if (roll < 35) return ChestType.NORMAL;
        if (roll < 60) return ChestType.GOOD;
        if (roll < 80) return ChestType.TRASH;
        if (roll < 92) return ChestType.NONE;
        if (roll < 98) return ChestType.EPIC;
        return ChestType.LEGENDARY;
    }

    public void forceRemovePlayer(Player player) {
        UUID uid = player.getUniqueId();
        if (lobbyPlayers.contains(uid)) {
            leaveLobby(player);
            return;
        }
        if (alivePlayers.contains(uid)) {
            alivePlayers.remove(uid);
            pendingSpectator.remove(uid);
            deathLocations.remove(uid);
            projectileShieldActive.remove(uid);
            reverseStoneActive.remove(uid);
            reverseStoneTier.remove(uid);
            player.getInventory().clearAll();
            broadcastAll(TextFormat.RED + player.getName() + " foi removido! §7(" + alivePlayers.size() + " restantes)");
            checkWinCondition();
        }
        spectatorPlayers.remove(uid);
        setupMode.remove(uid);
        setupPos1.remove(uid);
    }
}

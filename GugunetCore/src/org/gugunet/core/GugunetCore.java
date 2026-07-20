package org.gugunet.core;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.effect.Effect;
import cn.nukkit.entity.effect.EffectType;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.form.window.SimpleForm;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.LevelConfig;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import cn.nukkit.scoreboard.Scoreboard;
import cn.nukkit.scoreboard.data.DisplaySlot;

import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GugunetCore extends PluginBase implements Listener {

    private static GugunetCore instance;
    private static final String MAIN_WORLD = "world";
    private final Map<UUID, TaskHandler> loadingPlayers = new HashMap<>();
    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 500;

    private final Map<UUID, PlayerStats> statsMap = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private Config statsConfig;
    private Connection dbConnection;

    public static class PlayerStats {
        public int level = 1;
        public int xp = 0;
        public double money = 0.0;
    }

    public static GugunetCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.getServer().getPluginManager().registerEvents(this, this);
        this.saveDefaultConfig();

        this.statsConfig = new Config(new File(this.getDataFolder(), "players_stats.json"), Config.JSON);
        setupDatabase();

        createFlatWorld("spleef");
        createFlatWorld("pvp");
        createFlatWorld("get_arrow");

        this.getLogger().info("GugunetCore ativado!");

        // Agendamento para atualizar o scoreboard do lobby a cada 3 segundos
        this.getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (Player player : this.getServer().getOnlinePlayers().values()) {
                if (isInMainLobby(player)) {
                    updateScoreboard(player);
                } else {
                    removeScoreboard(player);
                }
            }
        }, 60);
    }

    @Override
    public void onDisable() {
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void setupDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(this.getDataFolder(), "gugunet_stats.db");
            if (!this.getDataFolder().exists()) {
                this.getDataFolder().mkdirs();
            }
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            this.dbConnection = DriverManager.getConnection(url);
            
            try (Statement stmt = this.dbConnection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "name VARCHAR(32), " +
                        "level INTEGER DEFAULT 1, " +
                        "xp INTEGER DEFAULT 0, " +
                        "money REAL DEFAULT 0.0" +
                        ");");
            }
            this.getLogger().info("Banco de dados SQLite iniciado com sucesso!");
        } catch (Exception e) {
            this.getLogger().error("Erro ao inicializar o banco de dados SQLite: " + e.getMessage());
        }
    }

    private void createFlatWorld(String name) {
        Level level = this.getServer().getLevelByName(name);
        if (level != null) return;

        File worldFolder = new File(this.getServer().getDataPath() + "/worlds/" + name);
        if (worldFolder.exists()) {
            this.getServer().loadLevel(name);
            return;
        }

        try {
            Level defaultLvl = this.getServer().getDefaultLevel();
            String defaultName = defaultLvl != null ? defaultLvl.getFolderName() : "world";
            LevelConfig baseConfig = this.getServer().getLevelConfig(defaultName);
            cn.nukkit.level.DimensionData dimData = defaultLvl != null ? defaultLvl.getDimensionData() : new cn.nukkit.level.DimensionData(0, -64, 320);

            Map<Integer, LevelConfig.GeneratorConfig> generators = new HashMap<>();
            LevelConfig.GeneratorConfig genConfig = new LevelConfig.GeneratorConfig()
                .name("flat")
                .seed(new java.util.Random().nextLong())
                .dimensionData(dimData);
            generators.put(0, genConfig);

            LevelConfig newConfig = new LevelConfig()
                .format(baseConfig != null ? baseConfig.format() : "leveldb")
                .enable(true)
                .generators(generators);

            this.getServer().generateLevel(name, newConfig);
            this.getServer().loadLevel(name);
            this.getLogger().info("Mundo plano '" + name + "' gerado e carregado com sucesso!");
        } catch (Exception e) {
            this.getLogger().error("Erro ao gerar o mundo plano '" + name + "': ", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Carrega as estatisticas do jogador
        getStats(uid);

        if (!player.isOp()) {
            player.setGamemode(Player.ADVENTURE);
        }

        Config config = this.getConfig();
        if (config.exists("loginpos.world")) {
            String worldName = config.getString("loginpos.world");
            double x = config.getDouble("loginpos.x");
            double y = config.getDouble("loginpos.y");
            double z = config.getDouble("loginpos.z");
            double yaw = config.getDouble("loginpos.yaw");
            double pitch = config.getDouble("loginpos.pitch");

            Level level = this.getServer().getLevelByName(worldName);
            if (level == null) {
                this.getServer().loadLevel(worldName);
                level = this.getServer().getLevelByName(worldName);
            }
            if (level != null) {
                Location loc = new Location(x, y, z, yaw, pitch, level);
                player.teleport(loc);
            }
        }

        player.sendTitle("§a§lBem-vindo!", "", 15, 50, 15);

        String rank = getPlayerRank(player);
        String welcomeMessage;
        if ("Admin".equalsIgnoreCase(rank) || player.isOp()) {
            welcomeMessage = "§c§l[Admin] " + player.getName() + " entrou no jogo com estilo! §r";
        } else if ("Magma".equalsIgnoreCase(rank)) {
            welcomeMessage = "§6§l[Magma] " + player.getName() + " entrou no jogo! §r";
        } else {
            welcomeMessage = "§a" + player.getName() + " entrou no jogo!";
        }
        event.setJoinMessage(welcomeMessage);

        // Clear all previous effects
        for (EffectType type : new ArrayList<>(player.getEffects().keySet())) {
            player.removeEffect(type);
        }
        player.getInventory().clearAll();
        player.addEffect(Effect.get(EffectType.SLOW_FALLING).setDuration(5 * 20).setAmplifier(1));
        player.setAllowFlight(true);
        player.setFlying(true);

        TaskHandler loadingTask = this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                if (player.getGamemode() != Player.CREATIVE && player.getGamemode() != Player.SPECTATOR) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
                loadingPlayers.remove(uid);
            }
        }, 5 * 20);
        loadingPlayers.put(uid, loadingTask);

        this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                giveLobbyClock(player);
                updateScoreboard(player);
            }
        }, 3);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        TaskHandler task = loadingPlayers.remove(uid);
        if (task != null) task.cancel();

        PlayerStats stats = statsMap.remove(uid);
        if (stats != null) {
            saveStats(uid, stats);
        }
        removeScoreboard(player);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        UUID uid = p.getUniqueId();
        if (loadingPlayers.containsKey(uid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                if (isInMainLobby(player)) {
                    // Clear all effects
                    for (EffectType type : new ArrayList<>(player.getEffects().keySet())) {
                        player.removeEffect(type);
                    }
                    // Reset gamemode if not OP
                    if (!player.isOp()) {
                        player.setGamemode(Player.ADVENTURE);
                    }
                    giveLobbyClock(player);
                    updateScoreboard(player);
                } else {
                    removeScoreboard(player);
                }
            }
        }, 3);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();
        if (item == null) return;
        CompoundTag tag = item.getNamedTag();
        if (tag == null) return;
        if (tag.contains("lobbyClock")) {
            long now = System.currentTimeMillis();
            UUID uid = player.getUniqueId();
            Long last = interactCooldown.get(uid);
            if (last != null && (now - last) < INTERACT_COOLDOWN_MS) return;
            interactCooldown.put(uid, now);
            event.setCancelled(true);
            if (!isInMainLobby(player)) {
                player.sendMessage(TextFormat.RED + "Use isso apenas no lobby principal!");
                return;
            }
            openMinigameForm(player);
        }
    }

    private boolean isInMainLobby(Player player) {
        return player.getLevel().getFolderName().equals(MAIN_WORLD);
    }

    private void giveLobbyClock(Player player) {
        if (!isInMainLobby(player)) return;
        if (hasClock(player)) return;

        Item clock = Item.get("minecraft:clock", 0, 1);
        clock.setCustomName("§6§lSelecionar Minigame");
        CompoundTag tag = clock.getNamedTag() != null ? clock.getNamedTag() : new CompoundTag();
        tag.putBoolean("lobbyClock", true);
        clock.setNamedTag(tag);

        player.getInventory().setItem(0, clock);
    }

    private boolean hasClock(Player player) {
        for (Item item : player.getInventory().getContents().values()) {
            if (item == null) continue;
            CompoundTag t = item.getNamedTag();
            if (t != null && t.contains("lobbyClock")) return true;
        }
        return false;
    }

    private void openMinigameForm(Player player) {
        SimpleForm form = new SimpleForm("Minigames", "Selecione um minigame");

        form.addButton("Battle Royale", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_1"), p -> {
            getServer().executeCommand(p, "pvp enter");
        });
        form.addButton("Minigame 2", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_2"), p -> {
            p.sendMessage(TextFormat.YELLOW + "Minigame numero 2!");
        });
        form.addButton("Minigame 3", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_3"), p -> {
            p.sendMessage(TextFormat.YELLOW + "Minigame numero 3!");
        });
        form.addButton("Minigame 4", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_4"), p -> {
            p.sendMessage(TextFormat.YELLOW + "Minigame numero 4!");
        });
        form.addButton("Minigame 5", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_5"), p -> {
            p.sendMessage(TextFormat.YELLOW + "Minigame numero 5!");
        });
        form.addButton("Minigame 6", new cn.nukkit.form.element.simple.ButtonImage(cn.nukkit.form.element.simple.ButtonImage.Type.PATH, "textures/preview_minigame_6"), p -> {
            p.sendMessage(TextFormat.YELLOW + "Minigame numero 6!");
        });

        form.send(player);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = getPlayerRank(player);
        String displayName = player.getDisplayName();
        String message = event.getMessage();

        String formattedMessage;
        if ("Admin".equalsIgnoreCase(rank)) {
            formattedMessage = "§7[§c§lAdmin§r§7] §c§l" + displayName + "§r§7: §c§l" + message;
        } else if ("Magma".equalsIgnoreCase(rank)) {
            formattedMessage = "§7[§6Magma§7] §6" + displayName + "§7: §6" + message;
        } else {
            formattedMessage = "§7[§eNovato§7] §f" + displayName + "§7: §f" + message;
        }

        event.setCancelled(true);
        for (CommandSender recipient : event.getRecipients()) {
            recipient.sendMessage(formattedMessage);
        }
        this.getServer().getLogger().info(TextFormat.clean(formattedMessage));
    }

    private static final Set<String> RESTRICTED_COMMANDS = Set.of(
        "guardian", "npc", "mininpc", "edittool", "wand", "mwe",
        "we", "brush", "setloginpos", "setrank", "addxp", "addmoney", "setlevel"
    );

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        String message = event.getMessage().trim();
        if (!message.startsWith("/")) return;

        String[] parts = message.substring(1).split(" ", 2);
        if (parts.length == 0) return;
        String command = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split(" ") : new String[0];

        if (command.equals("pvp")) {
            if (args.length < 1 || !args[0].equalsIgnoreCase("enter")) {
                player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este subcomando de /pvp.");
                event.setCancelled(true);
            }
            return;
        }

        if (command.equals("prision") || command.equals("prison") || command.equals("prisao")) {
            if (args.length < 1 || (!args[0].equalsIgnoreCase("visit") && !args[0].equalsIgnoreCase("status"))) {
                player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este subcomando.");
                event.setCancelled(true);
            }
            return;
        }

        if (command.equals("spleef") || command.equals("get_arrow")) {
            if (args.length < 1 || !args[0].equalsIgnoreCase("enter")) {
                player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este subcomando.");
                event.setCancelled(true);
            }
            return;
        }

        if (RESTRICTED_COMMANDS.contains(command)) {
            player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
            event.setCancelled(true);
        }
    }

    private String getPlayerRank(Player player) {
        Config config = this.getConfig();
        String rank = config.getString("ranks." + player.getUniqueId().toString(), "");
        if (rank.isEmpty()) {
            rank = config.getString("ranks." + player.getName().toLowerCase(), "");
        }
        if (rank.isEmpty()) {
            if (player.isOp()) return "Admin";
            return "Novato";
        }
        return rank;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        if (cmd.equals("setloginpos")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando só pode ser usado por jogadores.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.isOp()) {
                player.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
                return true;
            }
            Location loc = player.getLocation();
            Config config = this.getConfig();
            config.set("loginpos.world", loc.getLevel().getFolderName());
            config.set("loginpos.x", loc.getX());
            config.set("loginpos.y", loc.getY());
            config.set("loginpos.z", loc.getZ());
            config.set("loginpos.yaw", loc.getYaw());
            config.set("loginpos.pitch", loc.getPitch());
            config.save();
            player.sendMessage(TextFormat.GREEN + "Posição de login definida com sucesso!");
            return true;
        }

        if (cmd.equals("setrank")) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(TextFormat.RED + "Uso: /setrank <jogador> <Novato|Magma|Admin>");
                return true;
            }
            String targetName = args[0];
            String newRank = args[1];
            if (!newRank.equalsIgnoreCase("Novato") && !newRank.equalsIgnoreCase("Magma") && !newRank.equalsIgnoreCase("Admin")) {
                sender.sendMessage(TextFormat.RED + "Rank inválido! Escolha entre Novato, Magma ou Admin.");
                return true;
            }
            Player target = this.getServer().getPlayer(targetName);
            String uuidKey;
            String nameToDisplay;
            if (target != null) {
                uuidKey = target.getUniqueId().toString();
                nameToDisplay = target.getName();
            } else {
                uuidKey = targetName.toLowerCase();
                nameToDisplay = targetName;
            }
            Config config = this.getConfig();
            String formattedRank = newRank.substring(0, 1).toUpperCase() + newRank.substring(1).toLowerCase();
            config.set("ranks." + uuidKey, formattedRank);
            if (target != null) {
                config.set("ranks." + target.getName().toLowerCase(), formattedRank);
            }
            config.save();
            sender.sendMessage(TextFormat.GREEN + "Rank de " + nameToDisplay + " definido como " + formattedRank + "!");
            if (target != null) {
                target.sendMessage(TextFormat.GREEN + "Seu rank foi atualizado para: " + formattedRank);
            }
            return true;
        }

        if (cmd.equals("spleef")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando só pode ser usado por jogadores.");
                return true;
            }
            Player player = (Player) sender;
            if (args.length > 0 && args[0].equalsIgnoreCase("enter")) {
                player.sendMessage(TextFormat.GREEN + "Você entrou na fila de Spleef (Ação pendente)!");
            } else {
                player.sendMessage(TextFormat.YELLOW + "Uso: /spleef enter");
            }
            return true;
        }

        if (cmd.equals("get_arrow")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Este comando só pode ser usado por jogadores.");
                return true;
            }
            Player player = (Player) sender;
            if (args.length > 0 && args[0].equalsIgnoreCase("enter")) {
                player.sendMessage(TextFormat.GREEN + "Você entrou no Get Arrow (Ação pendente)!");
            } else {
                player.sendMessage(TextFormat.YELLOW + "Uso: /get_arrow enter");
            }
            return true;
        }

        if (cmd.equals("addxp")) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(TextFormat.RED + "Uso: /addxp <jogador> <quantidade>");
                return true;
            }
            String targetName = args[0];
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Quantidade inválida.");
                return true;
            }
            Player target = this.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(TextFormat.RED + "Jogador não encontrado.");
                return true;
            }
            addXp(target, amount);
            sender.sendMessage(TextFormat.GREEN + "Adicionado " + amount + " XP para " + target.getName());
            return true;
        }

        if (cmd.equals("addmoney")) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(TextFormat.RED + "Uso: /addmoney <jogador> <quantidade>");
                return true;
            }
            String targetName = args[0];
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Quantidade inválida.");
                return true;
            }
            Player target = this.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(TextFormat.RED + "Jogador não encontrado.");
                return true;
            }
            addMoney(target, amount);
            sender.sendMessage(TextFormat.GREEN + "Adicionado $" + String.format("%.2f", amount) + " para " + target.getName());
            return true;
        }

        if (cmd.equals("setlevel")) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(TextFormat.RED + "Uso: /setlevel <jogador> <nível>");
                return true;
            }
            String targetName = args[0];
            int level;
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextFormat.RED + "Nível inválido.");
                return true;
            }
            if (level < 1) {
                sender.sendMessage(TextFormat.RED + "Nível deve ser pelo menos 1.");
                return true;
            }
            Player target = this.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(TextFormat.RED + "Jogador não encontrado.");
                return true;
            }
            setLevel(target, level);
            sender.sendMessage(TextFormat.GREEN + "Nível de " + target.getName() + " definido como " + level);
            return true;
        }

        return false;
    }

    public PlayerStats getStats(UUID uuid) {
        if (statsMap.containsKey(uuid)) {
            return statsMap.get(uuid);
        }
        
        PlayerStats stats = new PlayerStats();
        String key = uuid.toString();
        
        if (dbConnection != null) {
            try (PreparedStatement ps = dbConnection.prepareStatement("SELECT level, xp, money FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.level = rs.getInt("level");
                        stats.xp = rs.getInt("xp");
                        stats.money = rs.getDouble("money");
                        statsMap.put(uuid, stats);
                        return stats;
                    }
                }
            } catch (Exception e) {
                this.getLogger().error("Erro ao ler stats do SQLite: " + e.getMessage());
            }
        }
        
        // Fallback: migrate from old JSON config if it exists
        if (statsConfig != null && statsConfig.exists("players." + key)) {
            stats.level = statsConfig.getInt("players." + key + ".level", 1);
            stats.xp = statsConfig.getInt("players." + key + ".xp", 0);
            stats.money = statsConfig.getDouble("players." + key + ".money", 0.0);
            // Save to SQLite
            saveStats(uuid, stats);
        } else {
            // New player initialization in DB
            saveStats(uuid, stats);
        }
        
        statsMap.put(uuid, stats);
        return stats;
    }

    public void saveStats(UUID uuid, PlayerStats stats) {
        String key = uuid.toString();
        Player player = this.getServer().getOnlinePlayers().get(uuid);
        String name = player != null ? player.getName() : "";
        
        if (dbConnection != null) {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                    "INSERT INTO player_stats(uuid, name, level, xp, money) VALUES(?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET name = (CASE WHEN excluded.name != '' THEN excluded.name ELSE player_stats.name END), level = excluded.level, xp = excluded.xp, money = excluded.money")) {
                ps.setString(1, key);
                ps.setString(2, name);
                ps.setInt(3, stats.level);
                ps.setInt(4, stats.xp);
                ps.setDouble(5, stats.money);
                ps.executeUpdate();
            } catch (Exception e) {
                this.getLogger().error("Erro ao salvar stats no SQLite: " + e.getMessage());
            }
        }
    }

    public void addXp(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = getStats(uuid);
        stats.xp += amount;
        int xpNeeded = stats.level * 100;
        boolean leveledUp = false;
        while (stats.xp >= xpNeeded) {
            stats.xp -= xpNeeded;
            stats.level++;
            xpNeeded = stats.level * 100;
            leveledUp = true;
        }
        saveStats(uuid, stats);
        if (leveledUp) {
            player.sendMessage("§a§l[!] NÍVEL AUMENTADO! §fVocê subiu para o nível §e" + stats.level + "§f!");
            player.sendTitle("§a§lLEVEL UP!", "§fNovo nível: §e" + stats.level, 10, 40, 10);
        }
        updateScoreboard(player);
    }

    public void addMoney(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = getStats(uuid);
        stats.money += amount;
        if (stats.money < 0) stats.money = 0;
        saveStats(uuid, stats);
        updateScoreboard(player);
    }

    public void setLevel(Player player, int level) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = getStats(uuid);
        stats.level = level;
        stats.xp = 0;
        saveStats(uuid, stats);
        updateScoreboard(player);
    }

    public void removeScoreboard(Player player) {
        Scoreboard sb = activeScoreboards.remove(player.getUniqueId());
        if (sb != null) {
            sb.removeViewer(player, DisplaySlot.SIDEBAR);
        }
    }

    public void updateScoreboard(Player player) {
        if (!isInMainLobby(player)) {
            removeScoreboard(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerStats stats = getStats(uuid);
        int xpNeeded = stats.level * 100;
        int xpToNext = xpNeeded - stats.xp;

        String rank = getPlayerRank(player);
        String coloredRank;
        if ("Admin".equalsIgnoreCase(rank)) {
            coloredRank = "§c§lAdmin";
        } else if ("Magma".equalsIgnoreCase(rank)) {
            coloredRank = "§6§lMagma";
        } else {
            coloredRank = "§e§lNovato";
        }

        Scoreboard sb = new Scoreboard("sb_" + player.getName(), "§e§lGUGUNET NETWORK");
        sb.addLine("§7§m-------------------", 7);
        sb.addLine(" §aJogador: §7" + player.getName(), 6);
        sb.addLine(" §aRank: " + coloredRank, 5);
        sb.addLine(" \uE200 " + stats.level + " (" + stats.xp + "/" + xpNeeded + ")", 4);
        sb.addLine(" \uE203 " + (int) stats.money, 3);
        sb.addLine(" §7§m------------------- ", 2);
        sb.addLine(" §eplay.gugunet.com", 1);

        Scoreboard oldSb = activeScoreboards.get(uuid);
        if (oldSb != null) {
            oldSb.removeViewer(player, DisplaySlot.SIDEBAR);
        }

        sb.addViewer(player, DisplaySlot.SIDEBAR);
        activeScoreboards.put(uuid, sb);
    }
}
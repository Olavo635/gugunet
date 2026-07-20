package org.gugunet.impostor;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.form.window.SimpleForm;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.PlaySoundPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.registry.Registries;
import cn.nukkit.utils.SerializedImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Impostor extends PluginBase implements Listener {

    public enum GameState { IDLE, LOBBY, STARTING }

    private static final int MIN_PLAYERS    = 3;
    private static final int MAX_PLAYERS    = 6;
    private static final int COUNTDOWN_SECS = 30;
    private static final long INTERACT_COOLDOWN_MS = 500;
    private static final int ACTIONBAR_TICKS = 20;

    private static final String[] COLORS       = {"preto", "branco", "azul", "vermelho", "amarelo", "verde"};
    private static final String[] COLOR_DISPLAY = {"§0Preto", "§fBranco", "§9Azul", "§cVermelho", "§eAmarelo", "§aVerde"};

    private static Impostor instance;
    private GameState gameState = GameState.IDLE;

    private final List<UUID> lobbyPlayers = new ArrayList<>();
    private final Map<UUID, String> playerColors   = new HashMap<>();
    private final Map<UUID, Skin>   originalSkins  = new HashMap<>();

    private String worldName = "impostor";
    private Location lobbySpawn = null;
    private Vector3 lobbyMin = null, lobbyMax = null;

    private final Map<String, Skin>  colorSkins = new HashMap<>();

    private TaskHandler countdownTask, actionBarTask;
    private int countdownVal = COUNTDOWN_SECS;

    private final Map<UUID, String>  setupMode = new HashMap<>();
    private final Map<UUID, Vector3> setupPos1 = new HashMap<>();

    private final Map<UUID, Long> interactCooldown = new HashMap<>();

    public static Impostor getInstance() { return instance; }

    @Override
    public void onLoad() { instance = this; }

    @Override
    public void onEnable() {
        try {
            Registries.ITEM.registerCustomItem(this, ImpostorExitItem.class);
        } catch (Exception e) {
            getLogger().error("Erro ao registrar ImpostorExitItem: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadImpConfig();
        loadSkins();
        getLogger().info("Impostor ativado!");
    }

    @Override
    public void onDisable() {
        cancelAllTasks();
        for (UUID uid : new HashSet<>(originalSkins.keySet())) {
            Player p = getPlayer(uid);
            if (p != null) {
                try { p.setSkin(originalSkins.get(uid)); } catch (Exception ignored) {}
            }
        }
    }

    private Player getPlayer(UUID uuid) {
        return getServer().getPlayer(uuid).orElse(null);
    }

    // ======================== CONFIG ========================

    private void loadImpConfig() {
        Config cfg = getConfig();
        worldName = cfg.getString("world", "impostor");

        if (cfg.exists("lobby-spawn.world")) {
            Level lvl = getServer().getLevelByName(cfg.getString("lobby-spawn.world", worldName));
            if (lvl != null) lobbySpawn = new Location(
                cfg.getDouble("lobby-spawn.x"), cfg.getDouble("lobby-spawn.y"),
                cfg.getDouble("lobby-spawn.z"), cfg.getDouble("lobby-spawn.yaw"),
                cfg.getDouble("lobby-spawn.pitch"), lvl);
        }
        if (cfg.exists("lobby-limits.min.x")) {
            lobbyMin = new Vector3(cfg.getDouble("lobby-limits.min.x"), cfg.getDouble("lobby-limits.min.y"), cfg.getDouble("lobby-limits.min.z"));
            lobbyMax = new Vector3(cfg.getDouble("lobby-limits.max.x"), cfg.getDouble("lobby-limits.max.y"), cfg.getDouble("lobby-limits.max.z"));
        }
    }

    private void savePosConfig(String prefix, Vector3 min, Vector3 max) {
        Config cfg = getConfig();
        cfg.set(prefix + ".min.x", min.x); cfg.set(prefix + ".min.y", min.y); cfg.set(prefix + ".min.z", min.z);
        cfg.set(prefix + ".max.x", max.x); cfg.set(prefix + ".max.y", max.y); cfg.set(prefix + ".max.z", max.z);
        cfg.save();
    }

    // ======================== SKINS ========================

    private void loadSkins() {
        File skinsDir = new File(getDataFolder(), "skins");
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
            getLogger().info("Pasta de skins criada em: plugins/Impostor/skins/");
            getLogger().info("Coloque os arquivos PNG: preto.png, branco.png, azul.png, vermelho.png, amarelo.png, verde.png");
            return;
        }

        for (String color : COLORS) {
            File skinFile = new File(skinsDir, color + ".png");
            if (skinFile.exists()) {
                try {
                    BufferedImage img = ImageIO.read(skinFile);
                    if (img == null) { getLogger().warning("Skin invalida: " + skinFile.getName()); continue; }
                    int width = img.getWidth();
                    int height = img.getHeight();
                    byte[] skinData = new byte[width * height * 4];
                    int index = 0;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int argb = img.getRGB(x, y);
                            skinData[index++] = (byte) ((argb >> 16) & 0xFF);
                            skinData[index++] = (byte) ((argb >> 8) & 0xFF);
                            skinData[index++] = (byte) (argb & 0xFF);
                            skinData[index++] = (byte) ((argb >> 24) & 0xFF);
                        }
                    }
                    Skin skin = new Skin();
                    skin.setSkinId("impostor_" + color);
                    skin.setSkinData(new SerializedImage(width, height, skinData));
                    skin.setGeometryName("geometry.humanoid");
                    skin.setGeometryData("");
                    colorSkins.put(color, skin);
                    getLogger().info("Skin carregada: " + color);
                } catch (IOException e) {
                    getLogger().error("Erro ao carregar skin " + color + ": " + e.getMessage());
                }
            } else {
                getLogger().warning("Skin nao encontrada: " + skinFile.getName());
            }
        }
    }

    private void applySkin(Player player, String color) {
        Skin skin = colorSkins.get(color);
        if (skin != null) {
            try { player.setSkin(skin); } catch (Exception ignored) {}
        }
    }

    // ======================== COMMANDS ========================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("impostor")) return false;

        if (args.length == 0) {
            sender.sendMessage(TextFormat.YELLOW + "/impostor <enter|setlobbyspawn|setlobbylimits>");
            return true;
        }

        if (args[0].equalsIgnoreCase("enter")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Apenas jogadores!"); return true; }
            joinLobby((Player) sender);
            return true;
        }

        if (!(sender instanceof Player)) { sender.sendMessage("Apenas jogadores!"); return true; }
        Player p = (Player) sender;
        if (!p.isOp()) { p.sendMessage(TextFormat.RED + "Apenas admins!"); return true; }

        switch (args[0].toLowerCase()) {
            case "setlobbyspawn":   setLobbySpawn(p); break;
            case "setlobbylimits":  enterSetupMode(p, "lobbylimits"); break;
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
        p.sendMessage(TextFormat.GREEN + "Spawn do lobby Impostor definido!");
    }

    private void enterSetupMode(Player p, String mode) {
        setupMode.put(p.getUniqueId(), mode);
        setupPos1.remove(p.getUniqueId());
        p.sendMessage(TextFormat.AQUA + "Modo de configuracao: §eLimites do Lobby");
        p.sendMessage(TextFormat.GRAY + "Quebre um bloco = Pos1 | Interaja com bloco = Pos2 (auto-confirma)");
    }

    // ======================== LOBBY ========================

    private void joinLobby(Player player) {
        if (gameState == GameState.STARTING && lobbyPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage(TextFormat.RED + "Lobby cheio! Maximo de " + MAX_PLAYERS + " jogadores.");
            return;
        }

        ensureWorldLoaded();
        if (lobbySpawn == null) {
            player.sendMessage(TextFormat.RED + "Spawn do lobby nao configurado! Admin: /impostor setlobbyspawn");
            return;
        }

        final UUID uuid = player.getUniqueId();
        player.teleport(lobbySpawn);

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (!player.isOnline()) return;

            lobbyPlayers.remove(uuid);

            lobbyPlayers.add(uuid);
            player.setGamemode(Player.ADVENTURE);
            player.getInventory().clearAll();

            originalSkins.put(uuid, player.getSkin());

            String assignedColor = null;
            for (String color : COLORS) {
                if (!playerColors.containsValue(color)) {
                    assignedColor = color;
                    break;
                }
            }
            if (assignedColor != null) {
                playerColors.put(uuid, assignedColor);
                applySkin(player, assignedColor);
            }

            player.getInventory().setItem(8, makeExitItem("§cSair do Lobby", "exitLobby"));
            player.getInventory().setItem(0, makeColorPickerItem());

            if (gameState == GameState.IDLE) {
                gameState = GameState.LOBBY;
                startActionBarTask();
            }

            broadcastLobby(TextFormat.YELLOW + player.getName() + " entrou! §7(" + lobbyPlayers.size() + "/" + MIN_PLAYERS + ")");

            if (lobbyPlayers.size() >= MIN_PLAYERS) {
                if (gameState == GameState.STARTING) {
                    if (lobbyPlayers.size() >= MAX_PLAYERS) {
                        resetCountdownTo(3);
                    } else {
                        resetCountdownTo(COUNTDOWN_SECS);
                    }
                } else {
                    startCountdown();
                }
            }
            updateActionBar();
        }, 3);
    }

    private void leaveLobby(Player player) {
        UUID uuid = player.getUniqueId();
        lobbyPlayers.remove(uuid);
        playerColors.remove(uuid);

        if (originalSkins.containsKey(uuid)) {
            try { player.setSkin(originalSkins.get(uuid)); } catch (Exception ignored) {}
            originalSkins.remove(uuid);
        }

        player.getInventory().clearAll();
        player.setGamemode(Player.ADVENTURE);
        teleportMainLobby(player);

        if (gameState == GameState.STARTING && lobbyPlayers.size() < MIN_PLAYERS) cancelCountdown();
        if (lobbyPlayers.isEmpty() && gameState == GameState.LOBBY) {
            gameState = GameState.IDLE;
            cancelAllTasks();
        }
        broadcastLobby(TextFormat.RED + player.getName() + " saiu do lobby.");
        updateActionBar();
    }

    // ======================== COUNTDOWN ========================

    private void startCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        gameState = GameState.STARTING;
        countdownVal = COUNTDOWN_SECS;

        countdownTask = getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            if (lobbyPlayers.size() < MIN_PLAYERS) { cancelCountdown(); return; }
            if (countdownVal <= 0) { startGame(); return; }

            String color = countdownVal <= 3 ? "§c§l" : (countdownVal <= 10 ? "§e§l" : "§a§l");
            String cstr = color + countdownVal;

            for (UUID uid : new ArrayList<>(lobbyPlayers)) {
                Player p = getPlayer(uid);
                if (p == null) continue;
                p.sendTitle("§6§lIMPOSTOR", "§fComeca em: " + cstr, 0, 25, 5);
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
        updateActionBar();
    }

    private void resetCountdownTo(int seconds) {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        countdownVal = seconds;
        for (UUID uid : lobbyPlayers) { Player p = getPlayer(uid); if (p != null) p.sendTitle("", "", 0, 1, 0); }
        startCountdown();
    }

    private void startGame() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (actionBarTask != null) { actionBarTask.cancel(); actionBarTask = null; }

        for (UUID uid : new ArrayList<>(lobbyPlayers)) {
            Player p = getPlayer(uid);
            if (p == null) continue;
            p.getInventory().clearAll();
            p.sendTitle("§6§lIMPOSTOR!", "§eO jogo comecou!", 10, 60, 20);
        }

        broadcastLobby(TextFormat.GREEN + "§lO jogo comecou!");
    }

    // ======================== ACTION BAR ========================

    private void startActionBarTask() {
        if (actionBarTask != null) return;
        actionBarTask = getServer().getScheduler().scheduleRepeatingTask(this, this::updateActionBar, ACTIONBAR_TICKS);
    }

    private void updateActionBar() {
        if (lobbyPlayers.isEmpty()) return;
        String msg;
        if (gameState == GameState.STARTING) {
            msg = "§eImpostor §7| §aJogadores: §f" + lobbyPlayers.size() + "/" + MAX_PLAYERS + " §7| §cComecando em: " + countdownVal + "s";
        } else {
            msg = "§eImpostor §7| §aJogadores: §f" + lobbyPlayers.size() + "/" + MAX_PLAYERS + " §7| §eAguardando... (min: " + MIN_PLAYERS + ")";
        }
        for (UUID uid : lobbyPlayers) { Player p = getPlayer(uid); if (p != null) p.sendActionBar(msg); }
    }

    // ======================== EVENTS ========================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (setupMode.containsKey(uid) && setupPos1.containsKey(uid)) {
            event.setCancelled(true);
            Vector3 block = event.getBlock().getLocation();
            Vector3 pos1 = setupPos1.get(uid);

            Vector3 min = new Vector3(Math.min(pos1.x, block.x), Math.min(pos1.y, block.y), Math.min(pos1.z, block.z));
            Vector3 max = new Vector3(Math.max(pos1.x, block.x), Math.max(pos1.y, block.y), Math.max(pos1.z, block.z));

            lobbyMin = min; lobbyMax = max;
            savePosConfig("lobby-limits", min, max);

            setupMode.remove(uid);
            setupPos1.remove(uid);
            player.sendMessage(TextFormat.GREEN + "Limites do lobby definidos!");
            return;
        }

        if (!lobbyPlayers.contains(uid)) return;

        Item item = player.getInventory().getItemInHand();
        if (item == null) return;

        CompoundTag tag = item.getNamedTag();
        if (tag == null) return;

        if (checkCooldown(player)) return;

        if (tag.contains("exitLobby")) {
            event.setCancelled(true);
            leaveLobby(player);
            return;
        }

        if (tag.contains("impostorColorPicker")) {
            event.setCancelled(true);
            openColorForm(player);
            return;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (setupMode.containsKey(uid)) {
            event.setCancelled(true);
            setupPos1.put(uid, event.getBlock().getLocation());
            player.sendMessage(TextFormat.GREEN + "Pos1 definida! Agora interaja com um bloco para definir Pos2.");
            return;
        }

        if (lobbyPlayers.contains(uid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (lobbyPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getLevel().getFolderName().equals(worldName)) return;
        UUID uid = p.getUniqueId();

        if ((gameState == GameState.LOBBY || gameState == GameState.STARTING) && lobbyPlayers.contains(uid)) {
            if (lobbyMin != null && lobbyMax != null) {
                double px = p.getX(), py = p.getY(), pz = p.getZ();
                if (px < lobbyMin.x || px > lobbyMax.x || py < lobbyMin.y || py > lobbyMax.y || pz < lobbyMin.z || pz > lobbyMax.z) {
                    p.teleport(lobbySpawn);
                    p.sendMessage(TextFormat.RED + "Voce saiu dos limites do lobby!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (lobbyPlayers.contains(uid)) {
            lobbyPlayers.remove(uid);
            playerColors.remove(uid);
            originalSkins.remove(uid);

            if (gameState == GameState.STARTING && lobbyPlayers.size() < MIN_PLAYERS) cancelCountdown();
            if (lobbyPlayers.isEmpty() && gameState == GameState.LOBBY) {
                gameState = GameState.IDLE;
                cancelAllTasks();
            }
            broadcastLobby(TextFormat.RED + player.getName() + " desconectou.");
            updateActionBar();
        }
        setupMode.remove(uid);
        setupPos1.remove(uid);
    }

    // ======================== COLOR FORM ========================

    private void openColorForm(Player player) {
        UUID uid = player.getUniqueId();
        String currentColor = playerColors.get(uid);

        SimpleForm form = new SimpleForm("Selecionar Cor");

        for (int i = 0; i < COLORS.length; i++) {
            final int idx = i;
            boolean taken = playerColors.containsValue(COLORS[i]) && !COLORS[i].equals(currentColor);
            String btn = taken ? COLOR_DISPLAY[i] + " §7(Indisponivel)" : COLOR_DISPLAY[i];
            form.addButton(btn, p -> {
                String selectedColor = COLORS[idx];
                String oldColor = playerColors.get(p.getUniqueId());

                if (selectedColor.equals(oldColor)) {
                    p.sendMessage(TextFormat.YELLOW + "Voce ja esta com essa cor!");
                    return;
                }

                if (playerColors.containsValue(selectedColor)) {
                    p.sendMessage(TextFormat.RED + "Essa cor ja esta em uso!");
                    return;
                }

                playerColors.put(p.getUniqueId(), selectedColor);
                applySkin(p, selectedColor);
                p.sendMessage(TextFormat.GREEN + "Cor alterada para " + COLOR_DISPLAY[idx] + "§a!");
                broadcastLobby(TextFormat.YELLOW + p.getName() + " mudou a cor para " + COLOR_DISPLAY[idx]);
            });
        }

        form.send(player);
    }

    // ======================== ITEMS ========================

    private Item makeExitItem(String name, String tagKey) {
        Item item = Item.get("gugunet:exit_dye_imp", 0, 1);
        item.setCustomName(name);
        CompoundTag t = item.getNamedTag() != null ? item.getNamedTag() : new CompoundTag();
        t.putBoolean(tagKey, true);
        item.setNamedTag(t);
        return item;
    }

    private Item makeColorPickerItem() {
        Item item = Item.get("minecraft:paper", 0, 1);
        item.setCustomName("§bSelecionar Cor");
        CompoundTag t = item.getNamedTag() != null ? item.getNamedTag() : new CompoundTag();
        t.putBoolean("impostorColorPicker", true);
        item.setNamedTag(t);
        List<String> lore = new ArrayList<>();
        lore.add("§7Clique para escolher sua cor");
        item.setLore(lore.toArray(new String[0]));
        return item;
    }

    // ======================== UTILS ========================

    private void ensureWorldLoaded() {
        if (getServer().getLevelByName(worldName) == null) getServer().loadLevel(worldName);
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

    private void broadcastLobby(String msg) {
        lobbyPlayers.forEach(uid -> { Player p = getPlayer(uid); if (p != null) p.sendMessage(msg); });
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

    private boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = interactCooldown.get(uid);
        if (last != null && (now - last) < INTERACT_COOLDOWN_MS) return true;
        interactCooldown.put(uid, now);
        return false;
    }

    private void cancelAllTasks() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (actionBarTask != null) { actionBarTask.cancel(); actionBarTask = null; }
    }

    public void forceRemovePlayer(Player player) {
        UUID uid = player.getUniqueId();
        if (lobbyPlayers.contains(uid)) {
            leaveLobby(player);
        }
        setupMode.remove(uid);
        setupPos1.remove(uid);
    }
}
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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GugunetCore extends PluginBase implements Listener {

    private static final String MAIN_WORLD = "world";
    private final Map<UUID, TaskHandler> loadingPlayers = new HashMap<>();
    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 500;

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.saveDefaultConfig();

        createFlatWorld("spleef");
        createFlatWorld("pvp");
        createFlatWorld("get_arrow");

        this.getLogger().info("GugunetCore ativado!");
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
            }
        }, 3);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TaskHandler task = loadingPlayers.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (loadingPlayers.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            UUID uid = p.getUniqueId();
            if (loadingPlayers.containsKey(uid)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline() && isInMainLobby(player)) {
                giveLobbyClock(player);
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
        SimpleForm form = new SimpleForm("§6§lSelecionar Minigame", "§7Escolha um minigame para jogar!");
        form.addButton("§eSpleef\n§7§lEM BREVE", p -> {
            p.sendMessage(TextFormat.YELLOW + "Spleef em breve!");
        });
        form.addButton("§aCatch the Arrow\n§7§lEM BREVE", p -> {
            p.sendMessage(TextFormat.YELLOW + "Catch the Arrow em breve!");
        });
        form.addButton("§cBattle Royale\n§7Clique para entrar!", p -> {
            getServer().executeCommand(p, "pvp enter");
        });
        form.addButton("§7§lFechar", p -> {});
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

        return false;
    }
}
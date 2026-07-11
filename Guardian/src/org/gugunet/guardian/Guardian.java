package org.gugunet.guardian;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerFoodLevelChangeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;

public class Guardian extends PluginBase implements Listener {

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.saveDefaultConfig();
        this.getLogger().info("Guardian ativado!");
    }

    private boolean getWorldRule(String world, String rule, boolean defaultValue) {
        Config config = this.getConfig();
        String path = "worlds." + world + "." + rule;
        if (config.exists(path)) {
            return config.getBoolean(path);
        }
        return defaultValue;
    }

    // 1. Regra de PVP
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            String world = victim.getLevel().getFolderName();
            
            if (!getWorldRule(world, "pvp", true)) {
                event.setCancelled(true);
            }
        }
    }

    // 2. Regra de Fome (Hunger)
    @EventHandler
    public void onFoodLevelChange(PlayerFoodLevelChangeEvent event) {
        Player player = event.getPlayer();
        String world = player.getLevel().getFolderName();
        
        if (!getWorldRule(world, "hunger", true)) {
            event.setCancelled(true);
            // Não chamar setFood aqui pois causa loop infinito
        }
    }

    // 3. Regra de Dano Genérico (damage)
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Ignora PvP porque já é tratado pelo EntityDamageByEntityEvent
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String world = player.getLevel().getFolderName();
            
            if (!getWorldRule(world, "damage", true)) {
                event.setCancelled(true);
            }
        }
    }

    // 4. Regra de Quebrar Blocos (break)
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return; // Admin ignora a restrição
        
        String world = player.getLevel().getFolderName();
        if (!getWorldRule(world, "break", true)) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "Você não tem permissão para quebrar blocos neste mundo!");
        }
    }

    // 5. Regra de Colocar Blocos (place)
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return; // Admin ignora a restrição
        
        String world = player.getLevel().getFolderName();
        if (!getWorldRule(world, "place", true)) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "Você não tem permissão para colocar blocos neste mundo!");
        }
    }

    // 6. Regras de Abrir Baú (open_chest) e Usar Itens (use_item)
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return; // Admin ignora as restrições

        Block block = event.getBlock();
        if (block == null) return;

        String world = player.getLevel().getFolderName();
        String blockId = block.getId();

        if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            // Verifica se é um baú ou container
            boolean isChest = blockId.contains("chest") || blockId.contains("shulker") || blockId.contains("barrel") || 
                             blockId.contains("hopper") || blockId.contains("furnace") || blockId.contains("dispenser") || blockId.contains("dropper");

            if (isChest) {
                if (!getWorldRule(world, "open_chest", true)) {
                    event.setCancelled(true);
                    player.sendMessage(TextFormat.RED + "Você não tem permissão para abrir baús neste mundo!");
                    return;
                }
            }

            // Verifica se é uma interação de uso de item (portas, alçapões, botões ou uso geral de itens)
            if (!getWorldRule(world, "use_item", true)) {
                event.setCancelled(true);
                // Não envia spam de mensagem para não incomodar no chat
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(TextFormat.RED + "Você não tem permissão para usar este comando.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("guardian")) {
            if (args.length == 0 && sender instanceof Player) {
                // Abre o Form UI de configuração para o jogador
                openGuardianWorldSelectForm((Player) sender);
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(TextFormat.RED + "Uso: /guardian <mundo> <regra> <true|false>");
                sender.sendMessage(TextFormat.YELLOW + "Regras disponíveis: pvp, hunger, damage, break, place, open_chest, use_item");
                sender.sendMessage(TextFormat.YELLOW + "Ou digite /guardian no chat do jogo para abrir a interface gráfica.");
                return true;
            }

            String world = args[0];
            String rule = args[1].toLowerCase();
            String valStr = args[2];

            if (!rule.equals("pvp") && !rule.equals("hunger") && !rule.equals("damage") && 
                !rule.equals("break") && !rule.equals("place") && !rule.equals("open_chest") && !rule.equals("use_item")) {
                sender.sendMessage(TextFormat.RED + "Regra inválida! Use uma de: pvp, hunger, damage, break, place, open_chest, use_item");
                return true;
            }

            boolean val = Boolean.parseBoolean(valStr);

            Config config = this.getConfig();
            config.set("worlds." + world + "." + rule, val);
            config.save();

            sender.sendMessage(TextFormat.GREEN + "Regra \"" + rule + "\" definida para " + val + " no mundo \"" + world + "\"!");
            return true;
        }

        return false;
    }

    private void openGuardianWorldSelectForm(Player player) {
        cn.nukkit.form.window.SimpleForm form = new cn.nukkit.form.window.SimpleForm("§aConfiguração do Guardian", "Selecione o mundo para configurar as regras:");
        
        List<String> worldNames = new ArrayList<>();
        for (Level lvl : this.getServer().getLevels().values()) {
            worldNames.add(lvl.getFolderName());
        }

        for (String name : worldNames) {
            form.addButton(name, p -> {
                openGuardianRulesForm(p, name);
            });
        }

        form.send(player);
    }

    private void openGuardianRulesForm(Player player, String worldName) {
        cn.nukkit.form.window.CustomForm form = new cn.nukkit.form.window.CustomForm("§6Guardian: " + worldName)
            .addToggle("Permitir PVP", getWorldRule(worldName, "pvp", true))
            .addToggle("Permitir Fome (Hunger)", getWorldRule(worldName, "hunger", true))
            .addToggle("Permitir Dano (Generico)", getWorldRule(worldName, "damage", true))
            .addToggle("Permitir Quebrar Blocos", getWorldRule(worldName, "break", true))
            .addToggle("Permitir Colocar Blocos", getWorldRule(worldName, "place", true))
            .addToggle("Permitir Abrir Baús", getWorldRule(worldName, "open_chest", true))
            .addToggle("Permitir Usar Itens (Portas, Alçapões)", getWorldRule(worldName, "use_item", true))
            .onSubmit((p, response) -> {
                boolean pvp = response.getToggleResponse(0);
                boolean hunger = response.getToggleResponse(1);
                boolean damage = response.getToggleResponse(2);
                boolean breakBlocks = response.getToggleResponse(3);
                boolean placeBlocks = response.getToggleResponse(4);
                boolean openChest = response.getToggleResponse(5);
                boolean useItem = response.getToggleResponse(6);

                Config config = this.getConfig();
                String path = "worlds." + worldName + ".";
                config.set(path + "pvp", pvp);
                config.set(path + "hunger", hunger);
                config.set(path + "damage", damage);
                config.set(path + "break", breakBlocks);
                config.set(path + "place", placeBlocks);
                config.set(path + "open_chest", openChest);
                config.set(path + "use_item", useItem);
                config.save();

                p.sendMessage(TextFormat.GREEN + "Regras do mundo \"" + worldName + "\" configuradas com sucesso!");
                p.sendMessage(TextFormat.GRAY + "PVP: " + pvp + " | Fome: " + hunger + " | Quebrar: " + breakBlocks + " | Colocar: " + placeBlocks);
            });

        form.send(player);
    }
}

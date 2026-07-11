package org.gugunet.miniworldedit;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockProperties;
import cn.nukkit.block.BlockState;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.TaskHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MiniWorldEdit extends PluginBase implements Listener {

    private static MiniWorldEdit instance;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    // Cooldown de interact (ms) para evitar disparos multiplos com 1 clique
    private final Map<UUID, Long> interactCooldown = new HashMap<>();
    private static final long INTERACT_COOLDOWN_MS = 1000; // 1 segundo

    // Limits to prevent server crashes
    private static final int MAX_BLOCKS_LIMIT = 500000;
    private static final int BATCH_SIZE = 4000; // Blocks processed per tick

    public static MiniWorldEdit getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        
        // Start selection border particle task running every 1 second (20 ticks)
        this.getServer().getScheduler().scheduleRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers().values()) {
                    if (player.isOp()) {
                        PlayerSession session = getSession(player);
                        if (session.getPos1() != null && session.getPos2() != null) {
                            Item item = player.getInventory().getItemInHand();
                            boolean holdsWand = item != null && (
                                (item.getNamedTag() != null && item.getNamedTag().contains("edittool") && item.getNamedTag().getBoolean("edittool")) ||
                                "§6Machado de Edição".equals(item.getCustomName())
                            );
                            if (holdsWand) {
                                drawSelectionBox(player, session.getPos1(), session.getPos2());
                            }
                        }
                    }
                }
            }
        }, 20);

        this.getLogger().info("MiniWorldEdit ativado com sucesso!");
    }

    public PlayerSession getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), k -> new PlayerSession());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            if (session.isProcessing()) {
                session.getCurrentTask().cancel();
            }
            // Auto-confirm active preview on quit so it's not lost
            if (session.getLastPreviewChanges() != null) {
                session.getHistory().addChange(session.getLastPreviewChanges());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Item item = player.getInventory().getItemInHand();

        if (item != null) {
            // Check for Wand
            boolean holdsWand = (item.getNamedTag() != null && item.getNamedTag().contains("edittool") && item.getNamedTag().getBoolean("edittool")) ||
                "§6Machado de Edição".equals(item.getCustomName());

            if (holdsWand) {
                event.setCancelled(true);
                Block block = event.getBlock();
                Position pos = new Position(block.getX(), block.getY(), block.getZ(), block.getLevel());
                PlayerSession session = getSession(player);
                session.setPos1(pos);
                player.sendMessage(TextFormat.GREEN + "Posição 1 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ")");
                return;
            }

            // Check for Adjust Stick
            boolean holdsAdjust = (item.getNamedTag() != null && item.getNamedTag().contains("adjuststick") && item.getNamedTag().getBoolean("adjuststick")) ||
                "§bGraveto de Ajuste".equals(item.getCustomName());

            if (holdsAdjust) {
                event.setCancelled(true);
                if (!player.isOp()) return;
                
                PlayerSession session = getSession(player);
                executeAdjustAction(player, session);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (item == null) return;

        // 1. Machado de Edição (Wand) - Botão Direito para definir Posição 2
        boolean holdsWand = (item.getNamedTag() != null && item.getNamedTag().contains("edittool") && item.getNamedTag().getBoolean("edittool")) ||
            "§6Machado de Edição".equals(item.getCustomName());

        if (holdsWand) {
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                // Cooldown: ignora se clicado em menos de 1s
                if (isOnInteractCooldown(player)) return;
                Block block = event.getBlock();
                Position pos = new Position(block.getX(), block.getY(), block.getZ(), block.getLevel());
                PlayerSession session = getSession(player);
                session.setPos2(pos);
                player.sendMessage(TextFormat.GREEN + "Posição 2 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ")");
            }
            return;
        }

        // 2. Graveto de Depuração (Debug Stick)
        boolean holdsDebug = (item.getNamedTag() != null && item.getNamedTag().contains("debugstick") && item.getNamedTag().getBoolean("debugstick")) ||
            "§bGraveto de Depuração".equals(item.getCustomName());

        if (holdsDebug) {
            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                if (!player.isOp()) return;
                if (isOnInteractCooldown(player)) return;

                Block block = event.getBlock();
                BlockProperties properties = block.getProperties();
                List<cn.nukkit.block.property.type.BlockPropertyType.BlockPropertyValue<?, ?, ?>> values = block.getPropertyValues();

                if (values == null || values.isEmpty()) {
                    player.sendActionBar(TextFormat.RED + "Este bloco não possui propriedades.");
                    return;
                }

                PlayerSession session = getSession(player);
                int selectedPropIndex = session.getSelectedPropertyIndex(block.getId());
                if (selectedPropIndex >= values.size()) {
                    selectedPropIndex = 0;
                }

                cn.nukkit.block.property.type.BlockPropertyType.BlockPropertyValue<?, ?, ?> selectedVal = values.get(selectedPropIndex);
                cn.nukkit.block.property.type.BlockPropertyType<?> propType = selectedVal.getPropertyType();

                if (player.isSneaking()) {
                    // Seleciona a próxima propriedade
                    selectedPropIndex = (selectedPropIndex + 1) % values.size();
                    session.setSelectedPropertyIndex(block.getId(), selectedPropIndex);

                    cn.nukkit.block.property.type.BlockPropertyType.BlockPropertyValue<?, ?, ?> newSelectedVal = values.get(selectedPropIndex);
                    player.sendActionBar(TextFormat.AQUA + "Propriedade selecionada: " + TextFormat.YELLOW + newSelectedVal.getPropertyType().getName());
                } else {
                    // Altera o valor da propriedade selecionada
                    List<?> validValues = propType.getValidValues();
                    Object activeValue = selectedVal.getValue();
                    int valueIndex = -1;
                    for (int i = 0; i < validValues.size(); i++) {
                        if (validValues.get(i).equals(activeValue)) {
                            valueIndex = i;
                            break;
                        }
                    }
                    if (valueIndex != -1) {
                        int nextValueIndex = (valueIndex + 1) % validValues.size();
                        Object nextValue = validValues.get(nextValueIndex);

                        cn.nukkit.block.property.type.BlockPropertyType.BlockPropertyValue<?, ?, ?> newValProp = propType.tryCreateValue(nextValue);

                        BlockState state = block.getBlockState();
                        BlockState newState = state.setPropertyValue(properties, newValProp);

                        Block newBlock = Block.get(newState);
                        block.getLevel().setBlock(block.getFloorX(), block.getFloorY(), block.getFloorZ(), newBlock, true, true);

                        player.sendActionBar(TextFormat.GREEN + propType.getName() + " alterado para: " + TextFormat.YELLOW + nextValue.toString());
                    }
                }
            }
            return;
        }

        // 3. Graveto de Ajuste (Adjust Stick) - Botão Direito para abrir Forms Menu
        boolean holdsAdjust = (item.getNamedTag() != null && item.getNamedTag().contains("adjuststick") && item.getNamedTag().getBoolean("adjuststick")) ||
            "§bGraveto de Ajuste".equals(item.getCustomName());

        if (holdsAdjust) {
            event.setCancelled(true);
            if (!player.isOp()) return;

            if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
                if (isOnInteractCooldown(player)) return;
                PlayerSession session = getSession(player);
                openAdjustStickMenu(player, session);
            }
        }
    }

    /**
     * Verifica se o jogador está em cooldown de interação.
     * Registra o tempo atual e retorna true se ainda está dentro do período de cooldown.
     */
    private boolean isOnInteractCooldown(Player player) {
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = interactCooldown.get(uid);
        if (last != null && (now - last) < INTERACT_COOLDOWN_MS) {
            return true; // ainda em cooldown, ignora o evento
        }
        interactCooldown.put(uid, now);
        return false;
    }

    private void openAdjustStickMenu(Player player, PlayerSession session) {
        List<String> actions = java.util.Arrays.asList("Mover", "Rotacionar", "Sobrescrever Ar");
        cn.nukkit.form.window.CustomForm form = new cn.nukkit.form.window.CustomForm("§bAjustador de Seleção / Paste")
            .addDropdown("Ação", actions, session.getAdjustActionIndex())
            .addSlider("Distância (para Mover)", 1.0f, 50.0f, 1, (float) session.getAdjustDistance())
            .addToggle("Sobrescrever Ar (no colar)", session.isPasteAir())
            .onSubmit((p, response) -> {
                int actionIndex = response.getDropdownResponse(0).elementId();
                int distance = (int) response.getSliderResponse(1);
                boolean pasteAir = response.getToggleResponse(2);

                session.setAdjustActionIndex(actionIndex);
                session.setAdjustDistance(distance);
                
                boolean oldPasteAir = session.isPasteAir();
                session.setPasteAir(pasteAir);

                p.sendMessage(TextFormat.GREEN + "Configurações do Adjust Stick salvas!");
                p.sendMessage(TextFormat.AQUA + "Ação: " + actions.get(actionIndex) + " | Distância: " + distance + " | Sobrescrever Ar: " + pasteAir);

                // Recreate preview if pasteAir toggle changed and preview is active
                if (oldPasteAir != pasteAir && session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
                    recreatePastePreview(p, session);
                }
            });
        form.send(player);
    }

    private void executeAdjustAction(Player player, PlayerSession session) {
        if (session.isProcessing()) {
            player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
            return;
        }

        int actionIndex = session.getAdjustActionIndex();
        if (actionIndex == 0) { // Mover
            cn.nukkit.math.BlockFace face = player.getDirection();
            int dist = session.getAdjustDistance();
            int dx = face.getXOffset() * dist;
            int dy = face.getYOffset() * dist;
            int dz = face.getZOffset() * dist;

            if (session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
                // Moving active paste preview
                Position oldBase = session.getPasteBase();
                if (oldBase != null) {
                    // Revert old preview
                    for (BlockChange change : session.getLastPreviewChanges()) {
                        change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
                    }
                    session.setLastPreviewChanges(null);

                    // Translate base
                    Position newBase = new Position(oldBase.getX() + dx, oldBase.getY() + dy, oldBase.getZ() + dz, oldBase.getLevel());
                    session.setPasteBase(newBase);

                    // Re-apply preview
                    Clipboard clipboard = session.getPasteClipboard();
                    if (clipboard != null) {
                        Block[][][] blocks = clipboard.getBlocks();
                        CompoundTag[][][] tiles = clipboard.getTiles();
                        List<BlockSetTask.PendingChange> pending = new ArrayList<>();

                        int baseX = newBase.getFloorX();
                        int baseY = newBase.getFloorY();
                        int baseZ = newBase.getFloorZ();

                        for (int x = 0; x < clipboard.getWidth(); x++) {
                            for (int y = 0; y < clipboard.getHeight(); y++) {
                                for (int z = 0; z < clipboard.getLength(); z++) {
                                    Block blockToPlace = blocks[x][y][z];
                                    if (!session.isPasteAir() && blockToPlace.getId().equals("minecraft:air")) {
                                        continue;
                                    }
                                    pending.add(new BlockSetTask.PendingChange(baseX + x, baseY + y, baseZ + z, blockToPlace, tiles[x][y][z]));
                                }
                            }
                        }

                        // Shift selection boundaries
                        session.setPos1(new Position(baseX, baseY, baseZ, player.getLevel()));
                        session.setPos2(new Position(baseX + clipboard.getWidth() - 1, baseY + clipboard.getHeight() - 1, baseZ + clipboard.getLength() - 1, player.getLevel()));

                        player.sendMessage(TextFormat.YELLOW + "Movendo preview para " + face.name() + " por " + dist + " blocos...");
                        BlockSetTask task = new BlockSetTask(player, player.getLevel(), pending, BATCH_SIZE, false, true, "//paste");
                        TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
                        task.setHandler(handler);
                        session.setCurrentTask(handler);
                    }
                }
            } else {
                // No active preview: shift actual blocks in world
                if (session.getPos1() == null || session.getPos2() == null) {
                    player.sendMessage(TextFormat.RED + "Selecione uma área ou cole (paste) primeiro para mover.");
                    return;
                }
                moveSelectionInWorld(player, session, dx, dy, dz, face, dist);
            }
        } else if (actionIndex == 1) { // Rotacionar
            if (session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
                Clipboard clipboard = session.getPasteClipboard();
                Position base = session.getPasteBase();
                if (clipboard != null && base != null) {
                    // Revert preview
                    for (BlockChange change : session.getLastPreviewChanges()) {
                        change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
                    }
                    session.setLastPreviewChanges(null);

                    // Rotate clipboard 90 degrees clockwise
                    Clipboard rotated = clipboard.rotate90();
                    session.setPasteClipboard(rotated);

                    // Re-apply preview
                    Block[][][] blocks = rotated.getBlocks();
                    CompoundTag[][][] tiles = rotated.getTiles();
                    List<BlockSetTask.PendingChange> pending = new ArrayList<>();

                    int baseX = base.getFloorX();
                    int baseY = base.getFloorY();
                    int baseZ = base.getFloorZ();

                    for (int x = 0; x < rotated.getWidth(); x++) {
                        for (int y = 0; y < rotated.getHeight(); y++) {
                            for (int z = 0; z < rotated.getLength(); z++) {
                                Block blockToPlace = blocks[x][y][z];
                                if (!session.isPasteAir() && blockToPlace.getId().equals("minecraft:air")) {
                                    continue;
                                }
                                pending.add(new BlockSetTask.PendingChange(baseX + x, baseY + y, baseZ + z, blockToPlace, tiles[x][y][z]));
                            }
                        }
                    }

                    // Update selection bounds to rotated bounds
                    session.setPos1(new Position(baseX, baseY, baseZ, player.getLevel()));
                    session.setPos2(new Position(baseX + rotated.getWidth() - 1, baseY + rotated.getHeight() - 1, baseZ + rotated.getLength() - 1, player.getLevel()));

                    player.sendMessage(TextFormat.YELLOW + "Rotacionando preview...");
                    BlockSetTask task = new BlockSetTask(player, player.getLevel(), pending, BATCH_SIZE, false, true, "//paste");
                    TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
                    task.setHandler(handler);
                    session.setCurrentTask(handler);
                }
            } else {
                player.sendMessage(TextFormat.RED + "A rotação com adjuststick só é suportada durante o preview de colagem (paste)!");
            }
        } else if (actionIndex == 2) { // Sobrescrever Ar
            boolean nextVal = !session.isPasteAir();
            session.setPasteAir(nextVal);
            player.sendMessage(TextFormat.GREEN + "Sobrescrever Ar alternado para: " + TextFormat.YELLOW + nextVal);
            if (session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
                recreatePastePreview(player, session);
            }
        }
    }

    private void recreatePastePreview(Player player, PlayerSession session) {
        Position base = session.getPasteBase();
        Clipboard clipboard = session.getPasteClipboard();
        if (base != null && clipboard != null) {
            // Revert preview
            for (BlockChange change : session.getLastPreviewChanges()) {
                change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
            }
            session.setLastPreviewChanges(null);

            // Re-apply preview with new settings
            Block[][][] blocks = clipboard.getBlocks();
            CompoundTag[][][] tiles = clipboard.getTiles();
            List<BlockSetTask.PendingChange> pending = new ArrayList<>();

            int baseX = base.getFloorX();
            int baseY = base.getFloorY();
            int baseZ = base.getFloorZ();

            for (int x = 0; x < clipboard.getWidth(); x++) {
                for (int y = 0; y < clipboard.getHeight(); y++) {
                    for (int z = 0; z < clipboard.getLength(); z++) {
                        Block blockToPlace = blocks[x][y][z];
                        if (!session.isPasteAir() && blockToPlace.getId().equals("minecraft:air")) {
                            continue;
                        }
                        pending.add(new BlockSetTask.PendingChange(baseX + x, baseY + y, baseZ + z, blockToPlace, tiles[x][y][z]));
                    }
                }
            }

            BlockSetTask task = new BlockSetTask(player, player.getLevel(), pending, BATCH_SIZE, false, true, "//paste");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
        }
    }

    private void moveSelectionInWorld(Player player, PlayerSession session, int dx, int dy, int dz, cn.nukkit.math.BlockFace face, int dist) {
        Position pos1 = session.getPos1();
        Position pos2 = session.getPos2();

        int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
        int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
        int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
        int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
        int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
        int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        int total = width * height * length;
        if (total > MAX_BLOCKS_LIMIT) {
            player.sendMessage(TextFormat.RED + "A seleção é muito grande para mover (" + total + " blocos).");
            return;
        }

        Level level = pos1.getLevel();
        Block[][][] blocks = new Block[width][height][length];
        CompoundTag[][][] tiles = new CompoundTag[width][height][length];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int cx = minX + x;
                    int cy = minY + y;
                    int cz = minZ + z;
                    blocks[x][y][z] = level.getBlock(cx, cy, cz).clone();
                    BlockEntity tile = level.getBlockEntity(new cn.nukkit.math.Vector3(cx, cy, cz));
                    if (tile != null) {
                        tiles[x][y][z] = tile.namedTag.copy();
                    }
                }
            }
        }

        List<BlockSetTask.PendingChange> pending = new ArrayList<>();
        Block air = Block.get("minecraft:air");

        // Clear old blocks to air
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pending.add(new BlockSetTask.PendingChange(x, y, z, air));
                }
            }
        }

        // Place at new position
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    int nx = minX + x + dx;
                    int ny = minY + y + dy;
                    int nz = minZ + z + dz;
                    pending.add(new BlockSetTask.PendingChange(nx, ny, nz, blocks[x][y][z], tiles[x][y][z]));
                }
            }
        }

        // Shift selection coordinates in session
        session.setPos1(new Position(pos1.getX() + dx, pos1.getY() + dy, pos1.getZ() + dz, level));
        session.setPos2(new Position(pos2.getX() + dx, pos2.getY() + dy, pos2.getZ() + dz, level));

        player.sendMessage(TextFormat.YELLOW + "Movendo seleção para " + face.name() + " por " + dist + " blocos...");
        BlockSetTask task = new BlockSetTask(player, level, pending, BATCH_SIZE, false, "//move");
        TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
        task.setHandler(handler);
        session.setCurrentTask(handler);
    }

    private void confirmPendingPreview(PlayerSession session) {
        if (session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
            session.getHistory().addChange(session.getLastPreviewChanges());
            session.setLastPreviewChanges(null);
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
        
        // Normalize double slash commands
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        PlayerSession session = getSession(player);

        if (cmd.equals("edittool") || cmd.equals("wand")) {
            Item tool = Item.get(Item.WOODEN_AXE);
            tool.setCustomName("§6Machado de Edição");
            
            CompoundTag tag = tool.getNamedTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.putBoolean("edittool", true);
            tool.setNamedTag(tag);

            player.getInventory().addItem(tool);
            player.sendMessage(TextFormat.GREEN + "Você recebeu o Machado de Edição (botão esquerdo/quebrar = pos 1, botão direito/interagir = pos 2)!");
            return true;
        }

        if (cmd.equals("debugstick")) {
            Item stick = Item.get(Item.STICK);
            stick.setCustomName("§bGraveto de Depuração");
            
            CompoundTag tag = stick.getNamedTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.putBoolean("debugstick", true);
            stick.setNamedTag(tag);

            player.getInventory().addItem(stick);
            player.sendMessage(TextFormat.GREEN + "Você recebeu o Graveto de Depuração (botão direito = alterar valor, agachar + botão direito = selecionar propriedade)!");
            return true;
        }

        if (cmd.equals("adjuststick")) {
            Item stick = Item.get(Item.STICK);
            stick.setCustomName("§bGraveto de Ajuste");
            
            CompoundTag tag = stick.getNamedTag();
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.putBoolean("adjuststick", true);
            stick.setNamedTag(tag);

            player.getInventory().addItem(stick);
            player.sendMessage(TextFormat.GREEN + "Você recebeu o Graveto de Ajuste (botão direito/interagir = configurar/menu, botão esquerdo/quebrar = executar ação)!");
            return true;
        }

        if (cmd.equals("confirm")) {
            if (session.getLastPreviewChanges() == null || session.getLastPreviewChanges().isEmpty()) {
                player.sendMessage(TextFormat.RED + "Não há nenhum preview de colar pendente.");
                return true;
            }
            session.getHistory().addChange(session.getLastPreviewChanges());
            session.setLastPreviewChanges(null);
            player.sendMessage(TextFormat.GREEN + "Colagem confirmada com sucesso!");
            return true;
        }

        if (cmd.equals("cancel")) {
            if (session.getLastPreviewChanges() == null || session.getLastPreviewChanges().isEmpty()) {
                player.sendMessage(TextFormat.RED + "Não há nenhum preview de colar pendente.");
                return true;
            }
            for (BlockChange change : session.getLastPreviewChanges()) {
                change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
            }
            session.setLastPreviewChanges(null);
            player.sendMessage(TextFormat.GREEN + "Colagem cancelada e área original restaurada!");
            return true;
        }

        if (cmd.equals("pos1")) {
            Position pos = new Position(player.getFloorX(), player.getFloorY(), player.getFloorZ(), player.getLevel());
            session.setPos1(pos);
            player.sendMessage(TextFormat.GREEN + "Posição 1 definida para a sua localização (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ").");
            return true;
        }

        if (cmd.equals("pos2")) {
            Position pos = new Position(player.getFloorX(), player.getFloorY(), player.getFloorZ(), player.getLevel());
            session.setPos2(pos);
            player.sendMessage(TextFormat.GREEN + "Posição 2 definida para a sua localização (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ").");
            return true;
        }

        if (cmd.equals("hpos1")) {
            Block target = player.getTargetBlock(100);
            if (target == null) {
                player.sendMessage(TextFormat.RED + "Nenhum bloco visível encontrado (máx 100 blocos).");
                return true;
            }
            Position pos = new Position(target.getX(), target.getY(), target.getZ(), target.getLevel());
            session.setPos1(pos);
            player.sendMessage(TextFormat.GREEN + "Posição 1 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ").");
            return true;
        }

        if (cmd.equals("hpos2")) {
            Block target = player.getTargetBlock(100);
            if (target == null) {
                player.sendMessage(TextFormat.RED + "Nenhum bloco visível encontrado (máx 100 blocos).");
                return true;
            }
            Position pos = new Position(target.getX(), target.getY(), target.getZ(), target.getLevel());
            session.setPos2(pos);
            player.sendMessage(TextFormat.GREEN + "Posição 2 definida para (" + pos.getFloorX() + ", " + pos.getFloorY() + ", " + pos.getFloorZ() + ").");
            return true;
        }

        if (cmd.equals("desel") || cmd.equals("deselect")) {
            session.setPos1(null);
            session.setPos2(null);
            player.sendMessage(TextFormat.GREEN + "Seleção limpa com sucesso.");
            return true;
        }

        if (cmd.equals("size")) {
            if (session.getPos1() == null || session.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Defina as posições 1 e 2 primeiro.");
                return true;
            }
            Position p1 = session.getPos1();
            Position p2 = session.getPos2();
            if (p1.getLevel() != p2.getLevel()) {
                player.sendMessage(TextFormat.RED + "As posições devem estar no mesmo mundo.");
                return true;
            }
            int dx = Math.abs(p1.getFloorX() - p2.getFloorX()) + 1;
            int dy = Math.abs(p1.getFloorY() - p2.getFloorY()) + 1;
            int dz = Math.abs(p1.getFloorZ() - p2.getFloorZ()) + 1;
            player.sendMessage(TextFormat.GREEN + "Tamanho da seleção: " + dx + "x" + dy + "x" + dz + " (" + (dx * dy * dz) + " blocos).");
            return true;
        }

        if (cmd.equals("set")) {
            if (args.length < 1) {
                player.sendMessage(TextFormat.RED + "Uso correto: //set <nome_do_bloco>");
                return true;
            }

            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }

            confirmPendingPreview(session);

            if (session.getPos1() == null || session.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Selecione as duas posições primeiro usando o Machado de Edição ou comandos.");
                return true;
            }

            Position pos1 = session.getPos1();
            Position pos2 = session.getPos2();

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

            int total = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (total > MAX_BLOCKS_LIMIT) {
                player.sendMessage(TextFormat.RED + "A seleção é muito grande (" + total + " blocos). Limite: " + MAX_BLOCKS_LIMIT);
                return true;
            }

            player.sendMessage(TextFormat.YELLOW + "Modificando " + total + " blocos...");

            List<BlockSetTask.PendingChange> pending = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pending.add(new BlockSetTask.PendingChange(x, y, z, block));
                    }
                }
            }

            BlockSetTask task = new BlockSetTask(player, pos1.getLevel(), pending, BATCH_SIZE, false, "//set");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("replace")) {
            if (args.length < 1) {
                player.sendMessage(TextFormat.RED + "Uso correto: //replace <novo_bloco> OU //replace <bloco_antigo> <novo_bloco>");
                return true;
            }

            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }

            confirmPendingPreview(session);

            if (session.getPos1() == null || session.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Selecione as duas posições primeiro.");
                return true;
            }

            Position pos1 = session.getPos1();
            Position pos2 = session.getPos2();

            if (pos1.getLevel() != pos2.getLevel()) {
                player.sendMessage(TextFormat.RED + "As posições devem estar no mesmo mundo.");
                return true;
            }

            Block oldBlockFilter = null;
            Block newBlock = null;

            try {
                if (args.length == 1) {
                    String newName = args[0].contains(":") ? args[0] : "minecraft:" + args[0];
                    newBlock = Block.get(newName);
                } else {
                    String oldName = args[0].contains(":") ? args[0] : "minecraft:" + args[0];
                    String newName = args[1].contains(":") ? args[1] : "minecraft:" + args[1];
                    oldBlockFilter = Block.get(oldName);
                    newBlock = Block.get(newName);
                }
            } catch (Exception e) {
                player.sendMessage(TextFormat.RED + "Erro ao processar blocos. Verifique a escrita.");
                return true;
            }

            if (newBlock == null) {
                player.sendMessage(TextFormat.RED + "Bloco de destino inválido.");
                return true;
            }

            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            int totalVolume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (totalVolume > MAX_BLOCKS_LIMIT) {
                player.sendMessage(TextFormat.RED + "A seleção é muito grande (" + totalVolume + " blocos). Limite: " + MAX_BLOCKS_LIMIT);
                return true;
            }

            List<BlockSetTask.PendingChange> pending = new ArrayList<>();
            Level level = pos1.getLevel();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block current = level.getBlock(x, y, z);
                        if (oldBlockFilter != null) {
                            if (current.getId().equals(oldBlockFilter.getId())) {
                                pending.add(new BlockSetTask.PendingChange(x, y, z, newBlock));
                            }
                        } else {
                            if (!current.getId().equals("minecraft:air")) {
                                pending.add(new BlockSetTask.PendingChange(x, y, z, newBlock));
                            }
                        }
                    }
                }
            }

            if (pending.isEmpty()) {
                player.sendMessage(TextFormat.YELLOW + "Nenhum bloco correspondente encontrado para substituição.");
                return true;
            }

            player.sendMessage(TextFormat.YELLOW + "Substituindo " + pending.size() + " blocos...");
            BlockSetTask task = new BlockSetTask(player, level, pending, BATCH_SIZE, false, "//replace");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("undo")) {
            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }

            confirmPendingPreview(session);

            List<BlockChange> changes = session.getHistory().getUndo();
            if (changes == null || changes.isEmpty()) {
                player.sendMessage(TextFormat.RED + "Nenhuma alteração para desfazer.");
                return true;
            }

            player.sendMessage(TextFormat.YELLOW + "Desfazendo " + changes.size() + " blocos...");
            List<BlockSetTask.PendingChange> pending = new ArrayList<>();
            for (BlockChange c : changes) {
                pending.add(new BlockSetTask.PendingChange(c.getX(), c.getY(), c.getZ(), c.getBeforeBlock()));
            }

            BlockSetTask task = new BlockSetTask(player, changes.get(0).getLevel(), pending, BATCH_SIZE, true, "//undo");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("redo")) {
            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }

            confirmPendingPreview(session);

            List<BlockChange> changes = session.getHistory().getRedo();
            if (changes == null || changes.isEmpty()) {
                player.sendMessage(TextFormat.RED + "Nenhuma alteração para refazer.");
                return true;
            }

            player.sendMessage(TextFormat.YELLOW + "Refazendo " + changes.size() + " blocos...");
            List<BlockSetTask.PendingChange> pending = new ArrayList<>();
            for (BlockChange c : changes) {
                pending.add(new BlockSetTask.PendingChange(c.getX(), c.getY(), c.getZ(), c.getAfterBlock()));
            }

            BlockSetTask task = new BlockSetTask(player, changes.get(0).getLevel(), pending, BATCH_SIZE, true, "//redo");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("copy")) {
            if (session.getPos1() == null || session.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Selecione as duas posições primeiro.");
                return true;
            }
            Position pos1 = session.getPos1();
            Position pos2 = session.getPos2();
            if (pos1.getLevel() != pos2.getLevel()) {
                player.sendMessage(TextFormat.RED + "As posições devem estar no mesmo mundo.");
                return true;
            }

            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            int dx = maxX - minX + 1;
            int dy = maxY - minY + 1;
            int dz = maxZ - minZ + 1;
            int total = dx * dy * dz;

            if (total > MAX_BLOCKS_LIMIT) {
                player.sendMessage(TextFormat.RED + "A seleção é muito grande para copiar (" + total + " blocos). Limite: " + MAX_BLOCKS_LIMIT);
                return true;
            }

            Block[][][] blocks = new Block[dx][dy][dz];
            CompoundTag[][][] tiles = new CompoundTag[dx][dy][dz];
            Level level = pos1.getLevel();

            for (int x = 0; x < dx; x++) {
                for (int y = 0; y < dy; y++) {
                    for (int z = 0; z < dz; z++) {
                        int cx = minX + x;
                        int cy = minY + y;
                        int cz = minZ + z;
                        blocks[x][y][z] = level.getBlock(cx, cy, cz).clone();
                        BlockEntity tile = level.getBlockEntity(new cn.nukkit.math.Vector3(cx, cy, cz));
                        if (tile != null) {
                            tiles[x][y][z] = tile.namedTag.copy();
                        }
                    }
                }
            }

            int offsetX = minX - player.getFloorX();
            int offsetY = minY - player.getFloorY();
            int offsetZ = minZ - player.getFloorZ();

            session.setClipboard(new Clipboard(blocks, tiles, offsetX, offsetY, offsetZ));
            player.sendMessage(TextFormat.GREEN + "Área copiada com sucesso (" + total + " blocos).");
            return true;
        }

        if (cmd.equals("cut")) {
            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }
            if (session.getPos1() == null || session.getPos2() == null) {
                player.sendMessage(TextFormat.RED + "Selecione as duas posições primeiro.");
                return true;
            }
            Position pos1 = session.getPos1();
            Position pos2 = session.getPos2();
            if (pos1.getLevel() != pos2.getLevel()) {
                player.sendMessage(TextFormat.RED + "As posições devem estar no mesmo mundo.");
                return true;
            }

            confirmPendingPreview(session);

            int minX = Math.min(pos1.getFloorX(), pos2.getFloorX());
            int maxX = Math.max(pos1.getFloorX(), pos2.getFloorX());
            int minY = Math.min(pos1.getFloorY(), pos2.getFloorY());
            int maxY = Math.max(pos1.getFloorY(), pos2.getFloorY());
            int minZ = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
            int maxZ = Math.max(pos1.getFloorZ(), pos2.getFloorZ());

            int dx = maxX - minX + 1;
            int dy = maxY - minY + 1;
            int dz = maxZ - minZ + 1;
            int total = dx * dy * dz;

            if (total > MAX_BLOCKS_LIMIT) {
                player.sendMessage(TextFormat.RED + "A seleção é muito grande para cortar (" + total + " blocos). Limite: " + MAX_BLOCKS_LIMIT);
                return true;
            }

            // 1. Copy
            Block[][][] blocks = new Block[dx][dy][dz];
            CompoundTag[][][] tiles = new CompoundTag[dx][dy][dz];
            Level level = pos1.getLevel();

            for (int x = 0; x < dx; x++) {
                for (int y = 0; y < dy; y++) {
                    for (int z = 0; z < dz; z++) {
                        int cx = minX + x;
                        int cy = minY + y;
                        int cz = minZ + z;
                        blocks[x][y][z] = level.getBlock(cx, cy, cz).clone();
                        BlockEntity tile = level.getBlockEntity(new cn.nukkit.math.Vector3(cx, cy, cz));
                        if (tile != null) {
                            tiles[x][y][z] = tile.namedTag.copy();
                        }
                    }
                }
            }
            int offsetX = minX - player.getFloorX();
            int offsetY = minY - player.getFloorY();
            int offsetZ = minZ - player.getFloorZ();
            session.setClipboard(new Clipboard(blocks, tiles, offsetX, offsetY, offsetZ));

            // 2. Clear blocks to air
            player.sendMessage(TextFormat.YELLOW + "Cortando " + total + " blocos...");
            List<BlockSetTask.PendingChange> pending = new ArrayList<>();
            Block air = Block.get("minecraft:air");
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pending.add(new BlockSetTask.PendingChange(x, y, z, air));
                    }
                }
            }

            BlockSetTask task = new BlockSetTask(player, level, pending, BATCH_SIZE, false, "//cut");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("paste")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("confirm")) {
                if (session.getLastPreviewChanges() == null || session.getLastPreviewChanges().isEmpty()) {
                    player.sendMessage(TextFormat.RED + "Não há nenhum preview de colar pendente.");
                    return true;
                }
                session.getHistory().addChange(session.getLastPreviewChanges());
                session.setLastPreviewChanges(null);
                player.sendMessage(TextFormat.GREEN + "Colagem confirmada com sucesso!");
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
                if (session.getLastPreviewChanges() == null || session.getLastPreviewChanges().isEmpty()) {
                    player.sendMessage(TextFormat.RED + "Não há nenhum preview de colar pendente.");
                    return true;
                }
                for (BlockChange change : session.getLastPreviewChanges()) {
                    change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
                }
                session.setLastPreviewChanges(null);
                player.sendMessage(TextFormat.GREEN + "Colagem cancelada e área original restaurada!");
                return true;
            }

            if (session.isProcessing()) {
                player.sendMessage(TextFormat.RED + "Aguarde o término da operação atual do WorldEdit.");
                return true;
            }

            Clipboard clipboard = session.getClipboard();
            if (clipboard == null) {
                player.sendMessage(TextFormat.RED + "Sua área de transferência está vazia. Use //copy primeiro.");
                return true;
            }

            // Revert active preview first if pasting again
            if (session.getLastPreviewChanges() != null && !session.getLastPreviewChanges().isEmpty()) {
                for (BlockChange change : session.getLastPreviewChanges()) {
                    change.getLevel().setBlock(change.getX(), change.getY(), change.getZ(), change.getBeforeBlock(), true, true);
                }
                session.setLastPreviewChanges(null);
            }

            int baseX = player.getFloorX() + clipboard.getOffsetX();
            int baseY = player.getFloorY() + clipboard.getOffsetY();
            int baseZ = player.getFloorZ() + clipboard.getOffsetZ();

            Block[][][] blocks = clipboard.getBlocks();
            CompoundTag[][][] tiles = clipboard.getTiles();
            List<BlockSetTask.PendingChange> pending = new ArrayList<>();

            for (int x = 0; x < clipboard.getWidth(); x++) {
                for (int y = 0; y < clipboard.getHeight(); y++) {
                    for (int z = 0; z < clipboard.getLength(); z++) {
                        Block blockToPlace = blocks[x][y][z];
                        if (!session.isPasteAir() && blockToPlace.getId().equals("minecraft:air")) {
                            continue;
                        }
                        pending.add(new BlockSetTask.PendingChange(baseX + x, baseY + y, baseZ + z, blockToPlace, tiles[x][y][z]));
                    }
                }
            }

            // Save paste details for adjust stick
            session.setPasteBase(new Position(baseX, baseY, baseZ, player.getLevel()));
            session.setPasteClipboard(clipboard);

            // Update selection to match pasted boundaries
            session.setPos1(new Position(baseX, baseY, baseZ, player.getLevel()));
            session.setPos2(new Position(baseX + clipboard.getWidth() - 1, baseY + clipboard.getHeight() - 1, baseZ + clipboard.getLength() - 1, player.getLevel()));

            player.sendMessage(TextFormat.YELLOW + "Aplicando preview de colagem (" + pending.size() + " blocos)...");
            BlockSetTask task = new BlockSetTask(player, player.getLevel(), pending, BATCH_SIZE, false, true, "//paste");
            TaskHandler handler = this.getServer().getScheduler().scheduleRepeatingTask(this, task, 1);
            task.setHandler(handler);
            session.setCurrentTask(handler);
            return true;
        }

        if (cmd.equals("rotate")) {
            Clipboard clipboard = session.getClipboard();
            if (clipboard == null) {
                player.sendMessage(TextFormat.RED + "Sua área de transferência está vazia. Use //copy primeiro.");
                return true;
            }
            int degrees = 90;
            if (args.length >= 1) {
                try {
                    degrees = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage(TextFormat.RED + "Ângulo inválido. Use 90, 180 ou 270.");
                    return true;
                }
            }

            if (degrees != 90 && degrees != 180 && degrees != 270) {
                player.sendMessage(TextFormat.RED + "Ângulo inválido. Use 90, 180 ou 270.");
                return true;
            }

            Clipboard rotated = clipboard;
            int rotations = degrees / 90;
            for (int i = 0; i < rotations; i++) {
                rotated = rotated.rotate90();
            }

            session.setClipboard(rotated);
            player.sendMessage(TextFormat.GREEN + "Área de transferência rotacionada em " + degrees + " graus.");
            return true;
        }

        return false;
    }

    private void drawSelectionBox(Player player, Position pos1, Position pos2) {
        Level level = pos1.getLevel();
        if (level == null || pos2.getLevel() == null || !level.getFolderName().equals(pos2.getLevel().getFolderName())) {
            return;
        }

        int x1 = Math.min(pos1.getFloorX(), pos2.getFloorX());
        int x2 = Math.max(pos1.getFloorX(), pos2.getFloorX()) + 1;
        int y1 = Math.min(pos1.getFloorY(), pos2.getFloorY());
        int y2 = Math.max(pos1.getFloorY(), pos2.getFloorY()) + 1;
        int z1 = Math.min(pos1.getFloorZ(), pos2.getFloorZ());
        int z2 = Math.max(pos1.getFloorZ(), pos2.getFloorZ()) + 1;

        double step = 1.0;
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;

        if (dx > 100 || dy > 100 || dz > 100) {
            spawnCornerParticles(player, level, x1, x2, y1, y2, z1, z2);
            return;
        }

        if (dx * dy * dz > 2000) {
            step = 2.0;
        }

        // Edges along X
        for (double x = x1; x <= x2; x += step) {
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x, y1, z1), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x, y2, z1), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x, y1, z2), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x, y2, z2), 0, 255, 0), player);
        }

        // Edges along Y
        for (double y = y1; y <= y2; y += step) {
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y, z1), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y, z1), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y, z2), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y, z2), 0, 255, 0), player);
        }

        // Edges along Z
        for (double z = z1; z <= z2; z += step) {
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y1, z), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y1, z), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y2, z), 0, 255, 0), player);
            level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y2, z), 0, 255, 0), player);
        }
    }

    private void spawnCornerParticles(Player player, Level level, double x1, double x2, double y1, double y2, double z1, double z2) {
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y1, z1), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y1, z1), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y2, z1), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y2, z1), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y1, z2), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y1, z2), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x1, y2, z2), 255, 0, 0), player);
        level.addParticle(new cn.nukkit.level.particle.DustParticle(new cn.nukkit.math.Vector3(x2, y2, z2), 255, 0, 0), player);
    }
}

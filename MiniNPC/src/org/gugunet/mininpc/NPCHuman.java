package org.gugunet.mininpc;

import cn.nukkit.Player;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.level.format.IChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class NPCHuman extends EntityHuman {
    private String npcId;

    public NPCHuman(IChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public String getNpcId() {
        return npcId;
    }

    public void setNpcId(String npcId) {
        this.npcId = npcId;
        if (npcId != null) {
            this.namedTag.putString("npcId", npcId);
        }
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        if (this.namedTag.contains("npcId")) {
            this.npcId = this.namedTag.getString("npcId");
        }
        this.setImmobile(true);
        this.setNameTagVisible(true);
        this.setNameTagAlwaysVisible(true);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        if (npcId != null) {
            this.namedTag.putString("npcId", npcId);
        }
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        // Prevent all damage
        source.setCancelled(true);
        
        // Handle left click command
        if (source instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ede = (EntityDamageByEntityEvent) source;
            if (ede.getDamager() instanceof Player) {
                Player player = (Player) ede.getDamager();
                MiniNPC.getInstance().handleNpcLeftClick(player, this);
            }
        }
        return false;
    }

    @Override
    public boolean onInteract(Player player, cn.nukkit.item.Item item, cn.nukkit.math.Vector3 clickedPos) {
        MiniNPC.getInstance().handleNpcRightClick(player, this);
        return true;
    }
}

package org.gugunet.br;

import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.item.customitem.CustomItemDefinition;

public class ExitLobbyItem extends ItemCustom {
    private static final CustomItemDefinition DEFINITION = CustomItemDefinition
        .simpleBuilder(new ExitLobbyItem())
        .name("Sair do Lobby")
        .texture("gugunet_exit_dye")
        .build();

    public ExitLobbyItem() {
        super("gugunet:exit_dye");
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return DEFINITION;
    }
}

package org.gugunet.impostor;

import cn.nukkit.item.customitem.ItemCustom;
import cn.nukkit.item.customitem.CustomItemDefinition;

public class ImpostorExitItem extends ItemCustom {
    private static final CustomItemDefinition DEFINITION = CustomItemDefinition
        .simpleBuilder(new ImpostorExitItem())
        .name("Sair do Lobby")
        .texture("gugunet_exit_dye")
        .build();

    public ImpostorExitItem() {
        super("gugunet:exit_dye_imp");
    }

    @Override
    public CustomItemDefinition getDefinition() {
        return DEFINITION;
    }
}
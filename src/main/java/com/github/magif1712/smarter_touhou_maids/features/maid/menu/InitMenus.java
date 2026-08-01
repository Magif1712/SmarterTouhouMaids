package com.github.magif1712.smarter_touhou_maids.features.maid.menu;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class InitMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SmarterTouhouMaids.MOD_ID);

    public static final RegistryObject<MenuType<AutoTaskConfigMenu>> AUTO_TASK_CONFIG_MENU = MENUS.register("auto_task_config_menu",
            () -> IForgeMenuType.create(AutoTaskConfigMenu::new));
}
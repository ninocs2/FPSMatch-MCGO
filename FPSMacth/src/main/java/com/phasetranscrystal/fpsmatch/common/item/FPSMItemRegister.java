package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.FlashBombEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.GrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.IncendiaryGrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FPSMItemRegister {
    public static final DeferredRegister<CreativeModeTab> TABS;
    public static RegistryObject<CreativeModeTab> FPSM_TAB;
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);
    public static final RegistryObject<BaseThrowAbleItem> SMOKE_SHELL = ITEMS.register("smoke_shell",
            () -> new BaseThrowAbleItem(new Item.Properties(), SmokeShellEntity::new , FPSMSoundRegister.voice_smoke::get));
    public static final RegistryObject<BaseThrowAbleItem> CT_INCENDIARY_GRENADE = ITEMS.register("ct_incendiary_grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,3,FPSMItemRegister.CT_INCENDIARY_GRENADE::get)));
    public static final RegistryObject<BaseThrowAbleItem> T_INCENDIARY_GRENADE = ITEMS.register("t_incendiary_grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(),
            (player,level)-> new IncendiaryGrenadeEntity(player,level,4,FPSMItemRegister.T_INCENDIARY_GRENADE::get)));
    public static final RegistryObject<BaseThrowAbleItem> GRENADE = ITEMS.register("grenade",
            () -> new BaseThrowAbleItem(new Item.Properties(), GrenadeEntity::new, FPSMSoundRegister.voice_grenade::get));
    public static final RegistryObject<BaseThrowAbleItem> FLASH_BOMB = ITEMS.register("flash_bomb",
            () -> new BaseThrowAbleItem(new Item.Properties(), FlashBombEntity::new, FPSMSoundRegister.voice_flash::get));
    public static final RegistryObject<Item> SHOP_EDIT_TOOL = ITEMS.register("shop_edit_tool",
            () -> new ShopEditTool(new Item.Properties()));

    static {
        TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "fpsmatch");
        FPSM_TAB = TABS.register("other", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tab.fpsm"))
                .icon(() -> T_INCENDIARY_GRENADE.get().getDefaultInstance()).displayItems((parameters, output) -> {
            ITEMS.getEntries().forEach((entry) -> {
                output.accept(entry.get());
            });
        }).build());
    }
}

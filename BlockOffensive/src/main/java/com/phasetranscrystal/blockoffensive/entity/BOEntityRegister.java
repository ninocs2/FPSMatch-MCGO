package com.phasetranscrystal.blockoffensive.entity;

import com.phasetranscrystal.blockoffensive.BlockOffensive;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BOEntityRegister {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BlockOffensive.MODID);
    public static final RegistryObject<EntityType<CompositionC4Entity>> C4 =
            ENTITY_TYPES.register("c4", () -> EntityType.Builder.<CompositionC4Entity>of(CompositionC4Entity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build("c4"));
}

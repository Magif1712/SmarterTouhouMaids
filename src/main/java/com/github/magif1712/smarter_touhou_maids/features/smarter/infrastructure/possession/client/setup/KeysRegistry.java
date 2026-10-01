package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession.client.setup;

import com.github.magif1712.smarter_touhou_maids.SmarterTouhouMaids;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class KeysRegistry {
    public static final KeyMapping POSSESSION_KEY = new KeyMapping(
            "key.smarter_touhou_maids.possession",
            GLFW.GLFW_KEY_P,
            "key.categories.smarter_touhou_maids"
    );
}
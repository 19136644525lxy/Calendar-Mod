package com.htmlcraft.api;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(HtmlCraftAPI.MOD_ID)
public class HtmlCraftAPI {
    public static final String MOD_ID = "htmlcraftapi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HtmlCraftAPI(FMLJavaModLoadingContext context) {
        LOGGER.info("HtmlCraft API 初始化");
    }
}

package com.htmlcraft.api;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HtmlCraft API 主入口类（Fabric 版）。
 * <p>实现 {@link ModInitializer}，在游戏加载阶段完成 API 初始化。
 * <p>原理：Fabric 通过 fabric.mod.json 中 entrypoints.main 指定的类调用 onInitialize，
 * 与 Forge 的 @Mod 注解 + FMLJavaModLoadingContext 等价。
 */
public class HtmlCraftAPI implements ModInitializer {
    public static final String MOD_ID = "htmlcraftapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("HtmlCraft API 初始化 (Fabric)");
    }

    /** 构建 Identifier 便捷方法（Yarn 用 Identifier 替代 Forge 的 ResourceLocation） */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}

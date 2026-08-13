package com.calendar.mod.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 集成入口（Fabric 版）。
 * <p>对应 Forge 版的 Catalogue 模组列表配置按钮。
 * <p>在 fabric.mod.json 的 entrypoints.modmenu 中注册此类，
 * 玩家在模组列表中点击「配置」按钮时打开 Cloth Config 配置界面。
 */
public class CalendarModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CalendarClientConfig::buildScreen;
    }
}
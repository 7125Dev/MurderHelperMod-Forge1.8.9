package me.dev7125.murderhelper.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

public class ModGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // 初始化方法，如果需要的话
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGUI.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    // 配置GUI包装器类
    public static class ConfigGUIWrapper extends ConfigGUI {
        public ConfigGUIWrapper(GuiScreen parent) {
            super(parent);
        }
    }
}
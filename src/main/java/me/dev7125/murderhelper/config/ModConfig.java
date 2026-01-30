package me.dev7125.murderhelper.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 模组配置管理器
 * 统一管理所有配置项的加载、保存和访问
 */
public class ModConfig {

    private Configuration config;

    // ==================== 全局配置 ====================
    public boolean globalEnabled = true;
    public int renderNameTags = 0; // 0=All Player, 1=Enemy Faction

    // ==================== 功能开关 ====================
    public boolean hudEnabled = true;
    public boolean bowDropESPEnabled = true;
    public boolean murderAlarm = false;
    public boolean enhancedHitboxes = true;   // 增强hitbox渲染（加粗蓝色边框）
    public boolean suspectDetection = true;   // 嫌疑人检测

    // ==================== HUD配置 ====================
    public int hudWindowX = 10;
    public int hudWindowY = 10;
    public int hudBgAlpha = 192; // 背景透明度 (0-255)

    // ==================== Murderer喊话配置 ====================
    public boolean shoutEnabled = false;
    public String shoutMessage = "%Murderer% committed a perfect murder using %Item% at location X:%X%, Y:%Y%, Z:%Z%";
    public String replaceFrom = "";
    public String replaceTo = "";

    // ==================== Drop Bow喊话配置 ====================
    public boolean shoutDropBowEnabled = false;
    public String shoutDropBowMessage = "The detective's mission has failed, his bow has fallen at X:%bowX%, Y:%bowY%, Z:%bowZ%.";
    public String replaceDropBowFrom = "";
    public String replaceDropBowTo = "";

    /**
     * 加载配置文件
     * @param configFile 配置文件
     */
    public void load(File configFile) {
        config = new Configuration(configFile);
        config.load();

        // 加载全局配置
        globalEnabled = config.getBoolean("globalEnabled", "general", true,
                "Global mod toggle");
        renderNameTags = config.getInt("renderNameTags", "general", 0, 0, 1,
                "Nametag render mode (0=All Player, 1=Enemy Faction)");

        // 加载功能开关
        hudEnabled = config.getBoolean("hudEnabled", "general", true,
                "Enable enemy HUD window");
        bowDropESPEnabled = config.getBoolean("bowDropESPEnabled", "general", true,
                "Enable bow drop ESP display");
        murderAlarm = config.getBoolean("Alarm", "general", false,
                "Enemy faction proximity alarm");
        enhancedHitboxes = config.getBoolean("enhancedHitboxes", "general", true,
                "Enhanced hitbox rendering (bold blue outline instead of vanilla white)");
        suspectDetection = config.getBoolean("suspectDetection", "general", true,
                "Enable suspect detection near corpses");

        // 加载HUD配置
        hudWindowX = config.getInt("hudWindowX", "general", 10, 0, 10000,
                "HUD window X position");
        hudWindowY = config.getInt("hudWindowY", "general", 10, 0, 10000,
                "HUD window Y position");
        hudBgAlpha = config.getInt("hudBgAlpha", "general", 192, 0, 255,
                "HUD background transparency (0-255)");

        // 加载Murderer喊话配置
        shoutEnabled = config.getBoolean("shoutEnabled", "general", false,
                "Auto shout when detecting murderer");
        shoutMessage = config.getString("shoutMessage", "general",
                "%Murderer% committed a perfect murder using %Item% at location X:%X%, Y:%Y%, Z:%Z%",
                "Message to shout when murderer detected");
        replaceFrom = config.getString("replaceFrom", "general", "",
                "Text to replace in murderer shout message");
        replaceTo = config.getString("replaceTo", "general", "",
                "Replacement text for murderer shout message");

        // 加载Drop Bow喊话配置
        shoutDropBowEnabled = config.getBoolean("shoutDropBowEnabled", "general", false,
                "Auto shout when bow drops");
        shoutDropBowMessage = config.getString("shoutDropBowMessage", "general",
                "The detective's mission has failed, his bow has fallen at X:%bowX%, Y:%bowY%, Z:%bowZ%.",
                "Message to shout when bow drops");
        replaceDropBowFrom = config.getString("replaceDropBowFrom", "general", "",
                "Text to replace in drop bow shout message");
        replaceDropBowTo = config.getString("replaceDropBowTo", "general", "",
                "Replacement text for drop bow shout message");

        if (config.hasChanged()) {
            config.save();
        }
    }

    /**
     * 保存配置到文件
     */
    public void save() {
        if (config == null) {
            throw new IllegalStateException("Config not loaded! Call load() first.");
        }

        // 保存全局配置
        config.get("general", "globalEnabled", true).set(globalEnabled);
        config.get("general", "renderNameTags", 0).set(renderNameTags);

        // 保存功能开关
        config.get("general", "hudEnabled", true).set(hudEnabled);
        config.get("general", "bowDropESPEnabled", true).set(bowDropESPEnabled);
        config.get("general", "Alarm", false).set(murderAlarm);
        config.get("general", "enhancedHitboxes", true).set(enhancedHitboxes);
        config.get("general", "suspectDetection", true).set(suspectDetection);

        // 保存HUD配置
        config.get("general", "hudWindowX", 10).set(hudWindowX);
        config.get("general", "hudWindowY", 10).set(hudWindowY);
        config.get("general", "hudBgAlpha", 192).set(hudBgAlpha);

        // 保存Murderer喊话配置
        config.get("general", "shoutEnabled", false).set(shoutEnabled);
        config.get("general", "shoutMessage", "").set(shoutMessage);
        config.get("general", "replaceFrom", "").set(replaceFrom);
        config.get("general", "replaceTo", "").set(replaceTo);

        // 保存Drop Bow喊话配置
        config.get("general", "shoutDropBowEnabled", false).set(shoutDropBowEnabled);
        config.get("general", "shoutDropBowMessage", "").set(shoutDropBowMessage);
        config.get("general", "replaceDropBowFrom", "").set(replaceDropBowFrom);
        config.get("general", "replaceDropBowTo", "").set(replaceDropBowTo);

        config.save();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        if (config != null) {
            load(config.getConfigFile());
        }
    }

    /**
     * 获取底层Configuration对象（用于特殊情况）
     */
    public Configuration getConfig() {
        return config;
    }
}
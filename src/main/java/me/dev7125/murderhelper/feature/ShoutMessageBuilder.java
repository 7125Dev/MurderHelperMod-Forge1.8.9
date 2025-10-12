package me.dev7125.murderhelper.feature;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Logger;

/**
 * 喊话消息构建器
 * 负责构建和处理喊话消息（占位符替换、格式化等）
 */
public class ShoutMessageBuilder {
    
    /**
     * 构建杀手喊话消息
     * 
     * @param template 消息模板
     * @param murdererName 杀手名字
     * @param weapon 杀手武器
     * @param murderer 杀手实体（用于获取坐标）
     * @param replaceFrom 自定义替换源文本
     * @param replaceTo 自定义替换目标文本
     * @param logger 日志记录器
     * @return 构建好的消息
     */
    public static String buildMurdererShout(
            String template,
            String murdererName,
            ItemStack weapon,
            EntityPlayer murderer,
            String replaceFrom,
            String replaceTo,
            Logger logger) {
        
        String message = template;
        
        // 替换 %Murderer%
        message = message.replace("%Murderer%", murdererName);
        
        // 替换 %Item%
        String itemName = formatItemName(weapon);
        message = message.replace("%Item%", itemName);
        
        // 替换坐标占位符
        if (murderer != null) {
            message = message.replace("%X%", String.format("%.1f", murderer.posX));
            message = message.replace("%Y%", String.format("%.1f", murderer.posY));
            message = message.replace("%Z%", String.format("%.1f", murderer.posZ));
        } else {
            // 如果找不到实体，用问号替代
            message = message.replace("%X%", "?");
            message = message.replace("%Y%", "?");
            message = message.replace("%Z%", "?");
        }
        
        // 应用用户自定义的替换规则
        if (replaceFrom != null && !replaceFrom.trim().isEmpty()) {
            message = message.replace(replaceFrom, replaceTo);
            if (logger != null) {
                logger.info("Applied custom replacement: '" + replaceFrom + "' -> '" + replaceTo + "'");
            }
        }
        
        return message;
    }

    /**
     * 构建弓掉落喊话消息
     *
     * @param template 消息模板
     * @param detectiveName 侦探名字
     * @param bowX 弓X坐标
     * @param bowY 弓Y坐标
     * @param bowZ 弓Z坐标
     * @param replaceFrom 自定义替换源文本
     * @param replaceTo 自定义替换目标文本
     * @param logger 日志记录器
     * @return 构建好的消息
     */
    public static String buildBowDropShout(
            String template,
            String detectiveName,
            double bowX,
            double bowY,
            double bowZ,
            String replaceFrom,
            String replaceTo,
            Logger logger) {

        String message = template;

        // 替换 %Detective%
        message = message.replace("%Detective%", detectiveName);

        // 替换弓掉落位置坐标
        message = message.replace("%bowX%", String.format("%.1f", bowX));
        message = message.replace("%bowY%", String.format("%.1f", bowY));
        message = message.replace("%bowZ%", String.format("%.1f", bowZ));

        // 应用用户自定义的替换规则
        if (replaceFrom != null && !replaceFrom.trim().isEmpty()) {
            message = message.replace(replaceFrom, replaceTo);
            if (logger != null) {
                logger.info("Applied custom replacement for bow drop: '" + replaceFrom + "' -> '" + replaceTo + "'");
            }
        }

        return message;
    }
    
    /**
     * 格式化物品名称
     * 
     * @param item 物品
     * @return 格式化后的物品名称
     */
    private static String formatItemName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }
        
        // 获取物品注册名
        String registryName = item.getItem().getRegistryName();
        if (registryName != null) {
            // 去掉 minecraft: 前缀
            if (registryName.contains(":")) {
                registryName = registryName.substring(registryName.indexOf(":") + 1);
            }
            
            // 将 diamond_sword 转换为 DiamondSword (驼峰命名)
            registryName = toCamelCase(registryName);
        }
        
        // 获取显示名并去除颜色代码
        String displayName = item.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            // 去除所有 Minecraft 颜色代码 (§ + 一个字符)
            displayName = displayName.replaceAll("§[0-9a-fk-or]", "");
            
            // 格式化为: DiamondSword(钻石剑)
            return registryName + "(" + displayName + ")";
        } else {
            // 如果显示名为空，只显示驼峰命名
            return registryName != null ? registryName : "Unknown";
        }
    }
    
    /**
     * 将下划线分隔的字符串转换为驼峰命名
     * 例如：diamond_sword -> DiamondSword
     * 
     * @param text 原始文本
     * @return 驼峰命名文本
     */
    private static String toCamelCase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] parts = text.split("_");
        StringBuilder camelCase = new StringBuilder();
        
        for (String part : parts) {
            if (part.length() > 0) {
                // 首字母大写，其余小写
                camelCase.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    camelCase.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return camelCase.toString();
    }
}
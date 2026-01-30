package me.dev7125.murderhelper.handler;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.render.BowDropRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弓箭追踪和渲染处理器
 * 优化版本：配合BowShotDetector使用，基于坐标而非实体引用进行渲染
 */
public class BowDropRenderHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // 记录弓位置及其检测时间
    // Key: 位置字符串 "x,y,z", Value: BowDropLocation
    private final Map<String, BowDropLocation> bowLocations = new ConcurrentHashMap<>();

    // 配置项：是否启用弓箭显示
    public static boolean enabled = true;

    /**
     * 弓掉落位置信息
     */
    private static class BowDropLocation {
        final double x, y, z;
        final long detectTime;

        BowDropLocation(double x, double y, double z, long detectTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.detectTime = detectTime;
        }

        String getKey() {
            return String.format("%.1f,%.1f,%.1f", x, y, z);
        }
    }

    /**
     * 渲染所有追踪的弓位置文字
     */
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // 检查前置条件
        if (!enabled || !MurderHelperMod.config.globalEnabled ||
                !MurderHelperMod.isGameActuallyStarted() ||
                mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // 遍历所有追踪的弓位置，渲染文字
        for (BowDropLocation location : bowLocations.values()) {
            // 调用优化后的渲染器，直接基于坐标渲染
            BowDropRenderer.renderBowDropText(
                    location.x,
                    location.y,
                    location.z,
                    location.detectTime
            );
        }
    }

    /**
     * 清空所有追踪的弓位置（游戏结束或切换世界时调用）
     */
    public void clear() {
        bowLocations.clear();
        MurderHelperMod.logger.info("[BowDropTracker] Cleared all bow locations");
    }

    /**
     * 获取当前追踪的弓数量
     */
    public int getTrackedBowCount() {
        return bowLocations.size();
    }

    /**
     * 添加弓位置追踪（通过坐标）
     * 这是新的推荐方法，由BowShotDetector调用
     */
    public void addBowLocation(double x, double y, double z, long detectTime) {
        BowDropLocation location = new BowDropLocation(x, y, z, detectTime);
        String key = location.getKey();

        if (!bowLocations.containsKey(key)) {
            bowLocations.put(key, location);
            MurderHelperMod.logger.info("[BowDropTracker] Added bow location: pos=({}, {}, {})",
                    String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z));
        }
    }

    /**
     * 添加弓位置追踪（使用当前时间）
     */
    public void addBowLocation(double x, double y, double z) {
        addBowLocation(x, y, z, System.currentTimeMillis());
    }

    /**
     * 移除弓位置追踪（通过坐标）
     */
    public void removeBowLocation(double x, double y, double z) {
        String key = String.format("%.1f,%.1f,%.1f", x, y, z);

        if (bowLocations.remove(key) != null) {
            MurderHelperMod.logger.info("[BowDropTracker] Removed bow location: pos=({}, {}, {})",
                    String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z));
        }
    }

    /**
     * 移除弓位置追踪（模糊匹配，允许小范围偏差）
     */
    public void removeBowLocationFuzzy(double x, double y, double z, double tolerance) {
        Iterator<Map.Entry<String, BowDropLocation>> iterator = bowLocations.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, BowDropLocation> entry = iterator.next();
            BowDropLocation location = entry.getValue();

            double distance = Math.sqrt(
                    Math.pow(x - location.x, 2) +
                            Math.pow(y - location.y, 2) +
                            Math.pow(z - location.z, 2)
            );

            if (distance <= tolerance) {
                iterator.remove();
                MurderHelperMod.logger.info("[BowDropTracker] Removed bow location (fuzzy): pos=({}, {}, {}), distance={}",
                        String.format("%.1f", location.x), String.format("%.1f", location.y),
                        String.format("%.1f", location.z), String.format("%.1f", distance));
            }
        }
    }

    // ==================== 兼容性方法（保留旧API） ====================

    /**
     * 手动添加一个弓实体追踪（兼容旧代码）
     * 从实体中提取坐标并添加到追踪列表
     */
    public void addBowEntity(Entity entity) {
        if (entity != null) {
            addBowLocation(entity.posX, entity.posY, entity.posZ);
        }
    }

    /**
     * 移除指定的弓实体追踪（兼容旧代码）
     */
    public void removeBowEntity(Entity entity) {
        if (entity != null) {
            removeBowLocationFuzzy(entity.posX, entity.posY, entity.posZ, 0.5);
        }
    }

    /**
     * 手动添加一个弓箭掉落物追踪（兼容旧代码）
     */
    public void addBowDrop(EntityItem entityItem) {
        addBowEntity(entityItem);
    }

    /**
     * 移除指定的弓箭掉落物追踪（兼容旧代码）
     */
    public void removeBowDrop(EntityItem entityItem) {
        removeBowEntity(entityItem);
    }
}

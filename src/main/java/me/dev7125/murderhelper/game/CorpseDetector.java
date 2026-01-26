package me.dev7125.murderhelper.game;

import net.minecraft.entity.DataWatcher;
import net.minecraft.network.play.server.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 尸体检测器
 * 负责检测玩家实体尸体和盔甲架尸体，区分飞刀盔甲架和其他隐形盔甲架
 */
public class CorpseDetector {
    
    private final Logger logger;
    
    // 尸体信息
    public static class CorpseInfo {
        public final int entityId;
        public final Vec3 position;
        public final long timestamp;
        public final CorpseType type;
        
        public CorpseInfo(int entityId, Vec3 position, CorpseType type) {
            this.entityId = entityId;
            this.position = position;
            this.timestamp = System.currentTimeMillis();
            this.type = type;
        }
    }
    
    public enum CorpseType {
        PLAYER_ENTITY,    // 玩家实体尸体（躺床）
        ARMOR_STAND       // 盔甲架尸体
    }
    
    // 临时存储：等待确认的盔甲架
    private static class PendingArmorStand {
        Vec3 position;
        boolean hasHeadSlot = false;
        boolean isSmallMarker = false;
        
        public boolean isCorpse() {
            // 必须有头盔槽装备 + 是小型标记盔甲架
            return hasHeadSlot && isSmallMarker;
        }
    }
    
    // 已检测到的尸体（entityId -> CorpseInfo）
    private final Map<Integer, CorpseInfo> detectedCorpses = new HashMap<>();
    
    // 等待确认的盔甲架（entityId -> PendingArmorStand）
    private final Map<Integer, PendingArmorStand> pendingArmorStands = new HashMap<>();
    
    // 已确认的飞刀盔甲架ID（用于排除）
    private final Set<Integer> confirmedKnifeStands = new HashSet<>();
    
    public CorpseDetector(Logger logger) {
        this.logger = logger;
    }
    
    // ==================== 玩家实体尸体检测 ====================
    
    /**
     * 监听玩家生成数据包
     */
    public void handleSpawnPlayer(S0CPacketSpawnPlayer packet) {
        int entityId = packet.getEntityID();
        
        // 记录玩家实体的位置（等待后续的躺床数据包）
        double x = packet.getX() / 32.0;
        double y = packet.getY() / 32.0;
        double z = packet.getZ() / 32.0;
        
        Vec3 position = new Vec3(x, y, z);
        
        // 暂时不记录为尸体，等待 S0APacketUseBed 确认
        // 创建临时标记（使用 pendingArmorStands 复用结构）
        PendingArmorStand pending = new PendingArmorStand();
        pending.position = position;
        pendingArmorStands.put(entityId, pending);
    }
    
    /**
     * 监听玩家使用床数据包（确认尸体）
     */
    public void handleUseBed(S0APacketUseBed packet) {
        int entityId = getUseBedEntityId(packet);
        BlockPos bedPos = packet.getBedPosition();
        
        // 从临时记录中获取玩家位置
        PendingArmorStand pending = pendingArmorStands.remove(entityId);
        if (pending == null || pending.position == null) {
            return;
        }
        
        // 确认为玩家实体尸体
        CorpseInfo corpse = new CorpseInfo(entityId, pending.position, CorpseType.PLAYER_ENTITY);
        detectedCorpses.put(entityId, corpse);
        
        logger.info("Detected PLAYER CORPSE at ({}, {}, {})", 
            pending.position.xCoord, pending.position.yCoord, pending.position.zCoord);
    }
    
    // ==================== 盔甲架尸体检测 ====================
    
    /**
     * 监听生成对象数据包（盔甲架）
     */
    public void handleSpawnObject(S0EPacketSpawnObject packet) {
        int type = packet.getType();
        
        // 只处理盔甲架（type=78）
        if (type != 78) {
            return;
        }
        
        int entityId = packet.getEntityID();
        
        // 记录盔甲架位置
        double x = packet.getX() / 32.0;
        double y = packet.getY() / 32.0;
        double z = packet.getZ() / 32.0;
        
        PendingArmorStand pending = new PendingArmorStand();
        pending.position = new Vec3(x, y, z);
        
        pendingArmorStands.put(entityId, pending);
    }
    
    /**
     * 监听实体装备数据包
     */
    public void handleEntityEquipment(S04PacketEntityEquipment packet) {
        int entityId = packet.getEntityID();
        int slot = packet.getEquipmentSlot();
        
        PendingArmorStand pending = pendingArmorStands.get(entityId);
        if (pending == null) {
            return;
        }
        
        // 槽位4 = 头盔 → 可能是尸体
        if (slot == 4) {
            pending.hasHeadSlot = true;
            checkAndConfirmCorpse(entityId, pending);
        }
        
        // 槽位0 = 主手 → 是飞刀，排除
        if (slot == 0) {
            confirmedKnifeStands.add(entityId);
            pendingArmorStands.remove(entityId);
        }
    }
    
    /**
     * 监听实体元数据数据包
     */
    /**
     * 监听实体元数据数据包
     */
    public void handleEntityMetadata(S1CPacketEntityMetadata packet) {
        int entityId = packet.getEntityId();

        PendingArmorStand pending = pendingArmorStands.get(entityId);
        if (pending == null) {
            return;
        }

        // 检查元数据中的盔甲架状态标志
        List<DataWatcher.WatchableObject> metadata = packet.func_149376_c();
        if (metadata == null) {
            return;
        }

        for (DataWatcher.WatchableObject watchable : metadata) {
            int objectType = watchable.getObjectType();
            int dataValueId = watchable.getDataValueId();
            Object value = watchable.getObject();

            // dataValueId=0 是盔甲架状态标志
            if (dataValueId == 0 && objectType == 0) {  // objectType 0 是 byte 类型
                if (value instanceof Byte) {
                    int flags = ((Byte) value).intValue();

                    // 检查是否是 Small(0x01) + Marker(0x10)
                    // 尸体通常是 0x17 (23) = 0x01 + 0x02 + 0x04 + 0x10
                    boolean isSmall = (flags & 0x01) != 0;
                    boolean isMarker = (flags & 0x10) != 0;

                    if (isSmall && isMarker) {
                        pending.isSmallMarker = true;
                        checkAndConfirmCorpse(entityId, pending);
                    }
                }
            }
        }
    }
    
    /**
     * 检查并确认盔甲架是否为尸体
     */
    private void checkAndConfirmCorpse(int entityId, PendingArmorStand pending) {
        // 如果已经被标记为飞刀，不处理
        if (confirmedKnifeStands.contains(entityId)) {
            return;
        }
        
        // 检查是否满足尸体条件
        if (pending.isCorpse()) {
            CorpseInfo corpse = new CorpseInfo(entityId, pending.position, CorpseType.ARMOR_STAND);
            detectedCorpses.put(entityId, corpse);
            pendingArmorStands.remove(entityId);
            
            logger.info("Detected ARMOR STAND CORPSE at ({}, {}, {})", 
                pending.position.xCoord, pending.position.yCoord, pending.position.zCoord);
        }
    }
    
    /**
     * 监听实体销毁数据包
     */
    public void handleDestroyEntities(S13PacketDestroyEntities packet) {
        for (int entityId : packet.getEntityIDs()) {
            // 移除尸体记录
            detectedCorpses.remove(entityId);
            
            // 移除临时记录
            pendingArmorStands.remove(entityId);
            confirmedKnifeStands.remove(entityId);
        }
    }
    
    // ==================== 查询接口 ====================
    
    /**
     * 获取所有检测到的尸体
     */
    public Collection<CorpseInfo> getAllCorpses() {
        return new ArrayList<>(detectedCorpses.values());
    }
    
    /**
     * 获取最近的尸体（5秒内）
     */
    public Collection<CorpseInfo> getRecentCorpses(long timeWindowMs) {
        long now = System.currentTimeMillis();
        List<CorpseInfo> recent = new ArrayList<>();
        
        for (CorpseInfo corpse : detectedCorpses.values()) {
            if (now - corpse.timestamp <= timeWindowMs) {
                recent.add(corpse);
            }
        }
        
        return recent;
    }
    
    /**
     * 清理过期的尸体记录（超过指定时间）
     */
    public void cleanupOldCorpses(long maxAgeMs) {
        long now = System.currentTimeMillis();
        detectedCorpses.entrySet().removeIf(entry -> 
            now - entry.getValue().timestamp > maxAgeMs
        );
    }
    
    /**
     * 重置所有数据
     */
    public void reset() {
        detectedCorpses.clear();
        pendingArmorStands.clear();
        confirmedKnifeStands.clear();
    }
    
    /**
     * 获取当前检测到的尸体数量
     */
    public int getCorpseCount() {
        return detectedCorpses.size();
    }

    public static int getUseBedEntityId(S0APacketUseBed packet) {
        for (Field field : S0APacketUseBed.class.getDeclaredFields()) {
            if (field.getType() == int.class) {
                field.setAccessible(true);
                try {
                    return field.getInt(packet);
                } catch (IllegalAccessException ignored) {}
            }
        }
        return -1;
    }

}
package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.feature.ShoutMessageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侦探弓箭掉落检测器 - 通过数据包检测弓箭掉落和拾取
 *
 * 工作流程：
 * 1. 侦探被杀死后，在原地生成一个隐形盔甲架持弓（S0EPacketSpawnObject + S04PacketEntityEquipment）
 * 2. 盔甲架会旋转并标记为特殊状态（S1CPacketEntityMetadata）
 * 3. 当玩家捡起弓时，盔甲架消失（S13PacketDestroyEntities）
 * 4. 通过追踪玩家接近盔甲架的距离，识别拾取弓的玩家并锁定为新侦探
 */
public class BowDropDetector {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 追踪盔甲架弓掉落物（持弓的盔甲架）
    // Key: 实体ID, Value: BowDropInfo
    private final Map<Integer, BowDropInfo> armorStandBows = new ConcurrentHashMap<>();

    // 追踪掉落物形式的弓（EntityItem，地上的弓）
    // Key: 实体ID, Value: BowDropInfo
    private final Map<Integer, BowDropInfo> itemBows = new ConcurrentHashMap<>();

    // 记录已经喊过话的弓位置（使用字符串格式 "x,y,z"），避免重复发送
    private final Set<String> shoutedBowPositions = new HashSet<>();

    // 追踪玩家靠近弓掉落的记录（用于识别捡弓者）
    // Key: 实体ID (盔甲架), Value: Map<玩家名, PlayerProximityInfo>
    private final Map<Integer, Map<String, PlayerProximityInfo>> playerProximityTracking = new ConcurrentHashMap<>();

    // 位置判定的容差范围（格）
    private static final double POSITION_TOLERANCE = 2.0;

    // 识别新侦探的时间窗口（毫秒）- 在弓掉落消失前的时间窗口内
    private static final long DETECTIVE_IDENTIFICATION_WINDOW = 500;

    // 拾取弓的距离阈值（格）- 适当放大以确保能捕捉到拾取者
    private static final double PICKUP_DISTANCE_THRESHOLD = 3.0;

    /**
     * 玩家接近度信息
     */
    private static class PlayerProximityInfo {
        final String playerName;
        double closestDistance;
        long lastUpdateTime;

        PlayerProximityInfo(String playerName, double distance, long time) {
            this.playerName = playerName;
            this.closestDistance = distance;
            this.lastUpdateTime = time;
        }

        void updateIfCloser(double distance, long time) {
            if (distance < closestDistance) {
                closestDistance = distance;
                lastUpdateTime = time;
            }
        }
    }

    /**
     * 弓掉落信息
     */
    private static class BowDropInfo {
        final int entityId;
        final EntityType type;
        double x, y, z;
        long detectTime;
        boolean confirmed; // 是否已确认（盔甲架：已装备弓；物品实体：已确认是弓）

        // 盔甲架专用属性
        public boolean hasEquippedBow = false;
        public boolean hasArmor = false;
        public boolean isInvisible = false;
        public boolean hasNoGravity = false;
        public boolean hasNoBasePlate = false;
        public boolean hasMarker = false;

        enum EntityType {
            ARMOR_STAND,  // 盔甲架持弓
            ITEM          // 掉落物（弓）
        }

        BowDropInfo(int entityId, EntityType type, double x, double y, double z) {
            this.entityId = entityId;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.detectTime = System.currentTimeMillis();
            this.confirmed = false;
        }

        public void updateArmorStandProperties(boolean isInvisible, boolean hasNoGravity,
                                               boolean hasNoBasePlate, boolean hasMarker) {
            this.isInvisible = isInvisible;
            this.hasNoGravity = hasNoGravity;
            this.hasNoBasePlate = hasNoBasePlate;
            this.hasMarker = hasMarker;
        }
    }

    // ==================== 数据包处理方法 ====================

    /**
     * 处理生成对象数据包
     * 检测盔甲架和掉落物的创建
     */
    public void handleSpawnObject(S0EPacketSpawnObject packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int type = packet.getType();
        int entityId = packet.getEntityID();
        double x = packet.getX() / 32.0;
        double y = packet.getY() / 32.0;
        double z = packet.getZ() / 32.0;

        // 类型78 = 盔甲架 - 创建待确认记录（需要等待装备弓才确认）
        if (type == 78) {
            BowDropInfo info = new BowDropInfo(entityId, BowDropInfo.EntityType.ARMOR_STAND, x, y, z);
            armorStandBows.put(entityId, info);
            // 为这个盔甲架创建玩家接近度追踪
            playerProximityTracking.put(entityId, new ConcurrentHashMap<>());
        }
        // 类型2 = 掉落物 - 创建待确认记录（需要通过元数据确认是弓）
        else if (type == 2) {
            BowDropInfo info = new BowDropInfo(entityId, BowDropInfo.EntityType.ITEM, x, y, z);
            itemBows.put(entityId, info);
        }
    }

    /**
     * 处理实体装备数据包
     * 检测盔甲架是否装备了弓，但不直接确认（需要等待元数据验证）
     */
    public void handleEntityEquipment(S04PacketEntityEquipment packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int entityId = packet.getEntityID();
        int slot = packet.getEquipmentSlot();
        ItemStack itemStack = packet.getItemStack();

        // 检查是否是主手槽位（slot 0）
        if (slot != 0) {
            return;
        }

        // 检查盔甲架装备弓
        BowDropInfo armorInfo = armorStandBows.get(entityId);
        if (armorInfo != null) {
            if (itemStack != null && itemStack.getItem() == Items.bow) {
                // 标记已装备弓，但不直接确认（需要等待元数据验证属性）
                if (!armorInfo.hasEquippedBow) {
                    armorInfo.hasEquippedBow = true;
                    MurderHelperMod.logger.info("[BowDropDetector] Bow equipped on armor stand: ID={}, pos=({}, {}, {}), awaiting metadata validation",
                            entityId, String.format("%.1f", armorInfo.x), String.format("%.1f", armorInfo.y), String.format("%.1f", armorInfo.z));

                    // 尝试确认（如果元数据已经到达且验证通过）
                    tryConfirmArmorStandBow(armorInfo);
                }
            } else if (itemStack == null) {
                // 盔甲架卸下了弓（可能是bug或特殊情况）
                if (armorInfo.hasEquippedBow) {
                    armorInfo.hasEquippedBow = false;
                    MurderHelperMod.logger.info("[BowDropDetector] Bow unequipped from armor stand: ID={}", entityId);
                }
            } else {
                // 盔甲架装备了其他物品（不是弓），移除追踪
                armorStandBows.remove(entityId);
                playerProximityTracking.remove(entityId);
                MurderHelperMod.logger.info("[BowDropDetector] Armor stand equipped non-bow item, removed from tracking: ID={}", entityId);
            }
        }
    }

    /**
     * 尝试确认盔甲架弓掉落
     * 只有当装备弓且属性验证通过时才确认
     */
    private void tryConfirmArmorStandBow(BowDropInfo armorInfo) {
        if (armorInfo.confirmed || !armorInfo.hasEquippedBow) {
            return;
        }

        if (validateArmorStandBowDrop(armorInfo)) {
            armorInfo.confirmed = true;
            MurderHelperMod.logger.info("[BowDropDetector] ✓ Bow armor stand CONFIRMED: ID={}, pos=({}, {}, {})",
                    armorInfo.entityId, String.format("%.1f", armorInfo.x),
                    String.format("%.1f", armorInfo.y), String.format("%.1f", armorInfo.z));
            MurderHelperMod.logger.info("  Properties: invisible={}, noGravity={}, noBasePlate={}, marker={}",
                    armorInfo.isInvisible, armorInfo.hasNoGravity,
                    armorInfo.hasNoBasePlate, armorInfo.hasMarker);
            onBowDropConfirmed(armorInfo);
        }
    }

    /**
     * 处理实体元数据数据包
     * 对盔甲架进行严格的属性检测，确保是真正的弓掉落
     */
    public void handleEntityMetadata(S1CPacketEntityMetadata packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int entityId = packet.getEntityId();
        List<DataWatcher.WatchableObject> metadata = packet.func_149376_c();
        if (metadata == null) {
            return;
        }

        // ========== 处理掉落物（EntityItem）==========
        BowDropInfo itemInfo = itemBows.get(entityId);
        if (itemInfo != null && !itemInfo.confirmed) {
            for (DataWatcher.WatchableObject obj : metadata) {
                // Slot 10 = EntityItem的ItemStack
                if (obj.getDataValueId() == 10 && obj.getObject() instanceof ItemStack) {
                    ItemStack itemStack = (ItemStack) obj.getObject();
                    if (itemStack.getItem() == Items.bow) {
                        itemInfo.confirmed = true;
                        MurderHelperMod.logger.info("[BowDropDetector] ✓ Bow item confirmed: ID={}, pos=({}, {}, {})",
                                entityId, String.format("%.1f", itemInfo.x),
                                String.format("%.1f", itemInfo.y), String.format("%.1f", itemInfo.z));
                        onBowDropConfirmed(itemInfo);
                    } else {
                        itemBows.remove(entityId);
                    }
                    break;
                }
            }
        }

        // ========== 处理盔甲架（EntityArmorStand）==========
        BowDropInfo armorInfo = armorStandBows.get(entityId);
        if (armorInfo != null) {
            // 提取盔甲架属性
            Boolean isInvisible = null;
            Boolean hasNoGravity = null;
            Boolean hasNoBasePlate = null;
            Boolean hasMarker = null;

            for (DataWatcher.WatchableObject obj : metadata) {
                int id = obj.getDataValueId();
                Object value = obj.getObject();

                // Slot 0 = Entity flags (byte)
                if (id == 0 && value instanceof Byte) {
                    byte flags = (Byte) value;
                    // isInvisible = getFlag(5) = (flags & (1 << 5)) != 0
                    isInvisible = (flags & 0x20) != 0;
                }
                // Slot 10 = ArmorStand status flags (byte)
                else if (id == 10 && value instanceof Byte) {
                    byte armorStandFlags = (Byte) value;
                    // hasNoGravity = (flags & 2) != 0
                    hasNoGravity = (armorStandFlags & 0x02) != 0;
                    // hasNoBasePlate = (flags & 8) != 0
                    hasNoBasePlate = (armorStandFlags & 0x08) != 0;
                    // hasMarker = (flags & 16) != 0
                    hasMarker = (armorStandFlags & 0x10) != 0;
                }
            }

            // 更新检测到的属性
            if (isInvisible != null) {
                armorInfo.isInvisible = isInvisible;
            }
            if (hasNoGravity != null) {
                armorInfo.hasNoGravity = hasNoGravity;
            }
            if (hasNoBasePlate != null) {
                armorInfo.hasNoBasePlate = hasNoBasePlate;
            }
            if (hasMarker != null) {
                armorInfo.hasMarker = hasMarker;
            }

            // 尝试确认（如果已装备弓且验证通过）
            tryConfirmArmorStandBow(armorInfo);
        }
    }

    /**
     * 验证盔甲架是否是真正的弓掉落
     * 完全复刻旧版本的严格检测逻辑
     */
    private boolean validateArmorStandBowDrop(BowDropInfo info) {
        // 必须持弓
        if (!info.hasEquippedBow) {
            return false;
        }

        // 必须没有穿盔甲（NPC会穿盔甲）
        if (info.hasArmor) {
            return false;
        }

        // 必须是隐形的
        if (!info.isInvisible) {
            return false;
        }

        // 模式1：新版本 Hypixel
        // - 无重力 (hasNoGravity = true)
        // - 有底座 (hasNoBasePlate = false)
        // - 标记模式 (hasMarker = true)
        boolean isMode1 = info.hasNoGravity && !info.hasNoBasePlate && info.hasMarker;

        // 模式2：旧版本 Mineberry/Mineblaze
        // - 有重力 (hasNoGravity = false)
        // - 无底座 (hasNoBasePlate = true)
        boolean isMode2 = !info.hasNoGravity && info.hasNoBasePlate;

        return isMode1 || isMode2;
    }

    /**
     * 处理实体相对移动数据包
     * 更新弓掉落位置
     */
    public void handleEntityRelMove(S14PacketEntity.S15PacketEntityRelMove packet) {
        if (!MurderHelperMod.isGameActuallyStarted() || mc.theWorld == null) {
            return;
        }

        // 获取实体，可能为null（实体尚未加载或已被移除）
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity == null) {
            return;
        }

        int entityId = entity.getEntityId();

        // 更新盔甲架位置
        BowDropInfo armorInfo = armorStandBows.get(entityId);
        if (armorInfo != null) {
            armorInfo.x += packet.func_149062_c() / 32.0;
            armorInfo.y += packet.func_149061_d() / 32.0;
            armorInfo.z += packet.func_149064_e() / 32.0;
        }

        // 更新掉落物位置
        BowDropInfo itemInfo = itemBows.get(entityId);
        if (itemInfo != null) {
            itemInfo.x += packet.func_149062_c() / 32.0;
            itemInfo.y += packet.func_149061_d() / 32.0;
            itemInfo.z += packet.func_149064_e() / 32.0;
        }
    }

    /**
     * 处理实体传送数据包
     * 更新弓掉落位置
     */
    public void handleEntityTeleport(S18PacketEntityTeleport packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int entityId = packet.getEntityId();
        double x = packet.getX() / 32.0;
        double y = packet.getY() / 32.0;
        double z = packet.getZ() / 32.0;

        // 更新盔甲架位置
        BowDropInfo armorInfo = armorStandBows.get(entityId);
        if (armorInfo != null) {
            armorInfo.x = x;
            armorInfo.y = y;
            armorInfo.z = z;
        }

        // 更新掉落物位置
        BowDropInfo itemInfo = itemBows.get(entityId);
        if (itemInfo != null) {
            itemInfo.x = x;
            itemInfo.y = y;
            itemInfo.z = z;
        }
    }

    /**
     * 处理实体销毁数据包
     * 检测弓被拾取（盔甲架或掉落物消失）并识别新侦探
     */
    public void handleDestroyEntities(S13PacketDestroyEntities packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int[] entityIds = packet.getEntityIDs();
        long currentTime = System.currentTimeMillis();

        for (int entityId : entityIds) {
            // 检查盔甲架弓
            BowDropInfo armorInfo = armorStandBows.remove(entityId);
            if (armorInfo != null && armorInfo.confirmed) {
                MurderHelperMod.logger.info("[BowDropDetector] ✓ Bow armor stand destroyed: ID={} (bow picked up)", entityId);

                // 识别捡起弓的新侦探
                String newDetective = identifyNewDetective(entityId, armorInfo, currentTime);
                if (newDetective != null) {
                    MurderHelperMod.logger.info("[BowDropDetector] ✓ Identified new detective: {}", newDetective);
                }

                // 清理该盔甲架的接近度追踪
                playerProximityTracking.remove(entityId);

                // 通知BowDropTracker移除渲染
                onBowPickedUp(armorInfo);
            }

            // 检查掉落物弓
            BowDropInfo itemInfo = itemBows.remove(entityId);
            if (itemInfo != null && itemInfo.confirmed) {
                MurderHelperMod.logger.info("[BowDropDetector] ✓ Bow item destroyed: ID={} (bow picked up)", entityId);

                // 识别捡起弓的新侦探
                String newDetective = identifyNewDetectiveForItem(itemInfo, currentTime);
                if (newDetective != null) {
                    MurderHelperMod.logger.info("[BowDropDetector] ✓ Identified new detective: {}", newDetective);
                }

                // 通知BowDropTracker移除渲染
                onBowPickedUp(itemInfo);
            }
        }
    }

    /**
     * 每tick更新玩家与盔甲架的接近度
     * 应该在客户端tick事件中调用此方法
     */
    public void onClientTick() {
        if (!MurderHelperMod.isGameActuallyStarted() || mc.theWorld == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // 遍历所有确认的盔甲架弓掉落
        for (Map.Entry<Integer, BowDropInfo> entry : armorStandBows.entrySet()) {
            int entityId = entry.getKey();
            BowDropInfo bowInfo = entry.getValue();

            // 只追踪已确认的弓掉落
            if (!bowInfo.confirmed) {
                continue;
            }

            Map<String, PlayerProximityInfo> proximityMap = playerProximityTracking.get(entityId);
            if (proximityMap == null) {
                continue;
            }

            // 检查所有玩家与该盔甲架的距离
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == null) {
                    continue;
                }

                // 排除死亡的幽灵玩家
                if (!isValidPickupCandidate(player)) {
                    continue;
                }

                String playerName = player.getName();

                // 排除杀手
                if (MurderHelperMod.playerTracker.isMurdererLocked(playerName)) {
                    continue;
                }

                // 计算距离
                double distance = calculateDistance(
                        player.posX, player.posY, player.posZ,
                        bowInfo.x, bowInfo.y, bowInfo.z
                );

                // 只追踪在拾取距离阈值内的玩家
                if (distance <= PICKUP_DISTANCE_THRESHOLD) {
                    PlayerProximityInfo proximityInfo = proximityMap.get(playerName);
                    if (proximityInfo == null) {
                        proximityInfo = new PlayerProximityInfo(playerName, distance, currentTime);
                        proximityMap.put(playerName, proximityInfo);
                    } else {
                        proximityInfo.updateIfCloser(distance, currentTime);
                    }
                }
            }

            // 清理超过时间窗口的旧记录
            proximityMap.entrySet().removeIf(e ->
                    (currentTime - e.getValue().lastUpdateTime) > DETECTIVE_IDENTIFICATION_WINDOW);
        }
    }

    /**
     * 识别捡起弓的新侦探（盔甲架类型）
     * 逻辑：
     * 1. 首先尝试使用追踪数据找最近的玩家
     * 2. 如果没有追踪数据，直接检查当前时刻谁最靠近盔甲架
     *
     * @param entityId 盔甲架实体ID
     * @param bowInfo 弓掉落信息
     * @param destroyTime 盔甲架销毁时间
     * @return 新侦探名字，如果找不到返回null
     */
    private String identifyNewDetective(int entityId, BowDropInfo bowInfo, long destroyTime) {
        String newDetective = null;
        double closestDistance = Double.MAX_VALUE;

        // 首先尝试使用追踪数据
        Map<String, PlayerProximityInfo> proximityMap = playerProximityTracking.get(entityId);
        if (proximityMap != null && !proximityMap.isEmpty()) {
            // 遍历所有在时间窗口内接近过盔甲架的玩家
            for (Map.Entry<String, PlayerProximityInfo> entry : proximityMap.entrySet()) {
                String playerName = entry.getKey();
                PlayerProximityInfo proximityInfo = entry.getValue();

                if (!MurderHelperMod.gameState.isRealPlayer(playerName)) {
                    continue;
                }

                // 检查是否在时间窗口内
                long timeDiff = destroyTime - proximityInfo.lastUpdateTime;
                if (timeDiff < 0 || timeDiff > DETECTIVE_IDENTIFICATION_WINDOW) {
                    continue;
                }

                // 排除已经是murderer的玩家
                if (MurderHelperMod.playerTracker.isMurdererLocked(playerName)) {
                    continue;
                }

                // 找到最接近的玩家
                if (proximityInfo.closestDistance < closestDistance) {
                    closestDistance = proximityInfo.closestDistance;
                    newDetective = playerName;
                }
            }
        }

        // 如果追踪数据没有找到，使用备用逻辑：检查当前时刻谁最靠近盔甲架
        if (newDetective == null && mc.theWorld != null) {
            MurderHelperMod.logger.info("[BowDropDetector] No tracking data, using fallback: checking current player positions");

            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player == null) {
                    continue;
                }

                // 排除死亡的幽灵玩家
                if (!isValidPickupCandidate(player)) {
                    continue;
                }

                String playerName = player.getName();

                if (!MurderHelperMod.gameState.isRealPlayer(playerName)) {
                    continue;
                }

                // 排除已经是murderer的玩家
                if (MurderHelperMod.playerTracker.isMurdererLocked(playerName)) {
                    continue;
                }

                // 计算距离
                double distance = calculateDistance(
                        player.posX, player.posY, player.posZ,
                        bowInfo.x, bowInfo.y, bowInfo.z
                );

                // 只考虑在拾取范围内的玩家（稍微放宽范围）
                if (distance <= PICKUP_DISTANCE_THRESHOLD * 2 && distance < closestDistance) {
                    closestDistance = distance;
                    newDetective = playerName;
                }
            }
        }

        // 如果找到新侦探，锁定其角色
        if (newDetective != null) {
            MurderHelperMod.playerTracker.lockDetective(newDetective);
            MurderHelperMod.logger.info("[BowDropDetector] ✓ Locked {} as DETECTIVE (picked up bow at distance {:.2f})",
                    newDetective, closestDistance);
        } else {
            MurderHelperMod.logger.info("[BowDropDetector] Could not identify new detective for armor stand {}", entityId);
        }

        return newDetective;
    }

    /**
     * 识别捡起弓的新侦探（掉落物类型）
     * 逻辑：在盔甲架销毁时距离最近的非murderer玩家
     *
     * @param itemInfo 弓掉落信息
     * @param destroyTime 掉落物销毁时间
     * @return 新侦探名字，如果找不到返回null
     */
    private String identifyNewDetectiveForItem(BowDropInfo itemInfo, long destroyTime) {
        if (mc.theWorld == null) {
            return null;
        }

        String newDetective = null;
        double closestDistance = Double.MAX_VALUE;

        // 遍历所有玩家，找到距离最近的非murderer玩家
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || !MurderHelperMod.gameState.isRealPlayer(player)) {
                continue;
            }

            // 排除死亡的幽灵玩家
            if (!isValidPickupCandidate(player)) {
                continue;
            }

            String playerName = player.getName();

            // 排除已经是murderer的玩家
            if (MurderHelperMod.playerTracker.isMurdererLocked(playerName)) {
                continue;
            }

            // 计算距离
            double distance = calculateDistance(
                    player.posX, player.posY, player.posZ,
                    itemInfo.x, itemInfo.y, itemInfo.z
            );

            // 只考虑在拾取范围内的玩家
            if (distance <= PICKUP_DISTANCE_THRESHOLD && distance < closestDistance) {
                closestDistance = distance;
                newDetective = playerName;
            }
        }

        // 如果找到新侦探，锁定其角色
        if (newDetective != null) {
            MurderHelperMod.playerTracker.lockDetective(newDetective);
            MurderHelperMod.logger.info("[BowDropDetector] ✓ Locked {} as DETECTIVE (picked up bow item at distance {:.2f})",
                    newDetective, closestDistance);
        }

        return newDetective;
    }

    /**
     * 检查玩家是否是有效的弓拾取候选者
     * 排除死亡的幽灵玩家、隐形玩家等
     */
    private boolean isValidPickupCandidate(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        // 排除死亡的玩家（幽灵状态）
        if (player.isDead) {
            return false;
        }

        // 排除隐形的玩家（死亡后的幽灵通常是隐形的）
        if (player.isInvisible()) {
            return false;
        }

        // 排除生命值为0的玩家
        if (player.getHealth() <= 0) {
            return false;
        }

        return true;
    }

    // ==================== 业务逻辑方法 ====================

    /**
     * 当确认弓掉落时调用
     */
    private void onBowDropConfirmed(BowDropInfo info) {
        // 检查是否已经在该位置喊过话
        if (hasShoutedAtPosition(info.x, info.y, info.z)) {
            MurderHelperMod.logger.info("[BowDropDetector] Position already shouted, skipping");
            return;
        }

        // 自动喊话（如果启用）
        if (MurderHelperMod.config.shoutDropBowEnabled) {
            String detectiveName = findDetectiveWhoDroppedBow(info);
            String message = buildBowShoutMessage(detectiveName, info);

            if (mc.thePlayer != null && !message.trim().isEmpty()) {
                mc.thePlayer.sendChatMessage(message);
                markPositionAsShouted(info.x, info.y, info.z);
                MurderHelperMod.logger.info("[BowDropDetector] Auto shout: " + message);
            }
        }

        // 通知BowDropTracker添加到渲染列表
        if (MurderHelperMod.bowDropRenderHandler != null) {
            // 查找世界中对应的实体
            Entity entity = findEntityById(info.entityId);
            if (entity != null) {
                MurderHelperMod.bowDropRenderHandler.addBowEntity(entity);
            }
        }
    }

    /**
     * 当弓被拾取时调用
     */
    private void onBowPickedUp(BowDropInfo info) {
        // 通知BowDropTracker移除渲染
        if (MurderHelperMod.bowDropRenderHandler != null) {
            Entity entity = findEntityById(info.entityId);
            if (entity != null) {
                MurderHelperMod.bowDropRenderHandler.removeBowEntity(entity);
            }
        }

        MurderHelperMod.logger.info("[BowDropDetector] Bow picked up at ({}, {}, {})",
                String.format("%.1f", info.x), String.format("%.1f", info.y), String.format("%.1f", info.z));
    }

    /**
     * 查找掉弓的侦探
     */
    private String findDetectiveWhoDroppedBow(BowDropInfo info) {
        if (mc.theWorld == null) {
            return "Unknown";
        }

        // 收集当前Tab栏中所有侦探的名字
        Set<String> currentDetectives = new HashSet<>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != null) {
                String playerName = player.getName();
                MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(player);
                if (role == MurderHelperMod.PlayerRole.DETECTIVE) {
                    currentDetectives.add(playerName);
                }
            }
        }

        // 获取所有被锁定的侦探列表
        Set<String> lockedDetectives = MurderHelperMod.playerTracker.getLockedDetectives();

        // 找出缺失的侦探（在锁定列表中但不在当前Tab栏中）
        for (String lockedDetective : lockedDetectives) {
            if (!currentDetectives.contains(lockedDetective)) {
                MurderHelperMod.logger.info("[BowDropDetector] Found missing detective: " + lockedDetective);
                return lockedDetective;
            }
        }

        // 如果没有找到缺失的侦探，找最近的侦探
        if (!currentDetectives.isEmpty()) {
            double nearestDistance = Double.MAX_VALUE;
            String nearestDetective = "Unknown";

            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player != null) {
                    String playerName = player.getName();

                    if (currentDetectives.contains(playerName)) {
                        double distance = calculateDistance(player.posX, player.posY, player.posZ, info.x, info.y, info.z);
                        if (distance < nearestDistance && distance < 20.0) {
                            nearestDistance = distance;
                            nearestDetective = playerName;
                        }
                    }
                }
            }

            MurderHelperMod.logger.info("[BowDropDetector] No missing detective, using nearest: " + nearestDetective);
            return nearestDetective;
        }

        return "Unknown";
    }

    /**
     * 构建弓喊话消息
     */
    private String buildBowShoutMessage(String detectiveName, BowDropInfo info) {
        // 使用ShoutMessageBuilder构建消息
        return ShoutMessageBuilder.buildBowDropShout(
                MurderHelperMod.config.shoutDropBowMessage,
                detectiveName,
                info.x,
                info.y,
                info.z,
                MurderHelperMod.config.replaceDropBowFrom,
                MurderHelperMod.config.replaceDropBowTo,
                MurderHelperMod.logger
        );
    }

    /**
     * 检查某个位置是否已经喊过话
     */
    private boolean hasShoutedAtPosition(double x, double y, double z) {
        for (String posKey : shoutedBowPositions) {
            String[] parts = posKey.split(",");
            double savedX = Double.parseDouble(parts[0]);
            double savedY = Double.parseDouble(parts[1]);
            double savedZ = Double.parseDouble(parts[2]);

            double distance = calculateDistance(x, y, z, savedX, savedY, savedZ);
            if (distance < POSITION_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标记某个位置已经喊过话
     */
    private void markPositionAsShouted(double x, double y, double z) {
        String posKey = String.format("%.1f,%.1f,%.1f", x, y, z);
        shoutedBowPositions.add(posKey);
        MurderHelperMod.logger.info("[BowDropDetector] Marked position as shouted: " + posKey);
    }

    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
    }

    /**
     * 根据实体ID查找世界中的实体
     */
    private Entity findEntityById(int entityId) {
        if (mc.theWorld == null) {
            return null;
        }

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 获取所有追踪的弓掉落信息
     */
    public Collection<BowDropInfo> getAllBowDrops() {
        List<BowDropInfo> allDrops = new ArrayList<>();
        allDrops.addAll(armorStandBows.values());
        allDrops.addAll(itemBows.values());
        return allDrops;
    }

    /**
     * 清空所有追踪数据
     */
    public void clear() {
        armorStandBows.clear();
        itemBows.clear();
        shoutedBowPositions.clear();
        playerProximityTracking.clear();
        MurderHelperMod.logger.info("[BowDropDetector] Cleared all bow tracking data");
    }
}
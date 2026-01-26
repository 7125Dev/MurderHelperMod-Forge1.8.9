package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.util.ItemClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弓箭射击检测器
 * 检测玩家的弓箭状态：持弓、拉弓、射箭、冷却
 * 追踪箭矢位置
 */
public class BowShotDetector {

    private final Minecraft mc;

    // 玩家弓箭信息映射 <玩家名, BowInfo>
    private final Map<String, BowInfo> bowByPlayer = new ConcurrentHashMap<>();

    // 箭矢追踪 <实体ID, ArrowInfo>
    private final Map<Integer, ArrowInfo> arrowMap = new ConcurrentHashMap<>();

    // 玩家到箭矢的映射 <玩家名, List<箭矢ID>>
    private final Map<String, List<Integer>> playerArrows = new ConcurrentHashMap<>();

    // 实体ID -> 玩家名映射（缓存）
    private final Map<Integer, String> entityIdToPlayerName = new ConcurrentHashMap<>();

    // 常量
    private static final int ARROW_ENTITY_TYPE = 60;
    private static final long ARROW_TIMEOUT = 5000;
    private static final int CLEANUP_INTERVAL = 20; // 每20帧清理一次

    private int cleanupCounter = 0;

    public BowShotDetector() {
        this.mc = Minecraft.getMinecraft();
    }

    /**
     * 弓箭信息类
     */
    public static class BowInfo {
        public String playerName;
        public int playerEntityId;

        // 弓状态
        public ItemStack bowStack;
        public ItemClassifier.BowCategory bowCategory;
        public String bowRegistryName;

        // 手持和拉弓状态
        public HoldingState holdingState;
        public DrawState drawState;
        public ShotState shotState;

        // 时间跟踪
        public long lastStateChange;
        public long drawStartTime;
        public long lastShotTime;

        // 常量
        private static final long DETECTIVE_BOW_COOLDOWN_MS = 5000;
        private static final long NORMAL_BOW_SHOT_DISPLAY_MS = 3000;
        private static final long BOW_FULL_DRAW_TIME_MS = 1000;

        public BowInfo(String playerName, int playerEntityId) {
            this.playerName = playerName;
            this.playerEntityId = playerEntityId;
            this.holdingState = HoldingState.NOT_HOLDING;
            this.drawState = DrawState.NONE;
            this.shotState = ShotState.READY;
            this.lastStateChange = System.currentTimeMillis();
            this.drawStartTime = 0;
            this.lastShotTime = 0;
        }

        /**
         * 更新弓（检测弓类型变化）
         */
        public void updateBow(ItemStack newBow, String registryName) {
            ItemClassifier.BowCategory newCategory = ItemClassifier.getBowCategory(newBow);

            // 如果弓类型改变，重置射击状态
            if (this.bowCategory != newCategory) {
                this.shotState = ShotState.READY;
                this.lastShotTime = 0;
                MurderHelperMod.logger.info("[BowDetector] {} bow type changed: {} -> {}",
                        playerName, this.bowCategory, newCategory);
            }

            this.bowStack = newBow;
            this.bowCategory = newCategory;
            this.bowRegistryName = registryName;
        }

        /**
         * 设置手持状态
         */
        public void setHoldingState(HoldingState state) {
            if (this.holdingState != state) {
                this.holdingState = state;
                this.lastStateChange = System.currentTimeMillis();

                // 如果不再手持，取消拉弓状态
                if (state == HoldingState.NOT_HOLDING) {
                    this.drawState = DrawState.NONE;
                    this.drawStartTime = 0;
                }

                MurderHelperMod.logger.info("[BowDetector] {} holding: {}", playerName, state);
            }
        }

        /**
         * 开始拉弓
         */
        public void startDrawing() {
            if (drawState == DrawState.NONE) {
                drawState = DrawState.DRAWING;
                drawStartTime = System.currentTimeMillis();
                lastStateChange = System.currentTimeMillis();

                // 如果是普通弓，且之前处于SHOT状态，拉弓动作表示还有箭
                if (shotState == ShotState.SHOT && bowCategory == ItemClassifier.BowCategory.NORMAL_BOW) {
                    shotState = ShotState.READY;
                    MurderHelperMod.logger.info("[BowDetector] {} normal bow recovered (started drawing)",
                            playerName);
                }

                MurderHelperMod.logger.debug("[BowDetector] {} started drawing bow", playerName);
            }
        }

        /**
         * 停止拉弓
         */
        public void stopDrawing() {
            if (drawState != DrawState.NONE) {
                drawState = DrawState.NONE;
                drawStartTime = 0;
                lastStateChange = System.currentTimeMillis();
                MurderHelperMod.logger.debug("[BowDetector] {} stopped drawing bow", playerName);
            }
        }

        /**
         * 记录射击
         */
        public void recordShot() {
            this.lastShotTime = System.currentTimeMillis();
            this.drawState = DrawState.NONE;
            this.drawStartTime = 0;
            this.lastStateChange = System.currentTimeMillis();

            // 根据弓类型设置射击状态
            ShotState oldState = this.shotState;
            switch (bowCategory) {
                case DETECTIVE_BOW:
                    this.shotState = ShotState.COOLDOWN;
                    break;
                case NORMAL_BOW:
                    this.shotState = ShotState.SHOT;
                    break;
                case KALI_BOW:
                    // Kali弓无限箭，保持READY状态
                    this.shotState = ShotState.READY;
                    break;
                default:
                    break;
            }

            MurderHelperMod.logger.info("[BowDetector] {} shot arrow: {} -> {} (bow type: {})",
                    playerName, oldState, this.shotState, bowCategory);
        }

        /**
         * 更新状态（每帧调用）
         */
        public void update() {
            long currentTime = System.currentTimeMillis();

            // 更新拉弓状态（从DRAWING到READY_TO_SHOOT）
            if (drawState == DrawState.DRAWING && drawStartTime > 0) {
                if (currentTime - drawStartTime >= BOW_FULL_DRAW_TIME_MS) {
                    drawState = DrawState.READY_TO_SHOOT;
                }
            }

            // 更新侦探弓冷却
            if (shotState == ShotState.COOLDOWN && bowCategory == ItemClassifier.BowCategory.DETECTIVE_BOW) {
                if (currentTime - lastShotTime >= DETECTIVE_BOW_COOLDOWN_MS) {
                    shotState = ShotState.READY;
                    MurderHelperMod.logger.info("[BowDetector] {} detective bow cooldown finished",
                            playerName);
                }
            }

            // 更新普通弓显示
            if (shotState == ShotState.SHOT && bowCategory == ItemClassifier.BowCategory.NORMAL_BOW) {
                if (currentTime - lastShotTime >= NORMAL_BOW_SHOT_DISPLAY_MS) {
                    shotState = ShotState.READY;
                }
            }
        }

        // Getters
        public HoldingState getHoldingState() { return holdingState; }
        public DrawState getDrawState() { return drawState; }
        public ShotState getShotState() { return shotState; }
        public ItemStack getBowStack() { return bowStack; }
        public ItemClassifier.BowCategory getBowCategory() { return bowCategory; }

        public double getCooldownRemainingSeconds() {
            if (shotState != ShotState.COOLDOWN) return 0.0;
            long currentTime = System.currentTimeMillis();
            long remaining = DETECTIVE_BOW_COOLDOWN_MS - (currentTime - lastShotTime);
            return Math.max(0, remaining / 1000.0);
        }

        public long getTimeSinceLastChange() {
            return System.currentTimeMillis() - lastStateChange;
        }
    }

    /**
     * 箭矢信息类
     */
    public static class ArrowInfo {
        public int entityId;
        public int shooterEntityId;
        public String shooterName;
        public Vec3 position;
        public Vec3 motion;
        public long spawnTime;

        public ArrowInfo(int entityId, int shooterEntityId, String shooterName, Vec3 position) {
            this.entityId = entityId;
            this.shooterEntityId = shooterEntityId;
            this.shooterName = shooterName;
            this.position = position;
            this.motion = new Vec3(0, 0, 0);
            this.spawnTime = System.currentTimeMillis();
        }

        /**
         * 获取箭矢到某个位置的距离
         */
        public double getDistanceTo(Vec3 targetPos) {
            if (position == null || targetPos == null) return Double.MAX_VALUE;
            return Math.sqrt(
                    Math.pow(position.xCoord - targetPos.xCoord, 2) +
                            Math.pow(position.yCoord - targetPos.yCoord, 2) +
                            Math.pow(position.zCoord - targetPos.zCoord, 2)
            );
        }

        /**
         * 获取箭矢到玩家的距离
         */
        public double getDistanceToPlayer(EntityPlayer player) {
            if (player == null) return Double.MAX_VALUE;
            return getDistanceTo(new Vec3(player.posX, player.posY, player.posZ));
        }

        /**
         * 获取存活时间（毫秒）
         */
        public long getAge() {
            return System.currentTimeMillis() - spawnTime;
        }
    }

    /**
     * 持弓状态
     */
    public enum HoldingState {
        NOT_HOLDING,
        HOLDING
    }

    /**
     * 拉弓状态
     */
    public enum DrawState {
        NONE,
        DRAWING,
        READY_TO_SHOOT
    }

    /**
     * 射击状态
     */
    public enum ShotState {
        READY,
        SHOT,
        COOLDOWN
    }

    // ==================== 公共API ====================

    /**
     * 获取玩家弓箭信息
     */
    public BowInfo getBowInfo(String playerName) {
        return bowByPlayer.get(playerName);
    }

    /**
     * 检查玩家是否持有弓
     */
    public boolean isHoldingBow(String playerName) {
        BowInfo info = bowByPlayer.get(playerName);
        return info != null && info.holdingState == HoldingState.HOLDING;
    }

    /**
     * 获取玩家射出的所有箭矢
     */
    public List<ArrowInfo> getPlayerArrows(String playerName) {
        List<Integer> arrowIds = playerArrows.get(playerName);
        if (arrowIds == null || arrowIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ArrowInfo> arrows = new ArrayList<>();
        for (Integer arrowId : arrowIds) {
            ArrowInfo arrow = arrowMap.get(arrowId);
            if (arrow != null) {
                arrows.add(arrow);
            }
        }
        return arrows;
    }

    /**
     * 获取离本地玩家最近的箭矢
     */
    public ArrowInfo getNearestArrowToLocalPlayer(String shooterName) {
        if (mc.thePlayer == null) return null;

        List<ArrowInfo> arrows = getPlayerArrows(shooterName);
        if (arrows.isEmpty()) return null;

        ArrowInfo nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (ArrowInfo arrow : arrows) {
            double distance = arrow.getDistanceToPlayer(mc.thePlayer);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = arrow;
            }
        }

        return nearest;
    }

    /**
     * 获取所有持弓的玩家
     */
    public List<BowInfo> getAllBowHolders() {
        List<BowInfo> result = new ArrayList<>();
        bowByPlayer.values().forEach(info -> {
            if (info.holdingState == HoldingState.HOLDING) {
                result.add(info);
            }
        });
        return result;
    }

    /**
     * 每帧更新
     */
    public void update() {
        // 更新所有玩家的弓箭状态
        bowByPlayer.values().forEach(BowInfo::update);

        // 降低清理频率
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupExpiredArrows();
        }
    }

    /**
     * 清理过期的箭矢
     */
    private void cleanupExpiredArrows() {
        long currentTime = System.currentTimeMillis();

        // 清理过期箭矢
        arrowMap.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue().spawnTime > ARROW_TIMEOUT;
            if (expired) {
                // 从玩家箭矢列表中移除
                String shooterName = entry.getValue().shooterName;
                List<Integer> arrows = playerArrows.get(shooterName);
                if (arrows != null) {
                    arrows.remove((Integer) entry.getKey());
                }
            }
            return expired;
        });

        // 清理空的箭矢列表
        playerArrows.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 清除所有数据
     */
    public void clear() {
        bowByPlayer.clear();
        arrowMap.clear();
        playerArrows.clear();
        entityIdToPlayerName.clear();
        cleanupCounter = 0;
        MurderHelperMod.logger.info("[BowDetector] Cleared all data");
    }

    /**
     * 注册玩家（用于缓存实体ID）
     */
    public void registerPlayer(String playerName, int entityId) {
        entityIdToPlayerName.put(entityId, playerName);
    }

    // ==================== 数据包处理 ====================

    /**
     * 处理实体装备数据包
     */
    public void handleEntityEquipment(S04PacketEntityEquipment packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        try {
            int entityId = packet.getEntityID();
            int equipmentSlot = packet.getEquipmentSlot();
            ItemStack itemStack = packet.getItemStack();

            // 只处理主手装备（slot 0）
            if (equipmentSlot != 0) return;

            String playerName = getPlayerNameByEntityId(entityId);
            if (playerName == null) return;

            BowInfo info = bowByPlayer.computeIfAbsent(playerName,
                    name -> new BowInfo(name, entityId));
            info.playerEntityId = entityId;

            // 检查是否是弓
            ItemClassifier.BowCategory category = ItemClassifier.getBowCategory(itemStack);
            String registryName = getItemRegistryName(itemStack);

            if (category != ItemClassifier.BowCategory.NONE) {
                // 装备了弓
                info.updateBow(itemStack, registryName);
                info.setHoldingState(HoldingState.HOLDING);
                MurderHelperMod.logger.info("[BowDetector] {} equipped bow: {} ({})",
                        playerName, category, registryName);
            } else {
                // 卸下弓或装备其他物品
                if (info.getBowStack() != null) {
                    info.setHoldingState(HoldingState.NOT_HOLDING);
                    MurderHelperMod.logger.info("[BowDetector] {} unequipped bow", playerName);
                }
            }

        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling EntityEquipment", e);
        }
    }

    /**
     * 处理实体元数据数据包
     * 检测拉弓动作（通过检测使用物品标记）
     */
    public void handleEntityMetadata(S1CPacketEntityMetadata packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        try {
            int entityId = packet.getEntityId();
            String playerName = getPlayerNameByEntityId(entityId);
            if (playerName == null) return;

            BowInfo info = bowByPlayer.get(playerName);
            if (info == null || info.holdingState != HoldingState.HOLDING) return;

            List<DataWatcher.WatchableObject> dataManagerEntries = packet.func_149376_c();
            if (dataManagerEntries == null || dataManagerEntries.isEmpty()) return;

            // 获取玩家实体以检查手持物品
            Entity entity = Minecraft.getMinecraft().theWorld.getEntityByID(entityId);
            if (!(entity instanceof EntityPlayer)) return;
            EntityPlayer player = (EntityPlayer) entity;

            // 查找实体标记
            for (DataWatcher.WatchableObject entry : dataManagerEntries) {
                if (entry == null) continue;
                try {
                    int dataValueId = entry.getDataValueId();
                    if (dataValueId == 0) {
                        Object obj = entry.getObject();
                        if (obj instanceof Byte) {
                            byte flags = (Byte) obj;
                            boolean isUsingItem = (flags & 0x10) != 0;

                            if (isUsingItem) {
                                // 额外检查：确保手持的是弓
                                ItemStack heldItem = player.getHeldItem();
                                if (heldItem != null && heldItem.getItem() instanceof ItemBow) {
                                    info.startDrawing();
                                }
                            } else if (info.drawState != DrawState.NONE) {
                                info.stopDrawing();
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling EntityMetadata", e);
        }
    }

    /**
     * 处理生成对象数据包
     * 检测箭矢实体生成，记录箭矢位置
     */
    public void handleSpawnObject(S0EPacketSpawnObject packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        try {
            int objectType = packet.getType();

            // 60 = 箭矢实体
            if (objectType != ARROW_ENTITY_TYPE) return;

            int entityId = packet.getEntityID();
            int shooterEntityId = packet.func_149009_m(); // field_149020_k

            if (shooterEntityId == 0) return;

            String playerName = getPlayerNameByEntityId(shooterEntityId);
            if (playerName == null) return;

            BowInfo info = bowByPlayer.get(playerName);
            if (info == null) return;

            // 记录射击
            info.recordShot();

            // 记录箭矢
            Vec3 position = new Vec3(
                    packet.getX() / 32.0,
                    packet.getY() / 32.0,
                    packet.getZ() / 32.0
            );

            ArrowInfo arrow = new ArrowInfo(entityId, shooterEntityId, playerName, position);

            // 保存初始速度
            arrow.motion = new Vec3(
                    packet.getSpeedX() / 8000.0,
                    packet.getSpeedY() / 8000.0,
                    packet.getSpeedZ() / 8000.0
            );

            arrowMap.put(entityId, arrow);

            // 添加到玩家的箭矢列表
            playerArrows.computeIfAbsent(playerName, k -> new ArrayList<>()).add(entityId);

            MurderHelperMod.logger.info("[BowDetector] {} shot arrow #{} at {}",
                    playerName, entityId, position);

        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling SpawnObject", e);
        }
    }

    /**
     * 处理实体相对移动数据包
     * 更新箭矢位置
     */
    public void handleEntityRelMove(S14PacketEntity.S15PacketEntityRelMove packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        try {
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity == null) return;

            int entityId = entity.getEntityId();
            ArrowInfo arrow = arrowMap.get(entityId);

            if (arrow != null && arrow.position != null) {
                // 更新箭矢位置（相对移动，除以4096）
                arrow.position = arrow.position.addVector(
                        packet.func_149062_c() / 4096.0,
                        packet.func_149061_d() / 4096.0,
                        packet.func_149064_e() / 4096.0
                );
            }
        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling EntityRelMove", e);
        }
    }

    /**
     * 处理实体传送数据包
     * 更新箭矢位置
     */
    public void handleEntityTeleport(S18PacketEntityTeleport packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        try {
            int entityId = packet.getEntityId();
            ArrowInfo arrow = arrowMap.get(entityId);

            if (arrow != null) {
                Vec3 newPos = new Vec3(
                        packet.getX() / 32.0,
                        packet.getY() / 32.0,
                        packet.getZ() / 32.0
                );
                arrow.position = newPos;
                MurderHelperMod.logger.debug("[BowDetector] Arrow {} teleported to {}",
                        entityId, newPos);
            }
        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling EntityTeleport", e);
        }
    }

    /**
     * 处理实体销毁数据包
     */
    public void handleDestroyEntities(S13PacketDestroyEntities packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        try {
            int[] entityIds = packet.getEntityIDs();
            if (entityIds != null) {
                for (int entityId : entityIds) {
                    ArrowInfo removed = arrowMap.remove(entityId);
                    if (removed != null) {
                        // 从玩家箭矢列表中移除
                        List<Integer> arrows = playerArrows.get(removed.shooterName);
                        if (arrows != null) {
                            arrows.remove((Integer) entityId);
                        }
                        MurderHelperMod.logger.debug("[BowDetector] Arrow {} destroyed", entityId);
                    }
                }
            }
        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error handling DestroyEntities", e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 通过实体ID获取玩家名（带缓存）
     */
    private String getPlayerNameByEntityId(int entityId) {
        String cached = entityIdToPlayerName.get(entityId);
        if (cached != null) {
            return cached;
        }

        EntityPlayer player = getPlayerByEntityId(entityId);
        if (player != null) {
            String name = player.getName();
            entityIdToPlayerName.put(entityId, name);
            return name;
        }

        return null;
    }

    /**
     * 通过实体ID获取玩家实体
     */
    private EntityPlayer getPlayerByEntityId(int entityId) {
        if (mc == null || mc.theWorld == null) return null;

        try {
            Entity entity = mc.theWorld.getEntityByID(entityId);
            return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
        } catch (Exception e) {
            MurderHelperMod.logger.error("[BowDetector] Error getting player by entity ID", e);
            return null;
        }
    }

    /**
     * 获取物品的注册名
     */
    private String getItemRegistryName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return null;
        }

        Item item = itemStack.getItem();
        ResourceLocation registryName = Item.itemRegistry.getNameForObject(item);
        return registryName != null ? registryName.toString() : null;
    }
}
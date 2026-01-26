package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.util.ItemClassifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞刀武器检测器
 * 追踪玩家的持刀状态和投掷物状态
 * 注意：玩家可以同时持刀和有飞刀在飞行中
 */
public class KnifeThrownDetector {

    /**
     * 手持状态
     */
    public enum HoldingState {
        NOT_HOLDING,    // 未持刀
        HOLDING         // 持刀中
    }

    /**
     * 飞刀状态
     */
    public enum KnifeState {
        NONE,           // 无飞刀
        IN_FLIGHT,      // 飞行中
        COOLDOWN        // 冷却中（刚销毁，还在CD）
    }

    /**
     * 投掷物信息
     */
    public static class ProjectileInfo {
        public int armorStandEntityId;      // 盔甲架实体ID
        public Vec3 position;               // 当前位置
        public long throwTime;              // 投掷时间戳

        public ProjectileInfo(int armorStandEntityId, Vec3 position) {
            this.armorStandEntityId = armorStandEntityId;
            this.position = position;
            this.throwTime = System.currentTimeMillis();
        }
    }

    /**
     * 玩家武器信息
     */
    public static class WeaponInfo {
        public String playerName;           // 玩家名字
        public int playerEntityId;          // 玩家实体ID

        // 手持状态
        public boolean isHolding;           // 是否手持飞刀
        public String weaponRegistryName;   // 武器物品注册名
        public boolean hasSlowEffect;       // 是否有持刀减速效果

        // 飞刀状态
        public ProjectileInfo projectile;   // 当前飞行中的投掷物
        public long lastThrowTime;          // 最后投掷时间
        public long lastDestroyTime;        // 最后销毁时间（用于计算冷却）

        // 其他
        public long lastStateChange;        // 最后状态改变时间

        public WeaponInfo(String playerName, int playerEntityId) {
            this.playerName = playerName;
            this.playerEntityId = playerEntityId;
            this.isHolding = false;
            this.weaponRegistryName = null;
            this.hasSlowEffect = false;
            this.projectile = null;
            this.lastThrowTime = 0;
            this.lastDestroyTime = 0;
            this.lastStateChange = System.currentTimeMillis();
        }

        /**
         * 获取手持状态
         */
        public HoldingState getHoldingState() {
            return isHolding ? HoldingState.HOLDING : HoldingState.NOT_HOLDING;
        }

        /**
         * 获取飞刀状态
         */
        public KnifeState getKnifeState() {
            if (projectile != null) {
                return KnifeState.IN_FLIGHT;
            }

            // 检查是否在冷却中（投掷后5秒内）
            long timeSinceDestroy = System.currentTimeMillis() - lastDestroyTime;
            if (lastDestroyTime > 0 && timeSinceDestroy < 5000) {
                return KnifeState.COOLDOWN;
            }

            return KnifeState.NONE;
        }

        /**
         * 获取冷却剩余时间（毫秒）
         */
        public long getCooldownRemaining() {
            if (getKnifeState() != KnifeState.COOLDOWN) {
                return 0;
            }
            long elapsed = System.currentTimeMillis() - lastDestroyTime;
            return Math.max(0, 5000 - elapsed);
        }

        /**
         * 获取冷却剩余时间（秒，带小数）
         */
        public double getCooldownRemainingSeconds() {
            return getCooldownRemaining() / 1000.0;
        }

        /**
         * 更新持刀状态
         */
        public void setHolding(boolean holding, String registryName) {
            if (this.isHolding != holding) {
                this.isHolding = holding;
                this.weaponRegistryName = holding ? registryName : null;
                this.lastStateChange = System.currentTimeMillis();
                MurderHelperMod.logger.info("[WeaponDetector] {} holding: {} (item: {})",
                        playerName, holding, registryName);
            }
        }

        /**
         * 更新减速效果状态
         */
        public void setSlowEffect(boolean hasSlow) {
            this.hasSlowEffect = hasSlow;
        }

        /**
         * 设置投掷物
         */
        public void setProjectile(ProjectileInfo projectile) {
            if (projectile != null) {
                // 开始投掷
                this.projectile = projectile;
                this.lastThrowTime = System.currentTimeMillis();
                this.lastStateChange = System.currentTimeMillis();
                MurderHelperMod.logger.info("[WeaponDetector] {} threw knife (armorstand: {})",
                        playerName, projectile.armorStandEntityId);
            } else if (this.projectile != null) {
                // 飞刀销毁
                this.lastDestroyTime = System.currentTimeMillis();
                this.projectile = null;
                this.lastStateChange = System.currentTimeMillis();
                MurderHelperMod.logger.info("[WeaponDetector] {} knife destroyed, entering cooldown",
                        playerName);
            }
        }

        /**
         * 获取距离上次状态改变的时间
         */
        public long getTimeSinceLastChange() {
            return System.currentTimeMillis() - lastStateChange;
        }

        /**
         * 获取距离上次投掷的时间
         */
        public long getTimeSinceLastThrow() {
            if (lastThrowTime == 0) return Long.MAX_VALUE;
            return System.currentTimeMillis() - lastThrowTime;
        }
    }


    // 玩家名 -> 武器信息
    private final Map<String, WeaponInfo> weaponByPlayer = new ConcurrentHashMap<>();

    // 盔甲架实体ID -> 武器信息（用于追踪投掷物）
    private final Map<Integer, WeaponInfo> weaponByArmorStand = new ConcurrentHashMap<>();

    // 实体ID -> 玩家名映射
    private final Map<Integer, String> entityIdToPlayerName = new ConcurrentHashMap<>();

    // 等待关联的盔甲架（刚创建，还未确定所属玩家）
    private final Map<Integer, PendingArmorStand> pendingArmorStands = new ConcurrentHashMap<>();

    private static class PendingArmorStand {
        long createTime;
        Vec3 position;
        String itemRegistryName;  // 盔甲架装备的物品注册名

        PendingArmorStand(long createTime, Vec3 position) {
            this.createTime = createTime;
            this.position = position;
            this.itemRegistryName = null;
        }
    }

    // 盔甲架对象类型
    private static final int ARMOR_STAND_OBJECT_TYPE = 78;
    private static final long PROJECTILE_TIMEOUT = 15000;  // 投掷物超时
    private static final long PENDING_TIMEOUT = 1000;  // 待确认盔甲架超时

    // ==================== 数据包处理方法 ====================

    /**
     * 处理实体装备数据包
     */
    public void handleEntityEquipment(S04PacketEntityEquipment packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        int entityId = packet.getEntityID();
        int slot = packet.getEquipmentSlot();
        ItemStack itemStack = packet.getItemStack();

        if (slot != 0) return; // 只关注主手

        String playerName = getPlayerNameByEntityId(entityId);

        if (playerName != null) {
            // 玩家装备/卸下飞刀 - 使用标准检测
            boolean isKnife = ItemClassifier.isMurderWeapon(itemStack);
            String registryName = getItemRegistryName(itemStack);
            MurderHelperMod.logger.info("[WeaponDetector] Player {} equipment change: isKnife={}, item={}",
                    playerName, isKnife, registryName);
            handlePlayerEquipment(playerName, entityId, isKnife, registryName);
        } else {
            // 盔甲架装备物品 - 只检查是否是待确认的盔甲架
            if (pendingArmorStands.containsKey(entityId) && itemStack != null) {
                String registryName = getItemRegistryName(itemStack);

                // 对盔甲架：只要装备了物品就尝试匹配
                // 通过物品注册名来判断是否和某个持刀玩家的武器一致
                MurderHelperMod.logger.info("[WeaponDetector] ArmorStand {} equipped item: {}",
                        entityId, registryName);
                handleArmorStandEquipment(entityId, registryName);
            }
        }
    }

    /**
     * 处理生成对象数据包
     */
    public void handleSpawnObject(S0EPacketSpawnObject packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        if (packet.getType() == ARMOR_STAND_OBJECT_TYPE) {
            int entityId = packet.getEntityID();
            Vec3 position = new Vec3(
                    packet.getX() / 32.0,
                    packet.getY() / 32.0,
                    packet.getZ() / 32.0
            );

            pendingArmorStands.put(entityId,
                    new PendingArmorStand(System.currentTimeMillis(), position));

            MurderHelperMod.logger.info("[WeaponDetector] ArmorStand spawned: {} at {}",
                    entityId, position);
        }
    }

    /**
     * 处理实体元数据数据包
     */
    public void handleEntityMetadata(S1CPacketEntityMetadata packet) {
        // 预留接口，暂不处理
    }

    /**
     * 处理实体相对移动数据包
     */
    public void handleEntityRelMove(S14PacketEntity.S15PacketEntityRelMove packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        Entity entity = packet.getEntity(MurderHelperMod.mc.theWorld);
        if (entity == null) return;

        int entityId = entity.getEntityId();
        WeaponInfo info = weaponByArmorStand.get(entityId);

        if (info != null && info.projectile != null && info.projectile.position != null) {
            info.projectile.position = info.projectile.position.addVector(
                    packet.func_149062_c() / 4096.0,
                    packet.func_149061_d() / 4096.0,
                    packet.func_149064_e() / 4096.0
            );
            // 注意：此处不加日志，因为移动包非常频繁
        }
    }

    /**
     * 处理实体传送数据包
     */
    public void handleEntityTeleport(S18PacketEntityTeleport packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        int entityId = packet.getEntityId();
        WeaponInfo info = weaponByArmorStand.get(entityId);

        if (info != null && info.projectile != null) {
            Vec3 newPos = new Vec3(
                    packet.getX() / 32.0,
                    packet.getY() / 32.0,
                    packet.getZ() / 32.0
            );
            info.projectile.position = newPos;
            MurderHelperMod.logger.info("[WeaponDetector] Projectile {} teleported to {}",
                    entityId, newPos);
        }
    }

    /**
     * 处理实体销毁数据包
     */
    public void handleDestroyEntities(S13PacketDestroyEntities packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        for (int entityId : packet.getEntityIDs()) {
            // 移除投掷物
            WeaponInfo info = weaponByArmorStand.remove(entityId);
            if (info != null) {
                MurderHelperMod.logger.info("[WeaponDetector] Projectile {} destroyed for player {}",
                        entityId, info.playerName);
                info.setProjectile(null);
            }

            // 清理待确认盔甲架
            PendingArmorStand removed = pendingArmorStands.remove(entityId);
            if (removed != null) {
                MurderHelperMod.logger.info("[WeaponDetector] Pending ArmorStand {} removed", entityId);
            }
        }
    }

    /**
     * 处理实体属性数据包
     * 关键：通过移动速度修正器的变化来检测投掷
     */
    public void handleEntityProperties(S20PacketEntityProperties packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) {
            return;
        }
        int entityId = packet.getEntityId();
        String playerName = getPlayerNameByEntityId(entityId);

        if (playerName == null) return;

        // 检查是否有移动速度减少修正器（-30%）
        boolean hasSlowModifier = packet.func_149441_d().stream()
                .anyMatch(snapshot -> {
                    if ("generic.movementSpeed".equals(snapshot.func_151409_a())) {
                        return snapshot.func_151408_c().stream()
                                .anyMatch(modifier ->
                                        modifier.getAmount() < -0.25 && modifier.getOperation() == 2);
                    }
                    return false;
                });

        WeaponInfo info = weaponByPlayer.get(playerName);
        if (info != null) {
            boolean hadSlowEffect = info.hasSlowEffect;
            info.setSlowEffect(hasSlowModifier);

            // 检测投掷：减速效果消失，但依然持刀
            if (hadSlowEffect && !hasSlowModifier && info.isHolding) {
                MurderHelperMod.logger.info("[WeaponDetector] Detected throw by {}: slow effect removed while holding knife",
                        playerName);
                onPlayerThrowKnife(playerName);
            }
        }
    }

    // ==================== 内部处理逻辑 ====================

    /**
     * 处理玩家装备飞刀
     */
    private void handlePlayerEquipment(String playerName, int entityId, boolean hasKnife, String registryName) {
        WeaponInfo info = weaponByPlayer.computeIfAbsent(playerName,
                name -> new WeaponInfo(name, entityId));
        info.playerEntityId = entityId;
        info.setHolding(hasKnife, registryName);

        if (hasKnife) {
            MurderHelperMod.logger.info("[WeaponDetector] {} equipped knife ({})", playerName, registryName);
        }
    }

    /**
     * 玩家投掷飞刀（检测到减速效果消失）
     */
    private void onPlayerThrowKnife(String playerName) {
        MurderHelperMod.logger.info("[WeaponDetector] {} throwing knife (slow effect removed)",
                playerName);
        // 标记玩家正在投掷，等待盔甲架装备确认
    }

    /**
     * 盔甲架装备物品（确认为投掷物）
     * 通过物品基础注册名匹配投掷者
     */
    private void handleArmorStandEquipment(int armorStandEntityId, String itemRegistryName) {
        PendingArmorStand pending = pendingArmorStands.get(armorStandEntityId);
        if (pending == null) {
            MurderHelperMod.logger.warn("[WeaponDetector] ArmorStand {} equipped but not in pending list",
                    armorStandEntityId);
            return;
        }

        // 如果盔甲架装备的不是有效物品，忽略
        if (itemRegistryName == null) {
            MurderHelperMod.logger.debug("[WeaponDetector] ArmorStand {} equipped null item, ignored",
                    armorStandEntityId);
            pendingArmorStands.remove(armorStandEntityId);
            return;
        }

        pending.itemRegistryName = itemRegistryName;

        // 提取基础物品名（去除NBT）
        String baseItemName = extractBaseItemName(itemRegistryName);

        long currentTime = System.currentTimeMillis();
        WeaponInfo bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        MurderHelperMod.logger.debug("[WeaponDetector] Searching for thrower of item: {} (base: {})",
                itemRegistryName, baseItemName);

        // 查找持有相同基础物品的玩家
        for (WeaponInfo info : weaponByPlayer.values()) {
            // 条件1：必须正在持刀
            if (!info.isHolding) {
                continue;
            }

            // 条件2：不能已经有投掷物在空中
            if (info.projectile != null) {
                continue;
            }

            // 条件3：物品基础名称必须匹配
            String playerBaseItem = extractBaseItemName(info.weaponRegistryName);
            if (playerBaseItem == null || !playerBaseItem.equals(baseItemName)) {
                MurderHelperMod.logger.debug("  - {} item mismatch: {} vs {}",
                        info.playerName, playerBaseItem, baseItemName);
                continue;
            }

            // 计算玩家到盔甲架生成位置的距离
            EntityPlayer player = getPlayerByEntityId(info.playerEntityId);
            if (player == null) {
                continue;
            }

            double distance = Math.sqrt(
                    Math.pow(player.posX - pending.position.xCoord, 2) +
                            Math.pow(player.posY - pending.position.yCoord, 2) +
                            Math.pow(player.posZ - pending.position.zCoord, 2)
            );

            MurderHelperMod.logger.debug("  - {} is candidate: distance={:.1f}m",
                    info.playerName, distance);

            // 选择距离最近的玩家（合理范围内：<20格）
            if (distance < 20.0 && distance < minDistance) {
                bestMatch = info;
                minDistance = distance;
            }
        }

        if (bestMatch == null) {
            MurderHelperMod.logger.warn("[WeaponDetector] ArmorStand {} equipped {} but no matching thrower found",
                    armorStandEntityId, baseItemName);

            // 打印当前所有持刀玩家（调试用）
            MurderHelperMod.logger.debug("  Current knife holders:");
            for (WeaponInfo info : weaponByPlayer.values()) {
                if (info.isHolding) {
                    MurderHelperMod.logger.debug("    - {} holding {}, hasProjectile={}",
                            info.playerName,
                            extractBaseItemName(info.weaponRegistryName),
                            info.projectile != null);
                }
            }

            pendingArmorStands.remove(armorStandEntityId);
            return;
        }

        // 关联投掷物
        ProjectileInfo projectile = new ProjectileInfo(armorStandEntityId, pending.position);
        bestMatch.setProjectile(projectile);
        weaponByArmorStand.put(armorStandEntityId, bestMatch);
        pendingArmorStands.remove(armorStandEntityId);

        MurderHelperMod.logger.info("[WeaponDetector] ✓ Matched projectile {} to {} (distance: {:.1f}m, item: {})",
                armorStandEntityId, bestMatch.playerName, minDistance, baseItemName);
    }


    /**
     * 定期更新
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();

        // 清理超时的待确认盔甲架
        pendingArmorStands.entrySet().removeIf(entry ->
                currentTime - entry.getValue().createTime > PENDING_TIMEOUT);

        // 检查投掷物超时
        weaponByPlayer.values().forEach(info -> {
            if (info.projectile != null) {
                if (currentTime - info.projectile.throwTime > PROJECTILE_TIMEOUT) {
                    MurderHelperMod.logger.warn("[WeaponDetector] {} projectile timeout",
                            info.playerName);
                    weaponByArmorStand.remove(info.projectile.armorStandEntityId);
                    info.setProjectile(null);
                }
            }
        });
    }

    /**
     * 清除所有数据
     */
    public void clear() {
        weaponByPlayer.clear();
        weaponByArmorStand.clear();
        entityIdToPlayerName.clear();
        pendingArmorStands.clear();
        MurderHelperMod.logger.info("[WeaponDetector] Cleared all data");
    }

    // ==================== 查询接口（通过玩家名字）====================

    /**
     * 获取玩家的武器信息
     */
    public WeaponInfo getWeaponInfo(String playerName) {
        return weaponByPlayer.get(playerName);
    }

    /**
     * 检查玩家是否持有飞刀
     */
    public boolean isHoldingKnife(String playerName) {
        WeaponInfo info = weaponByPlayer.get(playerName);
        return info != null && info.isHolding;
    }

    /**
     * 检查玩家是否有飞刀在空中
     */
    public boolean hasKnifeInAir(String playerName) {
        WeaponInfo info = weaponByPlayer.get(playerName);
        return info != null && info.projectile != null;
    }

    /**
     * 获取玩家飞刀的位置（如果在飞行中）
     */
    public Vec3 getKnifePosition(String playerName) {
        WeaponInfo info = weaponByPlayer.get(playerName);
        return info != null && info.projectile != null ? info.projectile.position : null;
    }

    /**
     * 获取所有持刀的玩家
     */
    public List<WeaponInfo> getAllKnifeHolders() {
        List<WeaponInfo> result = new ArrayList<>();
        weaponByPlayer.values().forEach(info -> {
            if (info.isHolding) {
                result.add(info);
            }
        });
        return result;
    }

    /**
     * 获取所有飞行中的飞刀
     */
    public List<WeaponInfo> getAllThrownKnives() {
        List<WeaponInfo> result = new ArrayList<>();
        weaponByPlayer.values().forEach(info -> {
            if (info.projectile != null) {
                result.add(info);
            }
        });
        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 提取物品基础名称（去除NBT部分）
     * 例如：minecraft:blaze_rod{NBT...} -> minecraft:blaze_rod
     */
    private String extractBaseItemName(String registryName) {
        if (registryName == null) {
            return null;
        }

        // 如果包含 { 说明有NBT，截取之前的部分
        int braceIndex = registryName.indexOf('{');
        if (braceIndex > 0) {
            return registryName.substring(0, braceIndex).trim();
        }

        return registryName.trim();
    }

    /**
     * 通过实体ID获取玩家实体
     */
    private EntityPlayer getPlayerByEntityId(int entityId) {
        if (MurderHelperMod.mc == null || MurderHelperMod.mc.theWorld == null) {
            return null;
        }

        Entity entity = MurderHelperMod.mc.theWorld.getEntityByID(entityId);
        return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
    }

    /**
     * 通过实体ID获取玩家名
     */
    private String getPlayerNameByEntityId(int entityId) {
        String cached = entityIdToPlayerName.get(entityId);
        if (cached != null) {
            return cached;
        }

        if (MurderHelperMod.mc == null || MurderHelperMod.mc.theWorld == null) {
            return null;
        }

        Entity entity = MurderHelperMod.mc.theWorld.getEntityByID(entityId);
        if (entity instanceof EntityPlayer) {
            String name = entity.getName();
            entityIdToPlayerName.put(entityId, name);
            return name;
        }

        return null;
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

    /**
     * 注册玩家
     */
    public void registerPlayer(String playerName, int entityId) {
        entityIdToPlayerName.put(entityId, playerName);
    }
}
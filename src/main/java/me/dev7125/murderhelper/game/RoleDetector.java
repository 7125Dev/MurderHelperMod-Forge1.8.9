package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.util.ItemClassifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色检测器
 * 负责检测玩家的角色(通过数据包驱动)
 * 现在也负责管理嫌疑人状态
 * <p>
 * 本地玩家: 监听 S2FPacketSetSlot, S30PacketWindowItems, S09PacketHeldItemChange
 * 其他玩家: 监听 S04PacketEntityEquipment
 */
public class RoleDetector {

    private final Logger logger;
    private final GameStateManager gameState;
    private final PlayerTracker playerTracker;

    // 缓存玩家上次检测的装备状态(用于检测变化)
    private Map<String, ItemStack> lastCheckedItemCache = new HashMap<>();

    // 本地玩家的物品栏缓存(用于跟踪主手物品)
    private ItemStack[] localPlayerInventory = new ItemStack[45];
    private int localPlayerHeldSlot = 0; // 当前手持槽位(0-8)

    public RoleDetector(Logger logger, GameStateManager gameState, PlayerTracker playerTracker) {
        this.logger = logger;
        this.gameState = gameState;
        this.playerTracker = playerTracker;
    }

    // ==================== 嫌疑人管理 ====================

    /**
     * 标记玩家为嫌疑人
     * 由SuspectTracker调用
     *
     * @param playerName 玩家名字
     */
    public void markAsSuspect(String playerName) {
        MurderHelperMod.PlayerRole currentRole = playerTracker.getPlayerRole(playerName);

        // 只有INNOCENT和SHOOTER可以成为嫌疑人
        if (currentRole != MurderHelperMod.PlayerRole.INNOCENT &&
                currentRole != MurderHelperMod.PlayerRole.SHOOTER) {
            return;
        }

        // 更新角色为SUSPECT
        playerTracker.updatePlayerRole(playerName, MurderHelperMod.PlayerRole.SUSPECT);
        logger.info("Player {} role changed to SUSPECT", playerName);
    }

    /**
     * 检查玩家是否是嫌疑人
     *
     * @param player 玩家实体
     * @return 是否是嫌疑人
     */
    public boolean isSuspect(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        MurderHelperMod.PlayerRole myRole = MurderHelperMod.gameState.getMyRole();
        if (myRole == MurderHelperMod.PlayerRole.MURDERER) {
            return false;
        }

        MurderHelperMod.PlayerRole role = playerTracker.getPlayerRole(player.getName());
        return role == MurderHelperMod.PlayerRole.SUSPECT;
    }

    // ==================== 本地玩家角色检测(数据包驱动) ====================

    /**
     * 处理槽位设置数据包(S2FPacketSetSlot)
     * 用于检测本地玩家的主手物品变化
     */
    public void handleSetSlot(S2FPacketSetSlot packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) return;

        int windowId = packet.func_149175_c();
        int slot = packet.func_149173_d();
        ItemStack itemStack = packet.func_149174_e();

        if (windowId == 0 && slot >= 36 && slot <= 44) {
            int hotbarSlot = slot - 36;
            localPlayerInventory[slot] = itemStack;

            // 检测当前手持槽位
            if (hotbarSlot == localPlayerHeldSlot) {
                detectMyRole(itemStack);
            }

            if (itemStack != null) {
                MurderHelperMod.PlayerRole role = checkItemForRole(itemStack);
                if ((role == MurderHelperMod.PlayerRole.MURDERER ||
                        role == MurderHelperMod.PlayerRole.DETECTIVE)) {

                    logger.warn("[RoleDetector][ME][SetSlot] CRITICAL ROLE ITEM at slot {}: {} -> {}",
                            hotbarSlot, itemStack.getDisplayName(), role);
                    detectMyRole(itemStack);
                }
            }
        }
    }

    /**
     * 处理窗口物品数据包(S30PacketWindowItems)
     * 检测本地玩家的物品栏初始化(用于角色检测)
     */
    public void handleWindowItems(S30PacketWindowItems packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) return;

        int windowId = packet.func_148911_c();
        ItemStack[] items = packet.getItemStacks();

        if (items != null) {
            localPlayerInventory = items;

            MurderHelperMod.PlayerRole detectedRole = null;
            ItemStack roleItem = null;
            int roleSlot = -1;

            for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                int inventorySlot = 36 + hotbarSlot;
                if (inventorySlot < items.length) {
                    ItemStack item = items[inventorySlot];

                    if (item != null) {
                        // 检测是否是角色物品
                        MurderHelperMod.PlayerRole role = checkItemForRole(item);
                        if (role != null && role != MurderHelperMod.PlayerRole.INNOCENT) {
                            detectedRole = role;
                            roleItem = item;
                            roleSlot = hotbarSlot;

                            // 凶手和侦探找到就立即停止
                            if (role == MurderHelperMod.PlayerRole.MURDERER ||
                                    role == MurderHelperMod.PlayerRole.DETECTIVE) {
                                break;
                            }
                        }
                    }
                }
            }

            // 更新角色
            if (detectedRole != null) {
                MurderHelperMod.PlayerRole currentRole = gameState.getMyRole();
                if (detectedRole != currentRole) {
                    logger.info("[RoleDetector] My role changed: {} -> {}", currentRole, detectedRole);
                    gameState.setMyRole(detectedRole);

                    if (roleItem != null) {
                        onRoleChanged(MurderHelperMod.mc.thePlayer.getName(), detectedRole, roleItem);
                    }
                }
            }
        }
    }

    /**
     * 检查单个物品是否是角色物品
     * @return 角色类型,如果不是角色物品返回null
     */
    private MurderHelperMod.PlayerRole checkItemForRole(ItemStack item) {
        if (item == null) return null;

        // 检测凶器(最高优先级)
        if (ItemClassifier.isMurderWeapon(item)) {
            return MurderHelperMod.PlayerRole.MURDERER;
        }

        // 检测弓箭
        ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(item);
        switch (bowCategory) {
            case DETECTIVE_BOW:
                return MurderHelperMod.PlayerRole.DETECTIVE;
            case KALI_BOW:
            case NORMAL_BOW:
                // 普通弓不在WindowItems时判定为SHOOTER
                // 因为可能是无辜者捡到金锭后获得的弓
                return MurderHelperMod.PlayerRole.SHOOTER;
            default:
                return null;
        }
    }

    /**
     * 处理手持物品切换数据包(S09PacketHeldItemChange)
     * 用于检测本地玩家切换手持槽位
     */
    public void handleHeldItemChange(S09PacketHeldItemChange packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) return;

        int newHeldSlot = packet.getHeldItemHotbarIndex();
        localPlayerHeldSlot = newHeldSlot;

        int inventorySlot = 36 + newHeldSlot;
        if (inventorySlot < localPlayerInventory.length) {
            detectMyRole(localPlayerInventory[inventorySlot]);
        }
    }

    // ==================== 其他玩家角色检测(数据包驱动) ====================

    /**
     * 处理实体装备数据包(S04PacketEntityEquipment)
     * 用于检测其他玩家的角色变化
     */
    public void handleEntityEquipment(S04PacketEntityEquipment packet) {
        if (!MurderHelperMod.isGameActuallyStarted()) return;

        if (packet.getEquipmentSlot() != 0) return;

        EntityPlayer player = getPlayerByEntityId(packet.getEntityID());
        if (player == null || isLocalPlayer(player)) return;

        checkPlayerEquipment(player.getName(), packet.getItemStack());
    }

    // ==================== 角色检测核心逻辑 ====================

    /**
     * 检测本地玩家的角色(通过主手装备)
     *
     * @param equipmentItem 主手装备物品
     */
    private void detectMyRole(ItemStack equipmentItem) {
        MurderHelperMod.PlayerRole currentRole = gameState.getMyRole();

        if (currentRole == MurderHelperMod.PlayerRole.DETECTIVE ||
                currentRole == MurderHelperMod.PlayerRole.MURDERER) {
            return;
        }

        boolean isMurderWeapon = ItemClassifier.isMurderWeapon(equipmentItem);

        MurderHelperMod.PlayerRole newRole;

        if (isMurderWeapon) {
            newRole = MurderHelperMod.PlayerRole.MURDERER;
        } else {
            ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(equipmentItem);

            switch (bowCategory) {
                case DETECTIVE_BOW:
                    newRole = MurderHelperMod.PlayerRole.DETECTIVE;
                    break;

                case NORMAL_BOW:
                case KALI_BOW:
                    newRole = currentRole == MurderHelperMod.PlayerRole.INNOCENT
                            ? MurderHelperMod.PlayerRole.SHOOTER
                            : currentRole;
                    break;

                case NONE:
                default:
                    newRole = MurderHelperMod.PlayerRole.INNOCENT;
                    break;
            }
        }

        if (newRole != currentRole) {
            gameState.setMyRole(newRole);
        }
    }


    /**
     * 检测其他玩家的装备并更新角色
     *
     * @param playerName 玩家名字
     * @param equipment  装备物品
     * @return 是否检测到角色变化
     */
    private boolean checkPlayerEquipment(String playerName, ItemStack equipment) {
        ItemStack last = lastCheckedItemCache.get(playerName);

        if (areItemStacksEqual(equipment, last)) {
            return false;
        }

        lastCheckedItemCache.put(playerName, equipment);

        if (equipment != null) {
            updatePlayerRole(playerName, equipment);
            return true;
        }
        return false;
    }

    /**
     * 根据装备物品更新玩家角色
     * 核心逻辑:
     * 1. 凶手锁定优先级最高 - 一旦锁定为凶手,永远是凶手
     * 2. 侦探锁定次之 - 如果未被锁定为凶手,锁定为侦探后永远是侦探
     * 3. 凶器检测优先级高于弓箭 - 先检测凶器再检测弓箭
     * 4. 嫌疑人状态可以被更高级别的角色覆盖
     *
     * @param playerName 玩家名字
     * @param equipment  装备物品
     */
    private void updatePlayerRole(String playerName, ItemStack equipment) {
        // 【最高优先级】如果已经被锁定为凶手,无论后续持有什么物品都是凶手
        // 即使凶手获得了弓箭,也不会改变其凶手身份
        if (playerTracker.isMurdererLocked(playerName)) {
            // 确保角色映射中仍然是凶手
            if (playerTracker.getPlayerRole(playerName) != MurderHelperMod.PlayerRole.MURDERER) {
                playerTracker.updatePlayerRole(playerName, MurderHelperMod.PlayerRole.MURDERER);
                logger.info("Player " + playerName + " remains MURDERER (locked)");
            }
            return;
        }

        // 【次优先级】如果已经被锁定为侦探,无论后续持有什么物品都是侦探
        if (playerTracker.isDetectiveLocked(playerName)) {
            // 确保角色映射中仍然是侦探
            if (playerTracker.getPlayerRole(playerName) != MurderHelperMod.PlayerRole.DETECTIVE) {
                playerTracker.updatePlayerRole(playerName, MurderHelperMod.PlayerRole.DETECTIVE);
                logger.info("Player " + playerName + " remains DETECTIVE (locked)");
            }
            return;
        }

        // 【角色判断】按优先级判断角色:凶器 > 弓箭 > 其他
        MurderHelperMod.PlayerRole newRole = determinePlayerRoleWithPriority(equipment);

        // 如果无法确定角色,保持当前状态
        if (newRole == null) {
            return;
        }

        MurderHelperMod.PlayerRole oldRole = playerTracker.getPlayerRole(playerName);

        // 【凶手锁定】如果检测到凶器,立即锁定为凶手(最高优先级)
        if (newRole == MurderHelperMod.PlayerRole.MURDERER) {
            playerTracker.lockMurderer(playerName);
            playerTracker.recordPlayerWeapon(playerName, equipment);
            logger.info("Player " + playerName + " LOCKED as MURDERER!");
        }

        // 【侦探锁定】如果检测到侦探弓且未被锁定为凶手,锁定为侦探
        if (newRole == MurderHelperMod.PlayerRole.DETECTIVE) {
            playerTracker.lockDetective(playerName);
            playerTracker.recordPlayerWeapon(playerName, equipment);
            logger.info("Player " + playerName + " LOCKED as DETECTIVE!");
        }

        // 记录持弓者的物品
        if (playerTracker.getPlayerRole(playerName) == MurderHelperMod.PlayerRole.SHOOTER) {
            playerTracker.recordPlayerWeapon(playerName, equipment);
        }

        // 【嫌疑人覆盖】如果当前是嫌疑人,新角色可以覆盖(除了INNOCENT)
        // 例如:嫌疑人拿到弓 -> 变成射手
        if (oldRole == MurderHelperMod.PlayerRole.SUSPECT &&
                newRole != MurderHelperMod.PlayerRole.INNOCENT) {
            playerTracker.updatePlayerRole(playerName, newRole);
            logger.info("Player " + playerName + " role changed from SUSPECT to: " + newRole);
            onRoleChanged(playerName, newRole, equipment);
            return;
        }

        if (newRole != oldRole) {
            playerTracker.updatePlayerRole(playerName, newRole);
            logger.info("Player " + playerName + " role changed to: " + newRole);

            // 触发角色变化回调(用于自动喊话)
            onRoleChanged(playerName, newRole, equipment);
        }
    }


    /**
     * 按优先级判断角色
     * 优先级:凶器 > 侦探弓 > 普通弓/Kali弓 > 其他
     * <p>
     * 注意:此方法用于判断其他玩家的角色
     * 重要:此方法被调用时,玩家已确保未被锁定为侦探或凶手
     * (锁定检查在 updatePlayerRole 开头已完成)
     * <p>
     * - 凶器:凶手(会被锁定)
     * - 侦探弓:侦探(会被锁定)
     * - 普通弓/Kali弓:射手(无辜者拾取金锭或Kali祝福获得,或已经是射手)
     * - 其他:无法判断,返回 null
     */
    private MurderHelperMod.PlayerRole determinePlayerRoleWithPriority(ItemStack item) {
        if (item == null) {
            return null;
        }

        // 第一优先级:检测凶器
        if (ItemClassifier.isMurderWeapon(item)) {
            return MurderHelperMod.PlayerRole.MURDERER;
        }

        // 第二优先级:检测弓箭类型
        ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(item);

        switch (bowCategory) {
            case DETECTIVE_BOW:
                // 侦探弓:判定为侦探(会被锁定)
                return MurderHelperMod.PlayerRole.DETECTIVE;

            case KALI_BOW:
                // Kali弓(无限箭):持有即为射手
                return MurderHelperMod.PlayerRole.SHOOTER;

            case NORMAL_BOW:
                // 普通弓:不做判定,完全由BowShotDetector管理
                // BowShotDetector会根据拉弓/射击动作来切换SHOOTER↔INNOCENT
                return null;

            case NONE:
            default:
                // 不是弓也不是凶器,无法判断角色
                return null;
        }
    }

    /**
     * 比较两个 ItemStack 是否相同
     */
    private boolean areItemStacksEqual(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null && stack2 == null) return true;
        if (stack1 == null || stack2 == null) return false;
        return stack1.getItem() == stack2.getItem()
                && stack1.getDisplayName().equals(stack2.getDisplayName());
    }

    /**
     * 强制检测指定玩家的角色(忽略缓存)
     * 注意:此方法用于特殊情况,通常应该依赖数据包驱动
     *
     * @param player 玩家实体
     */
    public void forceCheckPlayer(EntityPlayer player) {
        if (player == null) return;

        String playerName = player.getName();
        lastCheckedItemCache.remove(playerName);

        ItemStack heldItem = player.getHeldItem();
        if (heldItem != null) {
            updatePlayerRole(playerName, heldItem);
        }
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        lastCheckedItemCache.clear();
        localPlayerInventory = new ItemStack[45];
        localPlayerHeldSlot = 0;
    }

    /**
     * 重置检测器
     */
    public void reset() {
        lastCheckedItemCache.clear();
        localPlayerInventory = new ItemStack[45];
        localPlayerHeldSlot = 0;
    }

    /**
     * 角色变化时的回调(由 MurderHelperMod 注入处理逻辑)
     */
    public interface RoleChangeCallback {
        void onRoleChanged(String playerName, MurderHelperMod.PlayerRole newRole, ItemStack weapon);
    }

    private RoleChangeCallback roleChangeCallback = null;

    public void setRoleChangeCallback(RoleChangeCallback callback) {
        this.roleChangeCallback = callback;
    }

    private void onRoleChanged(String playerName, MurderHelperMod.PlayerRole newRole, ItemStack weapon) {
        if (roleChangeCallback != null) {
            roleChangeCallback.onRoleChanged(playerName, newRole, weapon);
        }
        if (MurderHelperMod.suspectTracker != null) {
            MurderHelperMod.suspectTracker.onPlayerRoleChanged(playerName, newRole);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 通过实体ID获取玩家实体
     */
    private EntityPlayer getPlayerByEntityId(int entityId) {
        if (MurderHelperMod.mc == null || MurderHelperMod.mc.theWorld == null) {
            return null;
        }

        try {
            Entity entity = MurderHelperMod.mc.theWorld.getEntityByID(entityId);
            return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
        } catch (Exception e) {
            logger.error("[RoleDetector] Error getting player by entity ID", e);
            return null;
        }
    }

    /**
     * 判断是否是本地玩家
     */
    private boolean isLocalPlayer(EntityPlayer player) {
        return player != null && MurderHelperMod.mc.thePlayer != null
                && player.getEntityId() == MurderHelperMod.mc.thePlayer.getEntityId();
    }
}
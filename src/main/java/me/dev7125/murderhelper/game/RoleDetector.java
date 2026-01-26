package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.util.ItemClassifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色检测器
 * 负责检测玩家的角色（自己和其他玩家）
 */
public class RoleDetector {

    private final Logger logger;
    private final GameStateManager gameState;
    private final PlayerTracker playerTracker;

    // 记录上次角色槽位的物品（用于检测自己的角色变化）
    private ItemStack lastRoleSlotItem = null;

    // 缓存玩家上次检测的手持状态（用于检测变化）
    private Map<String, ItemStack> lastCheckedItemCache = new HashMap<>();

    public RoleDetector(Logger logger, GameStateManager gameState, PlayerTracker playerTracker) {
        this.logger = logger;
        this.gameState = gameState;
        this.playerTracker = playerTracker;
    }

    /**
     * 检测自己的角色（通过 roleSlotIndex 槽位）
     *
     * @param localPlayer   本地玩家
     * @param roleSlotIndex 角色槽位索引
     */
    public void detectMyRole(EntityPlayer localPlayer, int roleSlotIndex) {
        ItemStack roleSlotItem = localPlayer.inventory.getStackInSlot(roleSlotIndex);

        // 只有当槽位物品发生变化时才更新角色
        if (areItemStacksEqual(roleSlotItem, lastRoleSlotItem)) {
            return;
        }

        lastRoleSlotItem = roleSlotItem;

        MurderHelperMod.PlayerRole currentRole = gameState.getMyRole();

        // 侦探和凶手角色一旦确定就不再改变（除非游戏重置）
        if (currentRole == MurderHelperMod.PlayerRole.DETECTIVE ||
                currentRole == MurderHelperMod.PlayerRole.MURDERER) {
            return;
        }

        MurderHelperMod.PlayerRole newRole;

        // 优先检测凶器（凶器和弓互斥，先检测凶器）
        if (ItemClassifier.isMurderWeapon(roleSlotItem)) {
            newRole = MurderHelperMod.PlayerRole.MURDERER;
        } else {
            // 检测弓的类型
            ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(roleSlotItem);

            switch (bowCategory) {
                case DETECTIVE_BOW:
                    // 服务器派发侦探弓，锁定为侦探
                    newRole = MurderHelperMod.PlayerRole.DETECTIVE;
                    break;

                case NORMAL_BOW:
                case KALI_BOW:
                    // 只有无辜者获得弓才升级为射手
                    // 如果已经是射手，保持射手身份
                    newRole = currentRole == MurderHelperMod.PlayerRole.INNOCENT
                            ? MurderHelperMod.PlayerRole.SHOOTER
                            : currentRole;
                    break;

                case NONE:
                default:
                    // 没有检测到角色物品，默认为无辜者
                    // （游戏开始时或槽位为空/非角色物品时）
                    newRole = MurderHelperMod.PlayerRole.INNOCENT;
                    break;
            }
        }

        // 只有角色真正改变时才更新并记录日志
        if (newRole != currentRole) {
            gameState.setMyRole(newRole);
            logger.info("My role changed to: " + newRole);
        }
    }

    /**
     * 检测玩家手持物品并更新角色
     *
     * @param player 玩家实体
     * @return 是否检测到角色变化
     */
    public boolean checkPlayerHeldItem(EntityPlayer player) {
        ItemStack currentHeld = player.getHeldItem();
        String playerName = player.getName();

        ItemStack lastChecked = lastCheckedItemCache.get(playerName);

        if (areItemStacksEqual(currentHeld, lastChecked)) {
            return false;
        }

        lastCheckedItemCache.put(playerName, currentHeld);

        if (currentHeld != null) {
            updatePlayerRole(playerName, currentHeld);
            return true;
        }

        return false;
    }

    /**
     * 根据当前手持物品更新玩家角色
     * 核心逻辑：
     * 1. 凶手锁定优先级最高 - 一旦锁定为凶手，永远是凶手
     * 2. 侦探锁定次之 - 如果未被锁定为凶手，锁定为侦探后永远是侦探
     * 3. 凶器检测优先级高于弓箭 - 先检测凶器再检测弓箭
     *
     * @param playerName 玩家名字
     * @param heldItem   手持物品
     */
    private void updatePlayerRole(String playerName, ItemStack heldItem) {
        // 【最高优先级】如果已经被锁定为凶手，无论后续持有什么物品都是凶手
        // 即使凶手获得了弓箭，也不会改变其凶手身份
        if (playerTracker.isMurdererLocked(playerName)) {
            // 确保角色映射中仍然是凶手
            if (playerTracker.getPlayerRole(playerName) != MurderHelperMod.PlayerRole.MURDERER) {
                playerTracker.updatePlayerRole(playerName, MurderHelperMod.PlayerRole.MURDERER);
                logger.info("Player " + playerName + " remains MURDERER (locked)");
            }
            return;
        }

        // 【次优先级】如果已经被锁定为侦探，无论后续持有什么物品都是侦探
        if (playerTracker.isDetectiveLocked(playerName)) {
            // 确保角色映射中仍然是侦探
            if (playerTracker.getPlayerRole(playerName) != MurderHelperMod.PlayerRole.DETECTIVE) {
                playerTracker.updatePlayerRole(playerName, MurderHelperMod.PlayerRole.DETECTIVE);
                logger.info("Player " + playerName + " remains DETECTIVE (locked)");
            }
            return;
        }

        // 【角色判断】按优先级判断角色：凶器 > 弓箭 > 其他
        MurderHelperMod.PlayerRole newRole = determinePlayerRoleWithPriority(heldItem);

        // 如果无法确定角色，保持当前状态
        if (newRole == null) {
            return;
        }

        MurderHelperMod.PlayerRole oldRole = playerTracker.getPlayerRole(playerName);

        // 【凶手锁定】如果检测到凶器，立即锁定为凶手（最高优先级）
        if (newRole == MurderHelperMod.PlayerRole.MURDERER) {
            playerTracker.lockMurderer(playerName);
            playerTracker.recordPlayerWeapon(playerName, heldItem);
            logger.info("Player " + playerName + " LOCKED as MURDERER!");
        }

        // 【侦探锁定】如果检测到侦探弓且未被锁定为凶手，锁定为侦探
        if (newRole == MurderHelperMod.PlayerRole.DETECTIVE) {
            playerTracker.lockDetective(playerName);
            playerTracker.recordPlayerWeapon(playerName, heldItem);
            logger.info("Player " + playerName + " LOCKED as DETECTIVE!");
        }

        if (newRole != oldRole) {
            playerTracker.updatePlayerRole(playerName, newRole);
            logger.info("Player " + playerName + " role changed to: " + newRole);

            // 触发角色变化回调（用于自动喊话）
            onRoleChanged(playerName, newRole, heldItem);
        }
    }


    /**
     * 按优先级判断角色
     * 优先级：凶器 > 侦探弓 > 普通弓/Kali弓 > 其他
     *
     * 注意：此方法用于判断其他玩家的角色
     * 重要：此方法被调用时，玩家已确保未被锁定为侦探或凶手
     * （锁定检查在 updatePlayerRole 开头已完成）
     *
     * - 凶器：凶手（会被锁定）
     * - 侦探弓：侦探（会被锁定）
     * - 普通弓/Kali弓：射手（无辜者拾取金锭或Kali祝福获得，或已经是射手）
     * - 其他：无法判断，返回 null
     */
    private MurderHelperMod.PlayerRole determinePlayerRoleWithPriority(ItemStack item) {
        if (item == null) {
            return null;
        }

        // 第一优先级：检测凶器
        if (ItemClassifier.isMurderWeapon(item)) {
            return MurderHelperMod.PlayerRole.MURDERER;
        }

        // 第二优先级：检测弓箭类型
        ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(item);

        switch (bowCategory) {
            case DETECTIVE_BOW:
                // 侦探弓：判定为侦探（会被锁定）
                return MurderHelperMod.PlayerRole.DETECTIVE;

            case NORMAL_BOW:
            case KALI_BOW:
                // 普通弓/Kali弓：判定为射手
                // 执行到这里时玩家肯定未被锁定为侦探（已在updatePlayerRole开头拦截）
                // 所以只可能是：无辜者获得弓 → 射手，或者已经是射手
                return MurderHelperMod.PlayerRole.SHOOTER;

            case NONE:
            default:
                // 不是弓也不是凶器，无法判断角色
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
     * 强制检测指定玩家的角色（忽略缓存）
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
        lastRoleSlotItem = null;
    }

    /**
     * 重置检测器
     */
    public void reset() {
        lastRoleSlotItem = null;
        lastCheckedItemCache.clear();
    }

    /**
     * 角色变化时的回调（由 MurderHelperMod 注入处理逻辑）
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
}
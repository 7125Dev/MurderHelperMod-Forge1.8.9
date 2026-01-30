package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家追踪器
 * 负责追踪和管理所有玩家的信息（角色、武器、敌对关系等）
 */
public class PlayerTracker {

    // 玩家角色映射
    private Map<String, MurderHelperMod.PlayerRole> playerRoles = new HashMap<>();

    // 旧的玩家角色，用于凶手被锁定后恢复其他玩家之前的角色
    private Map<String, MurderHelperMod.PlayerRole> oldPlayerRoles = new HashMap<>();

    // 已锁定的凶手列表（一旦确认为凶手，整场游戏都是凶手）
    private Set<String> lockedMurderers = new HashSet<>();

    // 已锁定的侦探列表（一旦确认为侦探，整场游戏都是侦探）
    private Set<String> lockedDetectives = new HashSet<>();

    // 玩家武器记录
    private Map<String, ItemStack> playerWeapons = new HashMap<>();

    /**
     * 更新玩家角色
     * @param playerName 玩家名字
     * @param role 新角色
     */
    public void updatePlayerRole(String playerName, MurderHelperMod.PlayerRole role) {
        if (role == null) {
            return;
        }

        // 如果要设置为嫌疑人，先记录当前角色（用于凶手锁定后回滚）
        if (role == MurderHelperMod.PlayerRole.SUSPECT) {
            if (!oldPlayerRoles.containsKey(playerName)) {
                MurderHelperMod.PlayerRole currentRole = playerRoles.getOrDefault(playerName, MurderHelperMod.PlayerRole.INNOCENT);
                oldPlayerRoles.put(playerName, currentRole);
            }
        } else if (!oldPlayerRoles.containsKey(playerName)) {
            oldPlayerRoles.put(playerName, role);
        }

        playerRoles.put(playerName, role);
    }

    /**
     * 锁定玩家为凶手（一旦锁定，整场游戏都是凶手）
     * @param playerName 玩家名字
     */
    public void lockMurderer(String playerName) {
        lockedMurderers.add(playerName);
        playerRoles.put(playerName, MurderHelperMod.PlayerRole.MURDERER);
    }

    /**
     * 锁定玩家为侦探（一旦锁定，整场游戏都是侦探）
     * @param playerName 玩家名字
     */
    public void lockDetective(String playerName) {
        lockedDetectives.add(playerName);
        playerRoles.put(playerName, MurderHelperMod.PlayerRole.DETECTIVE);
    }

    /**
     * 检查玩家是否被锁定为凶手
     * @param playerName 玩家名字
     * @return 是否被锁定为凶手
     */
    public boolean isMurdererLocked(String playerName) {
        return lockedMurderers.contains(playerName);
    }

    /**
     * 检查玩家是否被锁定为侦探
     * @param playerName 玩家名字
     * @return 是否被锁定为侦探
     */
    public boolean isDetectiveLocked(String playerName) {
        return lockedDetectives.contains(playerName);
    }

    /**
     * 记录玩家的武器
     * @param playerName 玩家名字
     * @param weapon 武器物品
     */
    public void recordPlayerWeapon(String playerName, ItemStack weapon) {
        if (weapon != null) {
            playerWeapons.put(playerName, weapon.copy());
        }
    }

    /**
     * 获取玩家的角色
     * @param playerName 玩家名字
     * @return 玩家角色，如果未知则返回 INNOCENT
     */
    public MurderHelperMod.PlayerRole getPlayerRole(String playerName) {
        return playerRoles.getOrDefault(playerName, MurderHelperMod.PlayerRole.INNOCENT);
    }

    /**
     * 获取玩家的角色（通过实体）
     * @param player 玩家实体
     * @return 玩家角色，如果未知则返回 INNOCENT
     */
    public MurderHelperMod.PlayerRole getPlayerRole(EntityPlayer player) {
        if (player == null) {
            return MurderHelperMod.PlayerRole.INNOCENT;
        }
        return getPlayerRole(player.getName());
    }

    /**
     * 获取玩家记录的武器（优先返回记录的武器，如果没有则返回当前手持）
     * @param player 玩家实体
     * @return 玩家武器
     */
    public ItemStack getPlayerWeapon(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        String playerName = player.getName();

        // 优先使用记录的武器
        if (playerWeapons.containsKey(playerName)) {
            return playerWeapons.get(playerName);
        }

        // 如果没有记录，返回当前手持物品
        return player.getHeldItem();
    }

    /**
     * 判断某个玩家是否是敌人
     * @param playerName 要判断的玩家名字
     * @param myRole 自己的角色
     * @return 是否是敌人
     */
    public boolean isEnemy(String playerName, MurderHelperMod.PlayerRole myRole) {
        MurderHelperMod.PlayerRole theirRole = getPlayerRole(playerName);
        if (theirRole == null) {
            return false;
        }

        // 如果我是平民或侦探或者射手 → 杀手是敌人
        if (myRole.equals(MurderHelperMod.PlayerRole.INNOCENT) || myRole.equals(MurderHelperMod.PlayerRole.DETECTIVE) || myRole.equals(MurderHelperMod.PlayerRole.SHOOTER)) {
            return theirRole == MurderHelperMod.PlayerRole.MURDERER;
        }

        // 如果我是杀手 → 侦探或者射手是敌人
        if (myRole.equals(MurderHelperMod.PlayerRole.MURDERER)) {
            return theirRole == MurderHelperMod.PlayerRole.DETECTIVE || theirRole == MurderHelperMod.PlayerRole.SHOOTER;
        }

        return false;
    }

    /**
     * 获取所有玩家的角色映射（只读）
     * @return 玩家角色映射的副本
     */
    public Map<String, MurderHelperMod.PlayerRole> getAllPlayerRoles() {
        return new HashMap<>(playerRoles);
    }

    /**
     * 获取所有被锁定的凶手（只读）
     * @return 锁定凶手列表的副本
     */
    public Set<String> getLockedMurderers() {
        return new HashSet<>(lockedMurderers);
    }

    /**
     * 获取所有被锁定的侦探（只读）
     * @return 锁定侦探列表的副本
     */
    public Set<String> getLockedDetectives() {
        return new HashSet<>(lockedDetectives);
    }

    /**
     * 重置所有追踪数据（游戏结束或切换世界时调用）
     */
    public void reset() {
        playerRoles.clear();
        oldPlayerRoles.clear();
        lockedMurderers.clear();
        lockedDetectives.clear();
        playerWeapons.clear();
    }

    /**
     * 获取当前追踪的玩家数量
     * @return 玩家数量
     */
    public int getTrackedPlayerCount() {
        return playerRoles.size();
    }

    /**
     * 获取当前锁定的凶手数量
     * @return 凶手数量
     */
    public int getLockedMurdererCount() {
        return lockedMurderers.size();
    }

    /**
     * 获取当前锁定的侦探数量
     * @return 侦探数量
     */
    public int getLockedDetectiveCount() {
        return lockedDetectives.size();
    }

    public void rollbackPlayerRole() {
        oldPlayerRoles.forEach((playerName, role) -> {
            if (!lockedMurderers.contains(playerName)) {
                playerRoles.put(playerName, role);
            }
        });
        MurderHelperMod.logger.info("rollbackPlayerRole====== " + playerRoles);
    }
}
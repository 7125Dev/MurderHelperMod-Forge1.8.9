package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 嫌疑人追踪器
 * 负责根据尸体位置和玩家位置判定嫌疑人
 */
public class SuspectTracker {

    private final Logger logger;
    private final PlayerTracker playerTracker;
    private final CorpseDetector corpseDetector;

    // 嫌疑人集合（玩家名 -> 成为嫌疑人的时间戳）
    private final Map<String, Long> suspects = new HashMap<>();

    // 嫌疑人判定配置
    private static final double SUSPECT_RANGE = 30.0;      // 尸体周围10格
    private static final long SUSPECT_TIME_WINDOW = 5000;  // 5秒内

    public SuspectTracker(Logger logger, PlayerTracker playerTracker, CorpseDetector corpseDetector) {
        this.logger = logger;
        this.playerTracker = playerTracker;
        this.corpseDetector = corpseDetector;
    }

    /**
     * 更新嫌疑人列表（每tick调用）
     * 检查所有玩家是否在最近的尸体附近
     */
    public void updateSuspects() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        MurderHelperMod.PlayerRole myRole = MurderHelperMod.gameState.getMyRole();
        if (myRole == MurderHelperMod.PlayerRole.MURDERER) {
            if (!suspects.isEmpty()) {
                suspects.clear();
            }
            return;
        }

        if (playerTracker.getLockedMurdererCount() > 0) {
            if (!suspects.isEmpty()) {
                logger.info("Murderer already locked, clearing all suspects.");
                suspects.clear();
            }
            return;
        }

        Collection<CorpseDetector.CorpseInfo> recentCorpses =
                corpseDetector.getRecentCorpses(SUSPECT_TIME_WINDOW);

        if (recentCorpses.isEmpty()) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null) {
                continue;
            }

            String playerName = player.getName();

            // 跳过自己
            if (playerName.equals(mc.thePlayer.getName())) {
                continue;
            }

            // 跳过死亡或隐形的玩家实体
            if (player.isDead || player.isInvisible()) {
                continue;
            }

            if (!canBeSuspect(playerName)) {
                continue;
            }

            Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);

            for (CorpseDetector.CorpseInfo corpse : recentCorpses) {
                double distance = playerPos.distanceTo(corpse.position);

                if (distance <= SUSPECT_RANGE) {
                    addSuspect(playerName, corpse);
                    break;
                }
            }
        }
    }

    /**
     * 判断玩家是否可以成为嫌疑人
     * 只有 INNOCENT 和 SHOOTER 可以成为嫌疑人
     */
    private boolean canBeSuspect(String playerName) {
        MurderHelperMod.PlayerRole role = playerTracker.getPlayerRole(playerName);

        // 侦探不能是嫌疑人
        if (role == MurderHelperMod.PlayerRole.DETECTIVE) {
            return false;
        }

        // 已知的凶手不再是"嫌疑人"（已经确认了）
        if (role == MurderHelperMod.PlayerRole.MURDERER) {
            return false;
        }

        // 只有 INNOCENT 和 SHOOTER 可以成为嫌疑人
        return role == MurderHelperMod.PlayerRole.INNOCENT ||
                role == MurderHelperMod.PlayerRole.SHOOTER;
    }

    /**
     * 添加嫌疑人
     */
    private void addSuspect(String playerName, CorpseDetector.CorpseInfo corpse) {
        if (!suspects.containsKey(playerName)) {
            suspects.put(playerName, System.currentTimeMillis());

            logger.info("Player {} marked as SUSPECT (near corpse at {}, {}, {})",
                    playerName,
                    corpse.position.xCoord,
                    corpse.position.yCoord,
                    corpse.position.zCoord);
        }
    }

    /**
     * 检查玩家是否是嫌疑人
     */
    public boolean isSuspect(String playerName) {
        // 如果本地玩家是杀手，不显示任何嫌疑人
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            MurderHelperMod.PlayerRole myRole = MurderHelperMod.gameState.getMyRole();
            if (myRole == MurderHelperMod.PlayerRole.MURDERER) {
                return false;
            }
        }

        return suspects.containsKey(playerName);
    }

    /**
     * 检查玩家实体是否是嫌疑人
     */
    public boolean isSuspect(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        MurderHelperMod.PlayerRole myRole = MurderHelperMod.gameState.getMyRole();
        if (myRole == MurderHelperMod.PlayerRole.MURDERER) {
            return false;
        }

        return suspects.containsKey(player.getName());
    }

    /**
     * 获取所有嫌疑人
     */
    public Set<String> getAllSuspects() {
        return new HashSet<>(suspects.keySet());
    }

    /**
     * 当找到凶手时，清除所有嫌疑人
     */
    public void onMurdererFound(String murdererName) {
        if (!suspects.isEmpty()) {
            logger.info("Murderer found: {}. Clearing all suspects.", murdererName);
            suspects.clear();
        }
    }

    /**
     * 监听玩家角色变化
     * 如果某个嫌疑人被确认为凶手，清除所有嫌疑人
     */
    public void onPlayerRoleChanged(String playerName, MurderHelperMod.PlayerRole newRole) {
        if (newRole == MurderHelperMod.PlayerRole.MURDERER) {
            onMurdererFound(playerName);
        }

        // 如果角色变为侦探，从嫌疑人列表移除
        if (newRole == MurderHelperMod.PlayerRole.DETECTIVE) {
            if (suspects.remove(playerName) != null) {
                logger.info("Player {} removed from suspects (now detective)", playerName);
            }
        }
    }

    /**
     * 清理过期的嫌疑人记录（可选，当前版本保持嫌疑人状态直到游戏结束或找到凶手）
     */
    public void cleanupOldSuspects(long maxAgeMs) {
        long now = System.currentTimeMillis();
        suspects.entrySet().removeIf(entry -> now - entry.getValue() > maxAgeMs);
    }

    /**
     * 重置所有嫌疑人数据
     */
    public void reset() {
        suspects.clear();
    }

    /**
     * 获取当前嫌疑人数量
     */
    public int getSuspectCount() {
        return suspects.size();
    }
}
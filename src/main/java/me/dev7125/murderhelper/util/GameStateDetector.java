package me.dev7125.murderhelper.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 游戏状态检测器（重新设计）
 *
 * 职责：提供底层检测能力，不管理状态
 * - 检测是否在密室谋杀游戏中
 * - 检测 Tab 列表
 * - 检测游戏环境特征（地图、计分板、Title）
 *
 * 不负责：
 * - 状态转换逻辑 → GameStateManager
 * - 延迟机制 → GameStateManager
 * - 游戏流程判断 → GameStateManager
 */
public class GameStateDetector {

    private static GameStateDetector instance;
    private Logger logger;

    // ========== Title 检测相关 ==========
    private boolean hasMurderTitle = false;
    private long titleDetectTime = 0;

    // ========== 计分板检测缓存 ==========
    private long lastScoreboardCheck = 0;
    private boolean cachedScoreboardResult = false;

    // ========== 整体检测结果缓存 ==========
    private long lastFullCheck = 0;
    private boolean cachedIsInMurderMystery = false;

    // ========== Tab列表缓存 ==========
    private Map<String, Boolean> tabListCache = new HashMap<>();
    private long lastTabListUpdate = 0;

    private GameStateDetector() {}

    public static GameStateDetector getInstance() {
        if (instance == null) {
            instance = new GameStateDetector();
        }
        return instance;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // ========== 核心检测方法 ==========

    /**
     * 判断是否在密室谋杀游戏中（带缓存）
     * 检测条件：快捷栏有地图 && (计分板有murder 或 收到过murder相关的title)
     */
    public boolean isInMurderMystery() {
        long now = System.currentTimeMillis();
        if (now - lastFullCheck > GameConstants.FULL_CHECK_CACHE_MS) {
            cachedIsInMurderMystery = hasMapInHotbar() &&
                    (hasScoreboardMurderCached() || isTitleValid());
            lastFullCheck = now;
        }
        return cachedIsInMurderMystery;
    }

    /**
     * 强制立即检测（不使用缓存）
     */
    public boolean isInMurderMysteryNow() {
        return hasMapInHotbar() && (hasScoreboardMurder() || isTitleValid());
    }

    // ========== 游戏开始条件检测 ==========

    /**
     * 检测游戏开始条件
     * 返回：是否满足游戏开始条件以及原因
     */
    public static class GameStartCondition {
        public final boolean roleSlotHasItem;
        public final boolean otherPlayerHoldsItem;
        public final String otherPlayerName;

        public GameStartCondition(boolean roleSlotHasItem,
                                  boolean otherPlayerHoldsItem,
                                  String otherPlayerName) {
            this.roleSlotHasItem = roleSlotHasItem;
            this.otherPlayerHoldsItem = otherPlayerHoldsItem;
            this.otherPlayerName = otherPlayerName;
        }

        /**
         * 是否满足任意开始条件
         */
        public boolean shouldStart() {
            return roleSlotHasItem || otherPlayerHoldsItem;
        }

        /**
         * 获取原因描述
         */
        public String getReason() {
            if (roleSlotHasItem) {
                return "Role slot item detected";
            }
            if (otherPlayerHoldsItem) {
                return "Other player holding item: " + otherPlayerName;
            }
            return "No start condition met";
        }
    }

    /**
     * 检测游戏开始条件（不包含超时逻辑）
     * 超时逻辑应该由 GameStateManager 处理
     */
    public GameStartCondition checkGameStartConditions(EntityPlayer localPlayer, int roleSlotIndex) {
        if (localPlayer == null) {
            return new GameStartCondition(false, false, null);
        }

        // 条件1: 自己的角色槽位有物品
        ItemStack roleSlotItem = localPlayer.inventory.getStackInSlot(roleSlotIndex);
        if (roleSlotItem != null) {
            return new GameStartCondition(true, false, null);
        }

        // 条件2: 检测到其他玩家手持武器或弓
        List<EntityPlayer> otherPlayers = getOtherPlayersInTabList(localPlayer);
        for (EntityPlayer player : otherPlayers) {
            ItemStack heldItem = player.getHeldItem();
            if (heldItem != null) {
                return new GameStartCondition(false, true, player.getName());
            }
        }

        return new GameStartCondition(false, false, null);
    }

    // ========== 聊天消息特征检测 ==========

    /**
     * 聊天消息类型
     */
    public enum ChatMessageType {
        TELEPORTING,        // 传送消息
        GAME_ENDING,        // 游戏结束消息
        AUTO_QUEUE,         // 自动排队消息
        SEPARATOR_LINE,     // 分隔符
        NORMAL              // 普通消息
    }

    /**
     * 检测聊天消息类型
     * 只负责识别消息类型，不决定如何处理
     */
    public ChatMessageType detectChatMessageType(String message, String formattedMessage) {
        if (message == null || message.isEmpty()) {
            return ChatMessageType.NORMAL;
        }

        // 检测传送消息
        for (String keyword : GameConstants.TELEPORT_KEYWORDS) {
            if (message.contains(keyword)) {
                return ChatMessageType.TELEPORTING;
            }
        }

        // 检测分隔符
        for (String separator : GameConstants.GAME_END_SEPARATORS) {
            if (message.contains(separator) || formattedMessage.contains(separator)) {
                return ChatMessageType.SEPARATOR_LINE;
            }
        }

        // 检测游戏结束（MURDER MYSTERY 标题）
        String upperMessage = message.toUpperCase();
        if (upperMessage.contains("MURDER MYSTERY")) {
            for (String winnerKeyword : GameConstants.WINNER_KEYWORDS) {
                if (message.contains(winnerKeyword)) {
                    return ChatMessageType.GAME_ENDING;
                }
            }
        }

        // 检测自动排队
        for (String queueKeyword : GameConstants.QUEUE_KEYWORDS) {
            if (message.contains(queueKeyword)) {
                return ChatMessageType.AUTO_QUEUE;
            }
        }

        return ChatMessageType.NORMAL;
    }

    // ========== Tab列表管理 ==========

    /**
     * 检查玩家是否在Tab列表中（带缓存）
     */
    public boolean isPlayerInTabList(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastTabListUpdate > GameConstants.TAB_LIST_CACHE_MS) {
            updateTabListCache();
            lastTabListUpdate = now;
        }

        return tabListCache.getOrDefault(player.getName(), false);
    }

    /**
     * 更新Tab列表缓存
     */
    private void updateTabListCache() {
        tabListCache.clear();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return;
        }

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            tabListCache.put(info.getGameProfile().getName(), true);
        }
    }

    /**
     * 获取除了自己以外在Tab列表中的所有玩家
     */
    public List<EntityPlayer> getOtherPlayersInTabList(EntityPlayer localPlayer) {
        List<EntityPlayer> result = new ArrayList<>();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return result;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == localPlayer) {
                continue;
            }

            if (isPlayerInTabList(player)) {
                result.add(player);
            }
        }

        return result;
    }

    // ========== 地图检测 ==========

    /**
     * 检测快捷栏（1-9格）是否有地图
     */
    private boolean hasMapInHotbar() {
        EntityPlayer player = getPlayer();
        if (player == null) return false;

        for (int i = 0; i < GameConstants.HOTBAR_SLOTS; i++) {
            ItemStack item = player.inventory.getStackInSlot(i);
            if (item != null && (item.getItem() == Items.map ||
                    item.getItem() == Items.filled_map)) {
                return true;
            }
        }
        return false;
    }

    // ========== 计分板检测 ==========

    /**
     * 检测计分板是否包含 "murder"（带缓存）
     */
    private boolean hasScoreboardMurderCached() {
        long now = System.currentTimeMillis();
        if (now - lastScoreboardCheck > GameConstants.SCOREBOARD_CACHE_MS) {
            cachedScoreboardResult = hasScoreboardMurder();
            lastScoreboardCheck = now;
        }
        return cachedScoreboardResult;
    }

    /**
     * 检测计分板是否包含 "murder"
     */
    private boolean hasScoreboardMurder() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;

        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return false;

        ScoreObjective sidebar = scoreboard.getObjectiveInDisplaySlot(GameConstants.SCOREBOARD_SIDEBAR_SLOT);
        if (sidebar == null) return false;

        // 检查计分板标题
        String displayName = stripFormatting(sidebar.getDisplayName()).toLowerCase();
        if (containsMurderKeyword(displayName)) {
            return true;
        }

        // 检查计分板各行内容
        Collection<Score> scores = scoreboard.getSortedScores(sidebar);
        for (Score score : scores) {
            String playerName = score.getPlayerName();
            if (playerName != null && containsMurderKeyword(playerName.toLowerCase())) {
                return true;
            }

            ScorePlayerTeam team = scoreboard.getPlayersTeam(playerName);
            if (team != null) {
                String prefix = stripFormatting(team.getColorPrefix()).toLowerCase();
                String suffix = stripFormatting(team.getColorSuffix()).toLowerCase();
                if (containsMurderKeyword(prefix) || containsMurderKeyword(suffix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查文本是否包含密室谋杀关键词
     */
    private boolean containsMurderKeyword(String text) {
        for (String keyword : GameConstants.MURDER_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ========== Title 处理 ==========

    /**
     * 检查 Title 是否有效
     */
    private boolean isTitleValid() {
        if (!hasMurderTitle) return false;

        if (System.currentTimeMillis() - titleDetectTime > GameConstants.TITLE_TIMEOUT_MS) {
            hasMurderTitle = false;
            return false;
        }

        return true;
    }

    /**
     * 处理收到的 Title 数据包
     */
    public void onTitlePacket(S45PacketTitle packet) {
        if (packet.getType() == S45PacketTitle.Type.TITLE ||
                packet.getType() == S45PacketTitle.Type.SUBTITLE) {

            IChatComponent message = packet.getMessage();
            if (message != null) {
                String text = stripFormatting(message.getUnformattedText()).toLowerCase();

                if (containsMurderKeyword(text)) {
                    hasMurderTitle = true;
                    titleDetectTime = System.currentTimeMillis();

                    if (logger != null) {
                        logger.debug("Murder Mystery title detected: " + text);
                    }
                }
            }
        }
    }

    // ========== 工具方法 ==========

    /**
     * 获取当前玩家
     */
    private EntityPlayer getPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer;
    }

    /**
     * 去除 Minecraft 格式化代码
     */
    private String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    // ========== 重置 ==========

    /**
     * 重置所有检测状态
     */
    public void reset() {
        hasMurderTitle = false;
        titleDetectTime = 0;
        cachedScoreboardResult = false;
        lastScoreboardCheck = 0;
        cachedIsInMurderMystery = false;
        lastFullCheck = 0;
        tabListCache.clear();
        lastTabListUpdate = 0;

        if (logger != null) {
            logger.debug("GameStateDetector reset");
        }
    }

    // ========== 调试 ==========

    /**
     * 打印调试信息
     */
    public void printDebugInfo() {
        System.out.println("=== Murder Mystery Detector Debug ===");
        System.out.println("Has Map in Hotbar: " + hasMapInHotbar());
        System.out.println("Has Scoreboard Murder: " + hasScoreboardMurderCached());
        System.out.println("Has Murder Title: " + hasMurderTitle);
        System.out.println("Title Valid: " + isTitleValid());
        System.out.println("In Murder Mystery: " + isInMurderMystery());
        System.out.println("Tab List Cache Size: " + tabListCache.size());
        System.out.println("====================================");
    }
}
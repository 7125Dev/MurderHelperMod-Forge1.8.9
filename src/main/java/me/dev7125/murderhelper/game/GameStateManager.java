package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.util.GameConstants;
import me.dev7125.murderhelper.util.GameStateDetector;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.Logger;

/**
 * 游戏状态管理器（增强版）
 *
 * 职责：
 * - 管理游戏状态（开始、进行中、结束）
 * - 管理状态转换逻辑
 * - 管理延迟机制（传送、游戏结束）
 * - 使用 GameStateDetector 提供的检测能力
 */
public class GameStateManager {

    private final Logger logger;
    private final GameStateDetector detector;

    // ========== 游戏状态 ==========
    private boolean inGame = false;
    private boolean gameActuallyStarted = false;
    private int ticksSinceInGame = 0;

    // ========== 游戏结束检测 ==========
    private int gameEndDelayTicks = 0;
    private int separatorLineCount = 0;
    private long lastSeparatorTime = 0;

    // ========== 传送检测 ==========
    private boolean justTeleported = false;
    private int teleportDelayTicks = 0;

    // ========== 角色信息 ==========
    private String myRole = "Innocent";

    public GameStateManager(Logger logger) {
        this.logger = logger;
        this.detector = GameStateDetector.getInstance();
    }

    // ==================== 状态查询 ====================

    /**
     * 是否在游戏中
     */
    public boolean isInGame() {
        return inGame;
    }

    /**
     * 游戏是否真正开始（已分配角色）
     */
    public boolean isGameActuallyStarted() {
        return gameActuallyStarted;
    }

    /**
     * 获取自己的角色
     */
    public String getMyRole() {
        return myRole;
    }

    /**
     * 设置自己的角色
     */
    public void setMyRole(String role) {
        if (!myRole.equals(role)) {
            myRole = role;
            logger.info("My role changed to: " + myRole);
        }
    }

    /**
     * 获取游戏开始后的tick数
     */
    public int getTicksSinceInGame() {
        return ticksSinceInGame;
    }

    /**
     * 是否刚刚传送
     */
    public boolean isJustTeleported() {
        return justTeleported;
    }

    // ==================== 状态管理 ====================

    /**
     * 设置游戏状态
     */
    public void setInGame(boolean inGame) {
        if (this.inGame != inGame) {
            this.inGame = inGame;
            logger.info("Murder Mystery game status changed: " + inGame);

            // 如果离开游戏，重置游戏开始标志
            if (!inGame) {
                gameActuallyStarted = false;
                ticksSinceInGame = 0;
            }
        }
    }

    /**
     * 检测并更新游戏状态（是否在游戏中）
     * 使用 GameStateDetector 提供的检测能力
     */
    public void updateInGameStatus() {
        boolean detectorSaysInGame = detector.isInMurderMystery();

        // 如果游戏已经真正开始，锁定状态
        if (gameActuallyStarted) {
            if (!inGame) {
                setInGame(true);
                logger.debug("Game status locked to true (game already started)");
            }
        } else {
            // 游戏还没真正开始，跟随检测器
            if (detectorSaysInGame != inGame) {
                setInGame(detectorSaysInGame);
            }
        }
    }

    /**
     * 触发游戏真正开始
     */
    public void triggerGameStart(String reason) {
        if (!gameActuallyStarted) {
            logger.info("Game actually started! Reason: " + reason);
            gameActuallyStarted = true;
            ticksSinceInGame = 0;
        }
    }

    /**
     * 检测并触发游戏开始
     * 使用 GameStateDetector 检测开始条件，加上超时逻辑
     */
    public boolean checkAndTriggerGameStart(EntityPlayer localPlayer, int roleSlotIndex) {
        if (gameActuallyStarted) {
            return false; // 已经开始了
        }

        // 使用检测器检查游戏开始条件
        GameStateDetector.GameStartCondition condition =
                detector.checkGameStartConditions(localPlayer, roleSlotIndex);

        if (condition.shouldStart()) {
            triggerGameStart(condition.getReason());
            return true;
        }

        // 超时检测（这是状态管理逻辑，不是检测逻辑）
        if (ticksSinceInGame >= GameConstants.GAME_START_TIMEOUT_TICKS) {
            triggerGameStart("Timeout - assuming game started (I'm Innocent)");
            return true;
        }

        return false;
    }

    // ==================== 聊天消息处理 ====================

    /**
     * 处理聊天消息
     * 使用 GameStateDetector 识别消息类型，然后决定如何处理
     */
    public void processChatMessage(String message, String formattedMessage) {
        GameStateDetector.ChatMessageType type =
                detector.detectChatMessageType(message, formattedMessage);

        switch (type) {
            case TELEPORTING:
                handleTeleport();
                break;

            case SEPARATOR_LINE:
                handleSeparatorLine();
                break;

            case GAME_ENDING:
                if (inGame && gameActuallyStarted) {
                    handleGameEnd("Game summary screen");
                }
                break;

            case AUTO_QUEUE:
                if (inGame && gameActuallyStarted) {
                    handleGameEnd("Auto-queue message");
                }
                break;

            case NORMAL:
            default:
                // 普通消息，不处理
                break;
        }
    }

    /**
     * 处理传送
     */
    private void handleTeleport() {
        logger.info("Detected teleport to new game");
        justTeleported = true;
        teleportDelayTicks = GameConstants.TELEPORT_DELAY_TICKS;
    }

    /**
     * 处理分隔符（游戏结束判断）
     */
    private void handleSeparatorLine() {
        long now = System.currentTimeMillis();
        if (now - lastSeparatorTime < 2000) {
            separatorLineCount++;
            if (separatorLineCount >= 2) {
                handleGameEnd("Multiple separator lines");
                separatorLineCount = 0;
            }
        } else {
            separatorLineCount = 1;
        }
        lastSeparatorTime = now;
    }

    /**
     * 处理游戏结束
     */
    private void handleGameEnd(String reason) {
        logger.info("Game ended detected: " + reason);
        gameEndDelayTicks = GameConstants.GAME_END_DELAY_TICKS;
    }

    // ==================== Tick更新 ====================

    /**
     * 每tick更新（在ClientTickEvent中调用）
     */
    public void tick() {
        // 处理传送延迟
        if (justTeleported && teleportDelayTicks > 0) {
            teleportDelayTicks--;
            if (teleportDelayTicks == 0) {
                logger.info("Teleport delay finished");
                justTeleported = false;
            }
        }

        // 处理游戏结束延迟
        if (gameEndDelayTicks > 0) {
            gameEndDelayTicks--;
            if (gameEndDelayTicks == 0) {
                logger.info("Resetting game state after game end delay");
                gameActuallyStarted = false;
                inGame = false;
            }
        }

        // 增加tick计数
        if (inGame && !gameActuallyStarted) {
            ticksSinceInGame++;
        }
    }

    /**
     * 检查是否应该跳过tick（传送期间或游戏结束延迟期间）
     */
    public boolean shouldSkipTick() {
        return (justTeleported && teleportDelayTicks > 0) || gameEndDelayTicks > 0;
    }

    /**
     * 处理传送延迟结束后的状态更新
     */
    public void handleTeleportComplete() {
        if (justTeleported && !shouldSkipTick()) {
            // 传送完成后，强制重新检测
            boolean detectorResult = detector.isInMurderMystery();
            if (detectorResult) {
                logger.debug("Detected in game after teleport");
                setInGame(true);
            } else {
                logger.debug("Not in game after teleport, will keep checking");
            }
        }
    }

    // ==================== 重置 ====================

    /**
     * 重置所有状态（世界卸载或断开连接时调用）
     */
    public void reset() {
        inGame = false;
        gameActuallyStarted = false;
        ticksSinceInGame = 0;
        gameEndDelayTicks = 0;
        separatorLineCount = 0;
        lastSeparatorTime = 0;
        justTeleported = false;
        teleportDelayTicks = 0;
        myRole = "Innocent";
        logger.info("GameStateManager reset!");
    }
}
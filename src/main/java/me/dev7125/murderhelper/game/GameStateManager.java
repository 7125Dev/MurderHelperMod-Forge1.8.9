package me.dev7125.murderhelper.game;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.util.GameConstants;
import org.apache.logging.log4j.Logger;

/**
 * 游戏状态管理器（简化版 - 数据包驱动）
 */
public class GameStateManager {

    private final Logger logger;

    // ========== 游戏状态 ==========
    private GameState currentState = GameState.IDLE;
    private long gameStartTime = 0;
    private MurderHelperMod.PlayerRole myRole = MurderHelperMod.PlayerRole.INNOCENT;

    // ========== 角色检测延迟 ==========
    private int roleCheckDelayTicks = 0;
    private boolean roleCheckEnabled = false;

    // ========== 状态锁定机制（防止数据包抖动） ==========
    private long stateLockedUntil = 0;
    private static final long STATE_LOCK_DURATION = 3000; // 游戏开始后3秒内锁定状态

    public enum GameState {
        IDLE,           // 空闲状态（不在游戏中）
        PREPARING,      // 准备阶段（在游戏地图但未开始）
        PLAYING         // 游戏进行中
    }

    public GameStateManager(Logger logger) {
        this.logger = logger;
    }

    // ==================== 数据包事件回调 ====================

    /**
     * 游戏开始（收到 nameTagVisibility = "never"）
     */
    public void onGameStart() {
        // 防止重复触发
        if (currentState == GameState.PLAYING) {
            logger.debug("[IGNORED] Duplicate game start signal");
            return;
        }

        currentState = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        roleCheckEnabled = true;
        roleCheckDelayTicks = GameConstants.GAME_START_ROLE_CHECK_DELAY_TICKS;

        // 锁定状态，防止后续的 PREPARING 数据包干扰
        stateLockedUntil = System.currentTimeMillis() + STATE_LOCK_DURATION;

        logger.info("=== GAME STARTED ===");
    }

    /**
     * 游戏准备阶段（收到 nameTagVisibility = "always"）
     */
    public void onGamePreparing() {
        // 检查是否在状态锁定期内
        if (isStateLocked()) {
            logger.debug("[IGNORED] Game preparing signal (state locked, probably server packet spam)");
            return;
        }

        // 防止重复触发
        if (currentState == GameState.PREPARING) {
            return;
        }

        currentState = GameState.PREPARING;
        logger.info("=== GAME PREPARING ===");
    }

    /**
     * 游戏重置（收到 S01PacketJoinGame）
     */
    public void onGameReset() {
        gameStartTime = 0;
        myRole = MurderHelperMod.PlayerRole.INNOCENT;
        roleCheckEnabled = false;
        roleCheckDelayTicks = 0;
        stateLockedUntil = 0; // 清除状态锁定

        logger.info("=== GAME DATA RESET ===");
    }

    // ==================== 状态查询 ====================

    /**
     * 检查状态是否被锁定
     */
    private boolean isStateLocked() {
        return System.currentTimeMillis() < stateLockedUntil;
    }

    /**
     * 是否在游戏中（准备阶段或游戏中）
     */
    public boolean isInGame() {
        return currentState == GameState.PREPARING || currentState == GameState.PLAYING;
    }

    /**
     * 游戏是否真正开始（已分配角色）
     */
    public boolean isGameActuallyStarted() {
        return currentState == GameState.PLAYING;
    }

    /**
     * 是否应该检测角色（游戏开始后的延迟）
     */
    public boolean shouldCheckRoles() {
        return roleCheckEnabled && roleCheckDelayTicks <= 0;
    }

    /**
     * 获取当前状态
     */
    public GameState getCurrentState() {
        return currentState;
    }

    /**
     * 获取游戏运行时间（毫秒）
     */
    public long getGameDuration() {
        if (currentState != GameState.PLAYING || gameStartTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - gameStartTime;
    }

    /**
     * 获取游戏运行时间（格式化字符串）
     */
    public String getGameDurationFormatted() {
        long duration = getGameDuration();
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 获取自己的角色
     */
    public MurderHelperMod.PlayerRole getMyRole() {
        return myRole;
    }

    /**
     * 设置自己的角色
     */
    public void setMyRole(MurderHelperMod.PlayerRole role) {
        if (!myRole.equals(role)) {
            myRole = role;
            logger.info("My role: " + myRole);
        }
    }

    // ==================== Tick更新（主线程调用） ====================

    /**
     * 每tick更新
     */
    public void tick() {
        // 处理角色检测延迟
        if (roleCheckDelayTicks > 0) {
            roleCheckDelayTicks--;
            if (roleCheckDelayTicks == 0) {
                logger.debug("Role check delay finished, starting role detection");
            }
        }
    }

    // ==================== 重置 ====================

    /**
     * 完全重置（断开连接时）
     */
    public void reset() {
        onGameReset();
        logger.info("GameStateManager fully reset!");
    }

    // ==================== 调试信息 ====================

    /**
     * 获取当前状态的调试信息
     */
    public String getDebugInfo() {
        return String.format("State: %s, Duration: %s, Role: %s, Locked: %s",
                currentState,
                getGameDurationFormatted(),
                myRole,
                isStateLocked() ? "YES" : "NO"
        );
    }
}
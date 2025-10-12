package me.dev7125.murderhelper.util;

/**
 * 游戏常量配置类
 * 集中管理所有魔法数字和配置常量
 */
public class GameConstants {
    
    // ========== 检测相关常量 ==========
    
    /**
     * Title 检测超时时间（毫秒）
     * 超过此时间后，Title 检测结果失效
     */
    public static final long TITLE_TIMEOUT_MS = 10000;
    
    /**
     * 计分板检测缓存时间（毫秒）
     * 每隔此时间重新检测一次计分板
     */
    public static final long SCOREBOARD_CACHE_MS = 1000;
    
    /**
     * 完整游戏状态检测缓存时间（毫秒）
     * 每隔此时间重新执行一次完整检测
     */
    public static final long FULL_CHECK_CACHE_MS = 500;
    
    /**
     * Tab 列表缓存时间（毫秒）
     * 每隔此时间更新一次 Tab 列表缓存
     */
    public static final long TAB_LIST_CACHE_MS = 1000;
    
    // ========== 游戏状态相关常量 ==========
    
    /**
     * 游戏开始超时（ticks）
     * 如果进入游戏后此时间内未检测到角色分配，则认为游戏已开始
     */
    public static final int GAME_START_TIMEOUT_TICKS = 100; // 5秒
    
    /**
     * 传送延迟（ticks）
     * 检测到传送消息后，等待此时间再重新检测游戏状态
     */
    public static final int TELEPORT_DELAY_TICKS = 60; // 3秒
    
    /**
     * 游戏结束延迟（ticks）
     * 检测到游戏结束标志后，等待此时间再重置游戏状态
     */
    public static final int GAME_END_DELAY_TICKS = 40; // 2秒
    
    /**
     * 自动排队延迟（ticks）
     * 检测到自动排队消息后，等待此时间再重置游戏状态
     */
    public static final int AUTO_QUEUE_DELAY_TICKS = 60; // 3秒
    
    // ========== 物品栏相关常量 ==========
    
    /**
     * 快捷栏槽位数量
     */
    public static final int HOTBAR_SLOTS = 9;
    
    /**
     * 默认角色槽位索引
     * 密室谋杀游戏中，角色物品通常在第 2 个槽位（索引 1）
     */
    public static final int DEFAULT_ROLE_SLOT_INDEX = 1;
    
    // ========== 计分板相关常量 ==========
    
    /**
     * 侧边栏显示槽位ID
     */
    public static final int SCOREBOARD_SIDEBAR_SLOT = 1;
    
    // ========== 检测关键词 ==========
    
    /**
     * 密室谋杀游戏关键词
     */
    public static final String[] MURDER_KEYWORDS = {
        "murder", "innocent", "murderer", "detective", "mystery"
    };
    
    /**
     * 传送相关关键词（多语言）
     */
    public static final String[] TELEPORT_KEYWORDS = {
        "正在前往",          // 中文
        "Sending you to",   // 英文
        "Téléportation",    // 法文
        "Teleportiere"      // 德文
    };
    
    /**
     * 游戏结束分隔符
     */
    public static final String[] GAME_END_SEPARATORS = {
        "?????????",
        "━━━",
        "▬▬▬",
        "═══"
    };
    
    /**
     * 胜利相关关键词（多语言）
     */
    public static final String[] WINNER_KEYWORDS = {
        "Winner",       // 英文
        "胜利者",       // 中文
        "Vainqueur",    // 法文
        "Sieger"        // 德文
    };
    
    /**
     * 排队相关关键词
     */
    public static final String[] QUEUE_KEYWORDS = {
        "已自动排队",
        "automatically",
        "queue"
    };
    
    // ========== 调试相关常量 ==========
    
    /**
     * 是否启用详细日志
     * 建议在生产环境关闭
     */
    public static final boolean ENABLE_VERBOSE_LOGGING = false;
    
    /**
     * 调试信息打印间隔（ticks）
     */
    public static final int DEBUG_PRINT_INTERVAL_TICKS = 200; // 10秒
    
    // ========== 性能优化相关常量 ==========
    
    /**
     * 玩家遍历批次大小
     * 每次 tick 最多处理的玩家数量，避免单次处理过多玩家造成卡顿
     */
    public static final int PLAYER_PROCESS_BATCH_SIZE = 20;
    
    /**
     * 角色检测冷却时间（ticks）
     * 同一玩家两次角色检测的最小间隔
     */
    public static final int ROLE_DETECTION_COOLDOWN_TICKS = 5;
    
    // ========== Minecraft 特定常量 ==========
    
    /**
     * Minecraft 每秒 Tick 数
     */
    public static final int TICKS_PER_SECOND = 20;
    
    /**
     * 将秒转换为 ticks
     */
    public static int secondsToTicks(int seconds) {
        return seconds * TICKS_PER_SECOND;
    }
    
    /**
     * 将 ticks 转换为秒
     */
    public static double ticksToSeconds(int ticks) {
        return (double) ticks / TICKS_PER_SECOND;
    }
    
    /**
     * 将毫秒转换为 ticks
     */
    public static int millisToTicks(long millis) {
        return (int) (millis / (1000 / TICKS_PER_SECOND));
    }
    
    /**
     * 将 ticks 转换为毫秒
     */
    public static long ticksToMillis(int ticks) {
        return (long) ticks * (1000 / TICKS_PER_SECOND);
    }
    
    // ========== 私有构造函数 ==========
    
    private GameConstants() {
        // 工具类，禁止实例化
        throw new AssertionError("Cannot instantiate GameConstants");
    }
}
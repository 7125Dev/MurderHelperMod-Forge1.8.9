package me.dev7125.murderhelper;

import me.dev7125.murderhelper.command.MurderHelperCommands;
import me.dev7125.murderhelper.config.ModConfig;
import me.dev7125.murderhelper.feature.AlarmSystem;
import me.dev7125.murderhelper.feature.ShoutMessageBuilder;
import me.dev7125.murderhelper.game.GameStateManager;
import me.dev7125.murderhelper.game.PlayerTracker;
import me.dev7125.murderhelper.game.RoleDetector;
import me.dev7125.murderhelper.handler.BowDropTracker;
import me.dev7125.murderhelper.handler.HUDHandler;
import me.dev7125.murderhelper.handler.RenderHandler;
import me.dev7125.murderhelper.util.ItemClassifier;
import me.dev7125.murderhelper.util.GameStateDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = MurderHelperMod.MODID, version = MurderHelperMod.VERSION, name = MurderHelperMod.NAME,
        clientSideOnly = true, guiFactory = "me.dev7125.murderhelper.gui.ModGuiFactory")
public class MurderHelperMod {

    // ========== 模组信息 ==========
    public static final String MODID = "murderhelper";
    public static final String VERSION = "1.2";
    public static final String NAME = "MurderHelperMod";

    // ========== 玩家角色枚举 ==========
    public enum PlayerRole {
        INNOCENT,      // 普通平民
        MURDERER,      // 杀手
        DETECTIVE      // 侦探（有弓）
    }

    // ========== 核心组件 ==========
    public static ModConfig config;
    public static PlayerTracker playerTracker;
    public static GameStateManager gameState;
    public static RoleDetector roleDetector;
    public static AlarmSystem alarmSystem;
    public static Logger logger;
    public static HUDHandler hudHandler;
    public static BowDropTracker bowDropTracker;

    // ========== 游戏状态检测器（单例） ==========
    private GameStateDetector detector;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();

        // 初始化配置
        File configDir = new File(event.getModConfigurationDirectory(), MODID);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        config = new ModConfig();
        config.load(new File(configDir, "config.cfg"));

        // 初始化游戏状态检测器
        detector = GameStateDetector.getInstance();
        detector.setLogger(logger);

        // 初始化核心组件
        playerTracker = new PlayerTracker();
        gameState = new GameStateManager(logger);
        roleDetector = new RoleDetector(logger, gameState, playerTracker);
        alarmSystem = new AlarmSystem();
        bowDropTracker = new BowDropTracker();

        // 设置角色变化回调（用于自动喊话）
        roleDetector.setRoleChangeCallback(this::handleRoleChange);

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(bowDropTracker);

        // 初始化并注册HUD处理器
        hudHandler = new HUDHandler();
        MinecraftForge.EVENT_BUS.register(hudHandler);

        // 从配置文件加载HUD窗口位置
        hudHandler.getHUD().windowX = config.hudWindowX;
        hudHandler.getHUD().windowY = config.hudWindowY;
        hudHandler.getHUD().bgAlpha = config.hudBgAlpha;

        logger.info("MurderHelperMod v" + VERSION + " initialized!");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 注册命令
        ClientCommandHandler.instance.registerCommand(new MurderHelperCommands());
        logger.info("MurderHelperMod commands registered!");
    }

    // ========== 事件处理 ==========

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            clearGameData();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearGameData();
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String message = event.message.getUnformattedText();
        String formattedMessage = event.message.getFormattedText();

        // 使用 GameStateManager 处理聊天消息
        // GameStateManager 会使用 GameStateDetector 进行检测
        gameState.processChatMessage(message, formattedMessage);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 更新游戏状态
        gameState.tick();

        // 处理传送延迟
        handleTeleportDelay();

        // 如果应该跳过tick（传送期间或游戏结束延迟期间）
        if (gameState.shouldSkipTick()) {
            return;
        }

        // 更新游戏状态（是否在游戏中）
        updateGameState();

        // 如果未开启全局功能或不在游戏中，直接返回
        if (!config.globalEnabled || !gameState.isInGame() ||
                Minecraft.getMinecraft().theWorld == null) {
            return;
        }

        // 处理游戏逻辑
        processGameLogic();
    }

    // ========== 游戏逻辑处理 ==========

    /**
     * 处理传送延迟
     */
    private void handleTeleportDelay() {
        gameState.handleTeleportComplete();
    }

    /**
     * 更新游戏是否在进行中的状态
     */
    private void updateGameState() {
        // 使用 GameStateManager 的方法，内部会调用 GameStateDetector
        gameState.updateInGameStatus();

        // 如果离开游戏，清理角色检测器缓存
        if (!gameState.isInGame()) {
            roleDetector.clearCache();
            logger.debug("Left game, cleared role detector cache");
        }
    }

    /**
     * 处理游戏内逻辑
     */
    private void processGameLogic() {
        EntityPlayer localPlayer = Minecraft.getMinecraft().thePlayer;
        if (localPlayer == null) {
            return;
        }

        // 检测自己的角色
        roleDetector.detectMyRole(localPlayer, config.roleSlotIndex);

        // 检测游戏是否真正开始
        if (!gameState.isGameActuallyStarted()) {
            checkAndTriggerGameStart(localPlayer);
        }

        // 只有游戏真正开始后才监听其他玩家和警报
        if (gameState.isGameActuallyStarted()) {
            monitorOtherPlayers(localPlayer);

            // 警报检测
            if (config.murderAlarm) {
                alarmSystem.checkAndAlarm();
            }
        }
    }

    /**
     * 检测并触发游戏开始
     */
    private void checkAndTriggerGameStart(EntityPlayer localPlayer) {
        // 使用 GameStateManager 的方法，内部会调用 GameStateDetector
        boolean gameStarted = gameState.checkAndTriggerGameStart(localPlayer, config.roleSlotIndex);

        if (gameStarted) {
            // 游戏开始时，立即检查所有玩家的手持物品
            forceCheckAllPlayers(localPlayer);
        }
    }

    /**
     * 监控其他玩家的手持物品变化
     */
    private void monitorOtherPlayers(EntityPlayer localPlayer) {
        // 使用 GameStateDetector 获取Tab列表中的玩家，避免重复遍历
        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == null || player == localPlayer) {
                continue;
            }

            // 使用带缓存的Tab列表检查
            if (!detector.isPlayerInTabList(player)) {
                continue;
            }

            roleDetector.checkPlayerHeldItem(player);
        }
    }

    /**
     * 强制检查所有玩家的手持物品
     */
    private void forceCheckAllPlayers(EntityPlayer localPlayer) {
        if (localPlayer == null || Minecraft.getMinecraft().theWorld == null) {
            return;
        }

        GameStateDetector detector = GameStateDetector.getInstance();
        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == null || player == localPlayer) {
                continue;
            }

            if (!detector.isPlayerInTabList(player)) {
                continue;
            }

            // 强制检测玩家角色（忽略缓存）
            roleDetector.forceCheckPlayer(player);
        }

        logger.info("Game started, force checked all players");
    }

    /**
     * 角色变化回调（用于自动喊话）
     */
    private void handleRoleChange(String playerName, PlayerRole newRole, ItemStack weapon) {
        // 只对杀手喊话
        if (!config.shoutEnabled || !gameState.isInGame() || newRole != PlayerRole.MURDERER) {
            return;
        }

        EntityPlayer murderer = getPlayerByName(playerName);
        String message = ShoutMessageBuilder.buildMurdererShout(
                config.shoutMessage,
                playerName,
                weapon,
                murderer,
                config.replaceFrom,
                config.replaceTo,
                logger
        );

        if (!message.trim().isEmpty()) {
            Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
            logger.debug("Auto-shout message sent for murderer: " + playerName);
        }
    }

    // ========== 数据清理 ==========

    /**
     * 清理游戏数据
     */
    private void clearGameData() {
        if (detector != null) {
            detector.reset();
        }

        if (playerTracker != null) {
            playerTracker.reset();
        }

        if (gameState != null) {
            gameState.reset();
        }

        if (roleDetector != null) {
            roleDetector.reset();
        }

        if (bowDropTracker != null) {
            bowDropTracker.clear();
        }

        if (alarmSystem != null) {
            alarmSystem.reset();
        }

        logger.info("Game data cleared!");
    }

    // ========== 公共静态方法 ==========

    /**
     * 检查玩家是否在Tab列表中（向后兼容的包装方法）
     */
    public static boolean isPlayerInTabList(EntityPlayer player) {
        return GameStateDetector.getInstance().isPlayerInTabList(player);
    }

    /**
     * 判断某个玩家是否是敌人
     */
    public static boolean isEnemy(String playerName) {
        return playerTracker.isEnemy(playerName, gameState.getMyRole());
    }

    /**
     * 获取玩家的角色
     */
    public static PlayerRole getPlayerRole(EntityPlayer player) {
        return playerTracker.getPlayerRole(player);
    }

    /**
     * 游戏是否真正开始（已分配角色）
     */
    public static boolean isGameActuallyStarted() {
        return gameState.isGameActuallyStarted();
    }

    /**
     * 获取玩家记录的武器
     */
    public static ItemStack getPlayerWeapon(EntityPlayer player) {
        return playerTracker.getPlayerWeapon(player);
    }

    /**
     * 根据玩家名获取玩家实体
     */
    private EntityPlayer getPlayerByName(String playerName) {
        if (Minecraft.getMinecraft().theWorld == null) {
            return null;
        }

        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player != null && player.getName().equals(playerName)) {
                return player;
            }
        }

        return null;
    }

    /**
     * 手动启动游戏状态（用于没有地图的服务器）
     * 通过 /mh startmurder 命令调用
     */
    public static void manuallyStartGame() {
        // 设置游戏状态为活跃
        gameState.setInGame(true);
        logger.info("Game manually activated via command");

        // 如果游戏还没真正开始，触发游戏开始
        if (!gameState.isGameActuallyStarted()) {
            gameState.triggerGameStart("Manual start via command");

            // 立即检测所有玩家的角色
            EntityPlayer localPlayer = Minecraft.getMinecraft().thePlayer;
            if (localPlayer == null || Minecraft.getMinecraft().theWorld == null) {
                return;
            }

            // 检测自己的角色
            ItemStack roleSlotItem = localPlayer.inventory.getStackInSlot(config.roleSlotIndex);
            if (roleSlotItem != null) {
                String detectedRole;
                if (ItemClassifier.isBow(roleSlotItem)) {
                    detectedRole = "Detective";
                } else if (ItemClassifier.isMurderWeapon(roleSlotItem)) {
                    detectedRole = "Murderer";
                } else {
                    detectedRole = "Innocent";
                }
                gameState.setMyRole(detectedRole);
                logger.info("Manual role detection: " + detectedRole);
            }

            // 检测其他玩家
            GameStateDetector detector = GameStateDetector.getInstance();
            for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
                if (player == null || player == localPlayer) {
                    continue;
                }

                if (!detector.isPlayerInTabList(player)) {
                    continue;
                }

                roleDetector.forceCheckPlayer(player);
            }
        }
    }

    /**
     * 保存配置（便捷方法）
     */
    public static void saveConfig() {
        if (config == null) {
            return;
        }

        // 从HUD获取最新的窗口位置
        if (hudHandler != null && hudHandler.getHUD() != null) {
            config.hudWindowX = hudHandler.getHUD().windowX;
            config.hudWindowY = hudHandler.getHUD().windowY;
            config.hudBgAlpha = hudHandler.getHUD().bgAlpha;
        }

        config.save();
        logger.info("Config saved!");
    }
}
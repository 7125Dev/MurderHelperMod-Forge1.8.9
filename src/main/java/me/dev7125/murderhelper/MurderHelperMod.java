package me.dev7125.murderhelper;

import me.dev7125.murderhelper.config.ModConfig;
import me.dev7125.murderhelper.core.listener.ConnectionEventHandler;
import me.dev7125.murderhelper.core.listener.MurderMysteryGameListener;
import me.dev7125.murderhelper.core.listener.PacketListenerRegistry;
import me.dev7125.murderhelper.feature.AlarmSystem;
import me.dev7125.murderhelper.feature.ShoutMessageBuilder;
import me.dev7125.murderhelper.game.*;
import me.dev7125.murderhelper.handler.BowDropTracker;
import me.dev7125.murderhelper.handler.HUDHandler;
import me.dev7125.murderhelper.handler.RenderHandler;
import me.dev7125.murderhelper.util.GameConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Mod(modid = MurderHelperMod.MODID, version = MurderHelperMod.VERSION, name = MurderHelperMod.NAME,
        clientSideOnly = true, guiFactory = "me.dev7125.murderhelper.gui.ModGuiFactory")
public class MurderHelperMod {

    // ========== 模组信息 ==========
    public static final String MODID = "murderhelper";
    public static final String VERSION = "1.0.3";
    public static final String NAME = "MurderHelperMod";

    // ========== 玩家角色枚举 ==========
    public enum PlayerRole {
        INNOCENT,      // 普通平民
        MURDERER,      // 杀手
        DETECTIVE,      // 侦探（有弓）
        SHOOTER, //平民获得到弓箭
        SUSPECT //未定位到杀手前根据尸体附近30格推断出来的几个嫌疑人
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
    public static KnifeThrownDetector weaponDetector;
    public static BowShotDetector bowShotDetector;
    public static CorpseDetector corpseDetector;
    public static SuspectTracker suspectTracker;

    private static Map<String, Boolean> tabListCache = new HashMap<>();
    private static long lastTabListUpdate = 0;

    public static String playerName;

    public static Minecraft mc = Minecraft.getMinecraft();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        //注册监听器
        MinecraftForge.EVENT_BUS.register(new ConnectionEventHandler());

        logger = event.getModLog();

        // 初始化配置
        File configDir = new File(event.getModConfigurationDirectory(), MODID);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        config = new ModConfig();
        config.load(new File(configDir, "config.cfg"));


        // 初始化核心组件
        playerTracker = new PlayerTracker();
        gameState = new GameStateManager(logger);
        roleDetector = new RoleDetector(logger, gameState, playerTracker);
        alarmSystem = new AlarmSystem();
        bowDropTracker = new BowDropTracker();
        weaponDetector = new KnifeThrownDetector();
        bowShotDetector = new BowShotDetector();
        corpseDetector = new CorpseDetector(logger);
        suspectTracker = new SuspectTracker(logger, playerTracker, corpseDetector);


        // 设置角色变化回调（用于自动喊话）
        roleDetector.setRoleChangeCallback(this::handleRoleChange);

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RenderHandler());
        MinecraftForge.EVENT_BUS.register(bowDropTracker);

        // 初始化并注册HUD处理器
        hudHandler = new HUDHandler(weaponDetector, bowShotDetector);
        MinecraftForge.EVENT_BUS.register(hudHandler);

        // 从配置文件加载HUD窗口位置
        hudHandler.getHUD().windowX = config.hudWindowX;
        hudHandler.getHUD().windowY = config.hudWindowY;
        hudHandler.getHUD().bgAlpha = config.hudBgAlpha;

        playerName = mc.getSession().getUsername();

        logger.info("MurderHelperMod v" + VERSION + " initialized!");
    }


    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        //注册数据包监听器
        PacketListenerRegistry.register(new MurderMysteryGameListener(weaponDetector, bowShotDetector, corpseDetector,
                suspectTracker));
        logger.info("MurderMysteryGameListener registered!");
    }


    // ========== 事件处理 ==========

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearGameData();
        // 清空待处理的数据包队列
        PacketListenerRegistry.clearQueue();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) // 提高优先级，优先处理数据包
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // 在tick开始时处理数据包队列
        if (event.phase == TickEvent.Phase.START) {
            PacketListenerRegistry.processQueue();
            return;
        }

        // 在tick结束时处理游戏逻辑
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer localPlayer = mc.thePlayer;
        if (localPlayer == null) {
            return;
        }

        // 如果未开启全局功能或不在游戏中，直接返回
        if (!config.globalEnabled || !gameState.isInGame() ||
                mc.theWorld == null) {
            return;
        }

        //检测凶手武器状态
        weaponDetector.tick();

        // 更新游戏状态管理器
        gameState.tick();


        // 只有在游戏真正开始且过了延迟时间后才检测角色
        if (gameState.isGameActuallyStarted() && gameState.shouldCheckRoles()) {

            // 检测自己的角色
            roleDetector.detectMyRole(localPlayer, config.roleSlotIndex);

            // 监控其他玩家
            monitorOtherPlayers(localPlayer);

            // 嫌疑人检测
            if (suspectTracker != null) {
                suspectTracker.updateSuspects();
            }

            // 清理过期尸体（可选，每秒检查一次）
            if (mc.thePlayer.ticksExisted % 20 == 0 && corpseDetector != null) {
                corpseDetector.cleanupOldCorpses(10000); // 清理10秒前的尸体
            }

            // 警报检测
            if (config.murderAlarm) {
                alarmSystem.checkAndAlarm();
            }
        }
    }

    // ========== 游戏逻辑处理 ==========


    /**
     * 监控其他玩家的手持物品变化
     */
    private void monitorOtherPlayers(EntityPlayer localPlayer) {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == localPlayer) {
                continue;
            }

            // 使用带缓存的Tab列表检查
            if (!isPlayerInTabList(player)) {
                continue;
            }

            roleDetector.checkPlayerHeldItem(player);
        }
    }

    /**
     * 角色变化回调（用于自动喊话）
     */
    private void handleRoleChange(String playerName, PlayerRole newRole, ItemStack weapon) {
        // 只对杀手喊话
        if (!config.shoutEnabled || !gameState.isInGame() || newRole != PlayerRole.MURDERER) {
            return;
        }

        // 组队模式不喊话队友
        if (!playerTracker.isEnemy(playerName, gameState.getMyRole())) {
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
            mc.thePlayer.sendChatMessage(message);
            logger.debug("Auto-shout message sent for murderer: " + playerName);
        }
    }


    // ========== 数据清理 ==========

    /**
     * 清理游戏数据
     */
    public static void clearGameData() {

        tabListCache.clear();
        lastTabListUpdate = 0;

        if (corpseDetector != null) {
            corpseDetector.reset();
        }
        if (suspectTracker != null) {
            suspectTracker.reset();
        }

        if (weaponDetector != null) {
            weaponDetector.clear();
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
     * 检查玩家是否在Tab列表中（带缓存）
     */
    public static boolean isPlayerInTabList(EntityPlayer player) {
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
    private static void updateTabListCache() {
        tabListCache.clear();

        if (mc.getNetHandler() == null) {
            return;
        }

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            tabListCache.put(info.getGameProfile().getName(), true);
        }
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
        if (mc.theWorld == null) {
            return null;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != null && player.getName().equals(playerName)) {
                return player;
            }
        }

        return null;
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
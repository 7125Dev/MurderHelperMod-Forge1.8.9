package me.dev7125.murderhelper;

import me.dev7125.murderhelper.config.ModConfig;
import me.dev7125.murderhelper.core.listener.ConnectionEventHandler;
import me.dev7125.murderhelper.core.listener.MurderMysteryGameListener;
import me.dev7125.murderhelper.core.listener.PacketListenerRegistry;
import me.dev7125.murderhelper.feature.AlarmSystem;
import me.dev7125.murderhelper.feature.ShoutMessageBuilder;
import me.dev7125.murderhelper.game.*;
import me.dev7125.murderhelper.handler.BowDropRenderHandler;
import me.dev7125.murderhelper.handler.HUDRenderHandler;
import me.dev7125.murderhelper.handler.NameTagsRenderHandler;
import net.minecraft.client.Minecraft;
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

@Mod(modid = MurderHelperMod.MODID, version = MurderHelperMod.VERSION, name = MurderHelperMod.NAME,
        clientSideOnly = true, guiFactory = "me.dev7125.murderhelper.gui.ModGuiFactory")
public class MurderHelperMod {

    // ========== 模组信息 ==========
    public static final String MODID = "murderhelper";
    public static final String VERSION = "1.0.4";
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
    public static HUDRenderHandler hudHandler;
    public static BowDropRenderHandler bowDropRenderHandler;
    public static KnifeThrownDetector weaponDetector;
    public static BowShotDetector bowShotDetector;
    public static CorpseDetector corpseDetector;
    public static SuspectTracker suspectTracker;
    public static BowDropDetector bowDropDetector;

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
        bowDropRenderHandler = new BowDropRenderHandler();
        weaponDetector = new KnifeThrownDetector();
        bowShotDetector = new BowShotDetector(playerTracker);
        corpseDetector = new CorpseDetector(logger);
        suspectTracker = new SuspectTracker(logger, playerTracker, corpseDetector);
        bowDropDetector = new BowDropDetector();


        // 设置角色变化回调（用于自动喊话）
        roleDetector.setRoleChangeCallback(this::handleRoleChange);

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new NameTagsRenderHandler());
        MinecraftForge.EVENT_BUS.register(bowDropRenderHandler);

        // 初始化并注册HUD处理器
        hudHandler = new HUDRenderHandler(weaponDetector, bowShotDetector);
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
                suspectTracker, roleDetector, bowDropDetector));
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
        bowShotDetector.update();

        // 更新游戏状态管理器
        gameState.tick();


        // 只有在游戏真正开始且过了延迟时间后才进行其他检测
        if (gameState.isGameActuallyStarted() && gameState.shouldCheckRoles()) {

            // 嫌疑人检测
            if (suspectTracker != null) {
                suspectTracker.updateSuspects();
            }

            if (bowDropDetector != null) {
                bowDropDetector.onClientTick();
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
     * 角色变化回调（用于自动喊话）
     */
    private void handleRoleChange(String playerName, PlayerRole newRole, ItemStack weapon) {
        // 只对杀手喊话
        if (!config.shoutEnabled || !gameState.isGameActuallyStarted() || newRole != PlayerRole.MURDERER) {
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

        if (bowDropDetector != null) {
            bowDropDetector.clear();
        }

        if (bowDropRenderHandler != null) {
            bowDropRenderHandler.clear();
        }

        if (alarmSystem != null) {
            alarmSystem.reset();
        }

        logger.info("Game data cleared!");
    }


    // ========== 公共静态方法 ==========

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
     * 获取本地玩家的实际游戏名
     * 动态获取，支持 alts 登录模组切换账号后的正确名称
     */
    public static String getLocalPlayerName() {
        if (mc.thePlayer != null) {
            return mc.thePlayer.getName();
        }
        // 玩家实体未加载时回退到 session 用户名
        return playerName;
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
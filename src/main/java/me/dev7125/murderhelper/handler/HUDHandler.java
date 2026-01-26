package me.dev7125.murderhelper.handler;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.game.BowShotDetector;
import me.dev7125.murderhelper.game.KnifeThrownDetector;
import me.dev7125.murderhelper.render.MurderMysteryHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * HUD处理器 - 负责渲染和管理敌人信息窗口
 */
public class HUDHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MurderMysteryHUD hud;

    // 鼠标状态追踪（用于检测点击）
    private boolean lastMousePressed = false;

    public HUDHandler(KnifeThrownDetector weaponDetector, BowShotDetector bowShotDetector) {
        this.hud = new MurderMysteryHUD(weaponDetector, bowShotDetector);
    }

    /**
     * 在游戏覆盖层渲染HUD窗口
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        // 只在渲染所有元素时绘制HUD
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        // 检查HUD是否启用
        if (!MurderHelperMod.config.hudEnabled) {
            return;
        }

        // 检查模组是否启用且游戏已开始
        if (!MurderHelperMod.config.globalEnabled || !MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        // 检查玩家是否存在
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // 查找最近的敌人
        EntityPlayer closestEnemy = findClosestEnemy();

        // 如果找到敌人，渲染HUD
        if (closestEnemy != null) {
            // 获取敌人的武器（优先使用记录的武器，而不是当前手持物品）
            ItemStack weapon = MurderHelperMod.getPlayerWeapon(closestEnemy);

            // 计算距离
            double distance = mc.thePlayer.getDistanceToEntity(closestEnemy);

            // 渲染HUD窗口
            hud.render(closestEnemy, weapon, distance);
        }
    }

    /**
     * 在客户端Tick时处理鼠标输入（用于拖动窗口）
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        // 只在游戏中处理鼠标输入
        if (!MurderHelperMod.config.globalEnabled || !MurderHelperMod.gameState.isInGame()) {
            return;
        }

        // 只允许在聊天界面拖动
        if (mc.currentScreen == null || !mc.currentScreen.getClass().getSimpleName().equals("GuiChat")) {
            return;
        }

        // 获取鼠标状态
        boolean mousePressed = Mouse.isButtonDown(0); // 左键

        // 获取鼠标位置（需要转换坐标系）
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;

        // 处理鼠标输入，并检查是否刚完成拖动
        boolean justFinishedDragging = hud.handleMouseInput(mouseX, mouseY, mousePressed);

        // 如果刚完成拖动，立即保存HUD位置
        if (justFinishedDragging) {
            MurderHelperMod.saveConfig();
            MurderHelperMod.logger.info("HUD position auto-saved: X=" + hud.windowX + ", Y=" + hud.windowY);
        }

        // 更新鼠标状态
        lastMousePressed = mousePressed;
    }

    /**
     * 查找最近的敌人玩家
     * @return 最近的敌人，如果没有则返回null
     */
    private EntityPlayer findClosestEnemy() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        EntityPlayer closestEnemy = null;
        double closestDistance = Double.MAX_VALUE;

        // 遍历所有玩家
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            // 跳过自己
            if (player == mc.thePlayer) {
                continue;
            }

            // 只检查在Tab列表中的玩家（过滤NPC和死亡玩家）
            if (!MurderHelperMod.isPlayerInTabList(player)) {
                continue;
            }

            // 检查是否是敌人
            if (MurderHelperMod.isEnemy(player.getName())) {
                double distance = mc.thePlayer.getDistanceToEntity(player);

                // 更新最近的敌人
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEnemy = player;
                }
            }
        }

        return closestEnemy;
    }

    /**
     * 获取HUD实例（供配置使用）
     */
    public MurderMysteryHUD getHUD() {
        return hud;
    }
}
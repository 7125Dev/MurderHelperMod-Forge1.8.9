package me.dev7125.murderhelper.handler;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.render.NametagRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 渲染处理器
 * 负责决定何时渲染什么内容，具体渲染工作委托给专门的Renderer类
 */
public class RenderHandler {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // 只有游戏真正开始后才渲染名牌
        if (!MurderHelperMod.config.globalEnabled || !MurderHelperMod.gameState.isInGame() || !MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // 遍历所有玩家实体，渲染自定义命名牌
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue; // 不渲染自己的命名牌

            // 只渲染在Tab列表中的玩家（过滤NPC和死亡玩家）
            if (!MurderHelperMod.isPlayerInTabList(player)) continue;

            // 判断是否应该渲染命名牌
            if (shouldRenderNametag(player)) {
                // 委托给NametagRenderer进行实际渲染
                NametagRenderer.renderThroughWallNametag(player, event.partialTicks);
            }
        }
    }

    /**
     * 判断是否应该渲染命名牌
     * 必须和 MixinRendererLivingEntity.shouldRenderCustomNameTag() 保持一致
     * 
     * @param player 要判断的玩家
     * @return 是否应该渲染
     */
    private boolean shouldRenderNametag(EntityPlayer player) {
        switch (MurderHelperMod.config.renderNameTags) {
            case 0: // All Player - 所有玩家
                return true;
            case 1: // Enemy Faction - 只显示敌对阵营
                return MurderHelperMod.isEnemy(player.getName());
            default:
                return false;
        }
    }
}
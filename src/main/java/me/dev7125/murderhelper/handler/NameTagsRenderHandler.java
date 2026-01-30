package me.dev7125.murderhelper.handler;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.render.NametagRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 渲染处理器
 * 负责决定何时渲染什么内容，具体渲染工作委托给专门的Renderer类
 */
public class NameTagsRenderHandler {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // 只有游戏真正开始后才渲染名牌
        if (!MurderHelperMod.config.globalEnabled ||
                !MurderHelperMod.gameState.isInGame() ||
                !MurderHelperMod.isGameActuallyStarted()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // 用于本次渲染循环去重（避免渲染重复的玩家实体）
        Set<String> renderedPlayers = new HashSet<>();

        // 遍历所有玩家实体，渲染自定义命名牌
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null) continue;

            String playerName = player.getName();

            // 跳过自己
            if (player == mc.thePlayer) continue;

            // 只渲染真实玩家（从GameStateManager的真实玩家列表），过滤NPC
            if (!MurderHelperMod.gameState.isRealPlayer(playerName)) {
                continue;
            }

            // 防止重复渲染（游戏胜利后会复制多份相同玩家实体）
            if (renderedPlayers.contains(playerName)) {
                continue;
            }

            // 判断是否应该渲染命名牌
            if (shouldRenderNametag(player)) {
                // 委托给NametagRenderer进行实际渲染
                NametagRenderer.renderThroughWallNametag(player, event.partialTicks);

                // 标记该玩家已渲染
                renderedPlayers.add(playerName);
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
package me.dev7125.murderhelper.mixins;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin类用于拦截原版实体名字牌渲染
 * 只有在满足所有条件时才会拦截
 */
@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {

    /**
     * 拦截原版实体名字牌渲染方法
     */
    @Inject(
            method = "renderName",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void onRenderName(
            EntityLivingBase entity,
            double x,
            double y,
            double z,
            CallbackInfo ci
    ) {
        // 第一步：检查是否是玩家实体
        if (!(entity instanceof EntityPlayer)) {
            return; // 不是玩家，不处理
        }

        // 第二步：检查所有前提条件（必须和 RenderHandler 保持一致）
        if (!MurderHelperMod.config.globalEnabled ||
                !MurderHelperMod.gameState.isInGame() ||
                !MurderHelperMod.isGameActuallyStarted()) {
            return; // 条件不满足，使用原版渲染
        }

        // 第三步：不处理自己的名字牌
        EntityPlayer player = (EntityPlayer) entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && player == mc.thePlayer) {
            return; // 是自己，不处理
        }

        // 第四步：检查玩家是否在Tab列表中（过滤NPC和死亡玩家）
        if (!MurderHelperMod.isPlayerInTabList(player)) {
            // 不在Tab列表中，取消渲染
            ci.cancel();
            return;
        }

        // 第五步：根据配置判断是否应该渲染自定义命名牌
        if (shouldRenderCustomNameTag(player)) {
            // 取消原版渲染！我们会在RenderHandler中渲染自定义命名牌
            ci.cancel();
        }
        // 如果不应该渲染自定义命名牌，则不取消，让原版继续渲染
    }

    /**
     * 判断是否应该渲染自定义命名牌
     * 必须和 RenderHandler.shouldRenderNametag() 保持一致
     */
    private boolean shouldRenderCustomNameTag(EntityPlayer player) {
        String playerName = player.getName();

        switch (MurderHelperMod.config.renderNameTags) {
            case 0:
                // All Player - 所有玩家都用自定义命名牌
                return true;

            case 1:
                // Enemy Faction - 只有敌对阵营用自定义命名牌
                return MurderHelperMod.isEnemy(playerName);

            default:
                return false;
        }
    }
}
package me.dev7125.murderhelper.mixins;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderManager.class)
public class MixinRenderManager {

    @Shadow
    public boolean renderOutlines;

    /**
     * 注入到renderDebugBoundingBox方法，强制渲染隐形玩家的碰撞箱
     */
    @Inject(
            method = "renderDebugBoundingBox",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderDebugBoundingBox(
            Entity entity,
            double x, double y, double z,
            float yaw,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (!MurderHelperMod.isGameActuallyStarted()) return;

        if (!(entity instanceof EntityPlayer)) return;

        // 如果增强hitbox渲染关闭，使用原版渲染
        if (!MurderHelperMod.config.enhancedHitboxes) return;

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);

        GL11.glLineWidth(3.0F);
        GlStateManager.color(0.0F, 0.75F, 1.0F, 1.0F);

        AxisAlignedBB bb = entity.getEntityBoundingBox();
        AxisAlignedBB renderBB = new AxisAlignedBB(
                bb.minX - entity.posX + x,
                bb.minY - entity.posY + y,
                bb.minZ - entity.posZ + z,
                bb.maxX - entity.posX + x,
                bb.maxY - entity.posY + y,
                bb.maxZ - entity.posZ + z
        );

        RenderGlobal.drawOutlinedBoundingBox(renderBB, 0, 191, 255, 255);

        // 状态恢复
        GL11.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        ci.cancel();
    }
}
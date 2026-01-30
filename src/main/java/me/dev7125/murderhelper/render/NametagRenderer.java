package me.dev7125.murderhelper.render;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/**
 * 玩家名牌渲染器
 * 负责渲染穿墙的自定义命名牌
 */
public class NametagRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * 渲染穿墙命名牌（主方法）
     *
     * @param player 要渲染命名牌的玩家
     * @param partialTicks 部分tick，用于平滑渲染
     */
    public static void renderThroughWallNametag(EntityPlayer player, float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer fontRenderer = mc.fontRendererObj;

        // 计算玩家的插值位置（平滑移动）
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - renderManager.viewerPosY + player.height + 0.5D;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

        // 计算距离并调整缩放
        float distance = mc.thePlayer.getDistanceToEntity(player);
        float scale = 0.016666668F * 1.6F; // 基础缩放

        if (distance > 10.0F) {
            scale = scale * Math.min(distance / 10.0F, 2.5F);
        }

        // 获取文本和颜色
        String text = getNametagText(player);
        int nametagColor = getNametagColor(player);

        // 渲染命名牌
        render3DNametag(text, x, y, z, nametagColor, scale, true);
    }

    /**
     * 渲染3D命名牌（通用方法）
     *
     * @param text 要显示的文本
     * @param x 世界相对坐标X
     * @param y 世界相对坐标Y
     * @param z 世界相对坐标Z
     * @param color 文字颜色
     * @param scale 缩放比例
     * @param throughWalls 是否穿墙显示
     */
    public static void render3DNametag(String text, double x, double y, double z, int color, float scale, boolean throughWalls) {
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer fontRenderer = mc.fontRendererObj;

        // 保存当前OpenGL状态
        GlStateManager.pushMatrix();

        // 移动到目标位置
        GlStateManager.translate((float)x, (float)y, (float)z);

        // 朝向摄像机（Billboard效果）
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        // 缩放和翻转
        GlStateManager.scale(-scale, -scale, scale);

        // 如果需要穿墙，禁用深度测试
        if (throughWalls) {
            GlStateManager.disableDepth();
        }

        GlStateManager.disableLighting();

        // 启用混合（透明效果）
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        // 绘制背景
        renderNametagBackground(text, fontRenderer);

        // 绘制文字
        GlStateManager.enableTexture2D();
        int textWidth = fontRenderer.getStringWidth(text);
        fontRenderer.drawString(text, -textWidth / 2, 0, color);

        // 恢复OpenGL状态
        if (throughWalls) {
            GlStateManager.enableDepth();
        }

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.popMatrix();
    }

    /**
     * 渲染命名牌背景（半透明黑色矩形）
     *
     * @param text 文本内容（用于计算宽度）
     * @param fontRenderer 字体渲染器
     */
    private static void renderNametagBackground(String text, FontRenderer fontRenderer) {
        GlStateManager.disableTexture2D();

        int textWidth = fontRenderer.getStringWidth(text);
        int bgX1 = -textWidth / 2 - 1;
        int bgX2 = textWidth / 2 + 1;
        int bgY1 = -1;
        int bgY2 = 8;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(bgX1, bgY2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(bgX2, bgY2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(bgX2, bgY1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        worldRenderer.pos(bgX1, bgY1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
        tessellator.draw();
    }

    /**
     * 获取命名牌文本(包含嫌疑人标记)
     *
     * @param player 玩家实体
     * @return 命名牌文本
     */
    public static String getNametagText(EntityPlayer player) {
        String baseName = player.getName();

        // 检查是否是嫌疑人(现在通过RoleDetector查询)
        if (MurderHelperMod.roleDetector != null &&
                MurderHelperMod.roleDetector.isSuspect(player)) {
            return baseName + " §e[Suspect]";
        }

        return baseName;
    }

    /**
     * 根据角色获取命名牌颜色
     *
     * @param player 玩家实体
     * @return 颜色值(ARGB格式)
     */
    public static int getNametagColor(EntityPlayer player) {
        // 先检查是否是嫌疑人(优先级高于普通角色颜色)
        if (MurderHelperMod.roleDetector != null &&
                MurderHelperMod.roleDetector.isSuspect(player)) {
            return 0xFFFFAA00; // 嫌疑人 - 橙色(介于红色和黄色之间)
        }

        MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(player);
        switch (role) {
            case MURDERER:
                return 0xFFFF5555; // 杀手 - 红色
            case DETECTIVE:
                return 0xFF55FFFF; // 侦探 - 青蓝
            case SHOOTER:
                return 0xFF55AAFF; // 射手 - 亮蓝
            case SUSPECT:
                return 0xFFFFAA00; // 嫌疑人 - 橙色
            case INNOCENT:
            default:
                return 0xFF55FF55; // 平民 - 绿色
        }
    }

}
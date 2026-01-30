package me.dev7125.murderhelper.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.opengl.GL11;

/**
 * 弓箭掉落物穿墙渲染工具类
 * 优化版本：直接基于坐标渲染，无需依赖实体对象
 */
public class BowDropRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * 渲染弓箭掉落物的黄色穿墙文字（基于坐标）
     *
     * @param x 世界坐标X
     * @param y 世界坐标Y
     * @param z 世界坐标Z
     * @param dropTime 掉落时间（毫秒时间戳）
     */
    public static void renderBowDropText(double x, double y, double z, long dropTime) {
        if (mc.thePlayer == null) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        FontRenderer fontRenderer = mc.fontRendererObj;

        // 计算掉落秒数
        long currentTime = System.currentTimeMillis();
        int seconds = (int) ((currentTime - dropTime) / 1000);

        // 计算距离玩家的距离
        double dx = x - mc.thePlayer.posX;
        double dy = y - mc.thePlayer.posY;
        double dz = z - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int distanceMeters = (int) distance;

        // 构建显示文字
        String text = String.format("Detective's Bow Drop %ds %dM", seconds, distanceMeters);

        // 转换为相对于渲染视角的坐标
        double renderX = x - renderManager.viewerPosX;
        double renderY = y - renderManager.viewerPosY + 0.5; // 在掉落位置上方显示
        double renderZ = z - renderManager.viewerPosZ;

        // 保存当前OpenGL状态
        GL11.glPushMatrix();

        // 移动到目标位置
        GL11.glTranslated(renderX, renderY, renderZ);

        // 使文字始终面向玩家（Billboard效果）
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);

        // 缩放文字大小（根据距离调整，距离越远字体越大）
        float scale = 0.02F;
        float distanceScale = (float) (distance * 0.1);
        if (distanceScale < 1.0F) distanceScale = 1.0F;
        scale *= distanceScale;

        GL11.glScalef(-scale, -scale, scale);

        // 禁用光照
        GlStateManager.disableLighting();

        // 启用混合以实现透明效果
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // 禁用深度测试以实现穿墙效果
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // 禁用纹理（用于绘制背景）
        GlStateManager.disableTexture2D();

        // 计算文字宽度
        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;

        // 绘制半透明黑色背景
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.5F); // 黑色，50%透明度
        GL11.glVertex3f(-halfWidth - 1, -1, 0);
        GL11.glVertex3f(-halfWidth - 1, 8, 0);
        GL11.glVertex3f(halfWidth + 1, 8, 0);
        GL11.glVertex3f(halfWidth + 1, -1, 0);
        GL11.glEnd();

        // 启用纹理以绘制文字
        GlStateManager.enableTexture2D();

        // 设置黄色 (RGB: 255, 255, 85)
        int yellowColor = 0xFFFF55;

        // 绘制文字（带阴影效果）
        fontRenderer.drawString(text, -halfWidth, 0, yellowColor, true);

        // 恢复深度测试
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // 恢复光照
        GlStateManager.enableLighting();

        // 禁用混合
        GlStateManager.disableBlend();

        // 恢复OpenGL状态
        GL11.glPopMatrix();
    }
}

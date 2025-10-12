package me.dev7125.murderhelper.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import org.lwjgl.opengl.GL11;

/**
 * 弓箭掉落物穿墙渲染工具类
 */
public class BowDropRenderer {
    
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    /**
     * 渲染弓箭掉落物的橙色穿墙文字
     * 
     * @param entityItem 弓箭掉落物实体
     * @param dropTime 掉落时间（毫秒时间戳）
     * @param partialTicks 部分tick，用于平滑渲染
     */
    public static void renderBowDropText(EntityItem entityItem, long dropTime, float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer fontRenderer = mc.fontRendererObj;
        
        // 计算掉落秒数
        long currentTime = System.currentTimeMillis();
        int seconds = (int) ((currentTime - dropTime) / 1000);
        
        // 计算距离玩家的距离
        double distance = mc.thePlayer.getDistanceToEntity(entityItem);
        int distanceMeters = (int) distance;
        
        // 构建显示文字
        String text = String.format("Detective's Bow Drop %ds %dM", seconds, distanceMeters);
        
        // 计算实体位置（插值以实现平滑移动）
        double x = entityItem.lastTickPosX + (entityItem.posX - entityItem.lastTickPosX) * partialTicks;
        double y = entityItem.lastTickPosY + (entityItem.posY - entityItem.lastTickPosY) * partialTicks;
        double z = entityItem.lastTickPosZ + (entityItem.posZ - entityItem.lastTickPosZ) * partialTicks;
        
        // 转换为相对于渲染视角的坐标
        double renderX = x - renderManager.viewerPosX;
        double renderY = y - renderManager.viewerPosY + entityItem.height + 0.5; // 在实体上方显示
        double renderZ = z - renderManager.viewerPosZ;
        
        // 保存当前OpenGL状态
        GL11.glPushMatrix();
        
        // 移动到实体位置
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
        
        // 设置橙色 (RGB: 255, 165, 0)
        int orangeColor = 0xFFA500;
        
        // 绘制文字（带阴影效果）
        fontRenderer.drawString(text, -halfWidth, 0, orangeColor, true);
        
        // 恢复深度测试
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        
        // 恢复光照
        GlStateManager.enableLighting();
        
        // 禁用混合
        GlStateManager.disableBlend();
        
        // 恢复OpenGL状态
        GL11.glPopMatrix();
    }
    
    /**
     * 渲染穿墙效果的文字（通用方法）
     * 
     * @param text 要显示的文字
     * @param x 世界坐标X
     * @param y 世界坐标Y
     * @param z 世界坐标Z
     * @param color 文字颜色（十六进制，例如0xFFA500为橙色）
     * @param scale 缩放比例
     * @param throughWalls 是否穿墙显示
     */
    public static void render3DText(String text, double x, double y, double z, int color, float scale, boolean throughWalls) {
        RenderManager renderManager = mc.getRenderManager();
        FontRenderer fontRenderer = mc.fontRendererObj;
        
        // 转换为相对于渲染视角的坐标
        double renderX = x - renderManager.viewerPosX;
        double renderY = y - renderManager.viewerPosY;
        double renderZ = z - renderManager.viewerPosZ;
        
        GL11.glPushMatrix();
        
        GL11.glTranslated(renderX, renderY, renderZ);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        
        // 如果需要穿墙，禁用深度测试
        if (throughWalls) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        
        // 绘制背景
        int textWidth = fontRenderer.getStringWidth(text);
        int halfWidth = textWidth / 2;
        
        GlStateManager.disableTexture2D();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.5F);
        GL11.glVertex3f(-halfWidth - 1, -1, 0);
        GL11.glVertex3f(-halfWidth - 1, 8, 0);
        GL11.glVertex3f(halfWidth + 1, 8, 0);
        GL11.glVertex3f(halfWidth + 1, -1, 0);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        
        // 绘制文字
        fontRenderer.drawString(text, -halfWidth, 0, color, true);
        
        if (throughWalls) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }
}
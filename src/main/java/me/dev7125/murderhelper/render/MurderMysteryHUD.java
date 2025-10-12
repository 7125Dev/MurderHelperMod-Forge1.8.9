package me.dev7125.murderhelper.render;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 密室谋杀游戏 - 可拖动玩家信息窗口
 * 垂直滑块 + 自适应宽度
 */
public class MurderMysteryHUD {

    private final Minecraft mc;

    // 窗口尺寸（动态计算）
    private int currentWindowWidth = 120;
    private static final int MIN_WIDTH = 120;
    private static final int WINDOW_HEIGHT = 98;
    private static final int SLIDER_WIDTH = 10;

    // 窗口位置
    public int windowX = 10;
    public int windowY = 10;

    // 拖动状态
    private boolean isDragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

    // 滑块拖动状态
    private boolean isDraggingSlider = false;

    // 背景透明度 (0-255)
    public int bgAlpha = 192;

    // 颜色常量
    private static final int SAFE_COLOR = 0x90EE90;
    private static final int DANGER_COLOR = 0x8B0000;

    // 距离阈值
    private static final double SAFE_DISTANCE = 50.0;
    private static final double DANGER_DISTANCE = 10.0;

    public MurderMysteryHUD() {
        this.mc = Minecraft.getMinecraft();
    }

    /**
     * 渲染主窗口
     */
    public void render(EntityPlayer targetPlayer, ItemStack weapon, double distance) {
        if (targetPlayer == null || mc.thePlayer == null) return;

        ScaledResolution sr = new ScaledResolution(mc);

        // 计算需要的宽度
        currentWindowWidth = calculateWindowWidth(targetPlayer, weapon);

        clampWindowPosition(sr);

        int borderColor = calculateBorderColor(distance);
        int bgColor = (bgAlpha << 24) | 0x000000;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();

        // 绘制主背景（不包括滑块区域）
        drawRect(windowX, windowY, windowX + currentWindowWidth, windowY + WINDOW_HEIGHT, bgColor);

        // 绘制边框
        drawBorder(windowX, windowY, currentWindowWidth, WINDOW_HEIGHT, 2, borderColor);

        // 渲染内容
        int contentX = windowX + 8;
        int contentY = windowY + 8;

        // 1. 绘制玩家头像（24x24）
        drawPlayerHead(targetPlayer, contentX, contentY, 24);

        // 2. 玩家名称和状态（在头像右侧）
        String playerName = targetPlayer.getName();
        mc.fontRendererObj.drawStringWithShadow(playerName, contentX + 30, contentY, 0xFFFFFF);

        // 根据角色显示不同的文字和颜色
        MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(targetPlayer);
        String roleText;
        int roleColor;

        if (role == MurderHelperMod.PlayerRole.DETECTIVE) {
            roleText = "§9Detective"; // 蓝色
            roleColor = 0x5555FF;
        } else if (role == MurderHelperMod.PlayerRole.MURDERER) {
            roleText = "§cMurderer"; // 红色
            roleColor = 0xFF5555;
        } else {
            roleText = "§aInnocent"; // 绿色（虽然不太可能出现）
            roleColor = 0x55FF55;
        }
        mc.fontRendererObj.drawStringWithShadow(roleText, contentX + 30, contentY + 10, roleColor);

        // 3. 武器信息
        contentY += 28;
        if (weapon != null) {
            drawItemStack(weapon, contentX, contentY - 4, 20); // 放大到20x20
            String weaponName = getFormattedItemName(weapon);
            mc.fontRendererObj.drawStringWithShadow("§7" + weaponName, contentX + 24, contentY, 0xFFFFFF);
        } else {
            mc.fontRendererObj.drawStringWithShadow("§7Unknown", contentX, contentY, 0xAAAAAA);
        }

        // 4. 距离（带颜色和状态）
        contentY += 14;
        int distanceColor = calculateDistanceColor(distance);
        String distanceText = String.format("%.1fm", distance);
        String statusText = getStatusText(distance);
        mc.fontRendererObj.drawStringWithShadow(distanceText, contentX, contentY, distanceColor);
        mc.fontRendererObj.drawString(statusText, contentX + 42, contentY, 0xFFFFFF);

        // 5. 坐标
        contentY += 12;
        String coordText = String.format("§7X:§f%.0f §7Y:§f%.0f §7Z:§f%.0f",
                targetPlayer.posX, targetPlayer.posY, targetPlayer.posZ);
        mc.fontRendererObj.drawStringWithShadow(coordText, contentX, contentY, 0xFFFFFF);

        // 6. 方向
        contentY += 12;
        double deltaX = targetPlayer.posX - mc.thePlayer.posX;
        double deltaZ = targetPlayer.posZ - mc.thePlayer.posZ;
        String direction = getDirectionText(deltaX, deltaZ);
        mc.fontRendererObj.drawStringWithShadow("§7Dir: §f" + direction, contentX, contentY, 0xFFFFFF);

        // 绘制垂直透明度滑块（右侧）
        drawOpacitySlider();

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * 计算窗口需要的宽度（根据内容）
     */
    private int calculateWindowWidth(EntityPlayer player, ItemStack weapon) {
        int maxWidth = MIN_WIDTH;

        // 检查玩家名字宽度
        String playerName = player.getName();
        int nameWidth = mc.fontRendererObj.getStringWidth(playerName) + 38; // 30 (头像) + 8 (padding)
        maxWidth = Math.max(maxWidth, nameWidth);

        // 检查武器名字宽度
        if (weapon != null) {
            String weaponName = getFormattedItemName(weapon);
            int weaponWidth = mc.fontRendererObj.getStringWidth(weaponName) + 32; // 24 (图标) + 8 (padding)
            maxWidth = Math.max(maxWidth, weaponWidth);
        }

        // 检查坐标宽度
        String coordText = String.format("X:%.0f Y:%.0f Z:%.0f",
                player.posX, player.posY, player.posZ);
        int coordWidth = mc.fontRendererObj.getStringWidth(coordText) + 16;
        maxWidth = Math.max(maxWidth, coordWidth);

        // 添加滑块宽度
        return maxWidth + SLIDER_WIDTH;
    }

    /**
     * 绘制垂直透明度滑块（窗口右侧）
     */
    private void drawOpacitySlider() {
        int sliderX = windowX + currentWindowWidth - SLIDER_WIDTH;
        int sliderY = windowY;

        // 滑块背景（深色）
        int sliderBgColor = 0x80000000;
        drawRect(sliderX, sliderY, sliderX + SLIDER_WIDTH, sliderY + WINDOW_HEIGHT, sliderBgColor);

        // 滑块轨道（垂直）
        int trackX = sliderX + 4;
        int trackY = sliderY + 10;
        int trackWidth = 2;
        int trackHeight = WINDOW_HEIGHT - 20;
        drawRect(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0xFF555555);

        // 计算滑块位置（从上到下：透明到不透明）
        float ratio = bgAlpha / 255.0f;
        int knobY = trackY + (int)(trackHeight * (1.0f - ratio)); // 反转：上面=透明，下面=不透明
        int knobX = sliderX + 2;
        int knobSize = 6;

        // 绘制滑块按钮
        drawRect(knobX, knobY - knobSize/2, knobX + knobSize, knobY + knobSize/2, 0xFFFFFFFF);

        // 在滑块上显示百分比（旋转显示）
        GlStateManager.pushMatrix();
        GlStateManager.translate(sliderX + 5, sliderY + WINDOW_HEIGHT / 2, 0);
        GlStateManager.rotate(-90, 0, 0, 1);
        String opacityText = String.format("%d%%", (int)(ratio * 100));
        int textWidth = mc.fontRendererObj.getStringWidth(opacityText);
        mc.fontRendererObj.drawStringWithShadow(opacityText, -textWidth / 2, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    /**
     * 获取格式化的物品名称 (驼峰命名 + 显示名)
     */
    private String getFormattedItemName(ItemStack weapon) {
        String itemName = "Unknown";
        if (weapon != null) {
            // 获取物品注册名
            String registryName = weapon.getItem().getRegistryName();
            if (registryName != null) {
                // 去掉 minecraft: 前缀
                if (registryName.contains(":")) {
                    registryName = registryName.substring(registryName.indexOf(":") + 1);
                }
                // 将 diamond_sword 转换为 DiamondSword (驼峰命名)
                String[] parts = registryName.split("_");
                StringBuilder camelCase = new StringBuilder();
                for (String part : parts) {
                    if (part.length() > 0) {
                        // 首字母大写，其余小写
                        camelCase.append(Character.toUpperCase(part.charAt(0)));
                        if (part.length() > 1) {
                            camelCase.append(part.substring(1).toLowerCase());
                        }
                    }
                }
                registryName = camelCase.toString();
            }
            // 获取显示名并去除颜色代码
            String displayName = weapon.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                // 去除所有 Minecraft 颜色代码 (§ + 一个字符)
                displayName = displayName.replaceAll("§[0-9a-fk-or]", "");
                // 格式化为: DiamondSword(钻石剑)
                itemName = registryName + "(" + displayName + ")";
            } else {
                // 如果显示名为空，只显示驼峰命名
                itemName = registryName;
            }
            // 如果注册名也为空，使用 Unknown
            if (itemName == null || itemName.isEmpty()) {
                itemName = "Unknown";
            }
        }
        return itemName;
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(double distance) {
        if (distance <= DANGER_DISTANCE) {
            return "§c§lDANGER";
        } else if (distance <= SAFE_DISTANCE / 2) {
            return "§6Alert";
        } else if (distance <= SAFE_DISTANCE) {
            return "§eWatch";
        } else {
            return "§aSafe";
        }
    }

    /**
     * 获取方向文本
     */
    private String getDirectionText(double deltaX, double deltaZ) {
        double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "E →";
        else if (angle >= 22.5 && angle < 67.5) return "SE ↘";
        else if (angle >= 67.5 && angle < 112.5) return "S ↓";
        else if (angle >= 112.5 && angle < 157.5) return "SW ↙";
        else if (angle >= 157.5 && angle < 202.5) return "W ←";
        else if (angle >= 202.5 && angle < 247.5) return "NW ↖";
        else if (angle >= 247.5 && angle < 292.5) return "N ↑";
        else return "NE ↗";
    }

    /**
     * 计算边框颜色
     */
    private int calculateBorderColor(double distance) {
        if (distance >= SAFE_DISTANCE) {
            return 0xFF000000 | SAFE_COLOR;
        } else if (distance <= DANGER_DISTANCE) {
            return 0xFF000000 | DANGER_COLOR;
        } else {
            float ratio = (float) ((distance - DANGER_DISTANCE) / (SAFE_DISTANCE - DANGER_DISTANCE));
            return interpolateColor(DANGER_COLOR, SAFE_COLOR, ratio);
        }
    }

    /**
     * 计算距离文字颜色
     */
    private int calculateDistanceColor(double distance) {
        if (distance >= SAFE_DISTANCE) {
            return 0x00FF00;
        } else if (distance <= DANGER_DISTANCE) {
            return 0xFF0000;
        } else {
            float ratio = (float) ((distance - DANGER_DISTANCE) / (SAFE_DISTANCE - DANGER_DISTANCE));
            return interpolateColor(0xFF0000, 0x00FF00, ratio);
        }
    }

    /**
     * 颜色插值
     */
    private int interpolateColor(int color1, int color2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 绘制边框
     */
    private void drawBorder(int x, int y, int width, int height, int thickness, int color) {
        drawRect(x, y, x + width, y + thickness, color);
        drawRect(x, y + height - thickness, x + width, y + height, color);
        drawRect(x, y, x + thickness, y + height, color);
        drawRect(x + width - thickness, y, x + width, y + height, color);
    }

    /**
     * 绘制玩家头像
     */
    private void drawPlayerHead(EntityPlayer player, int x, int y, int size) {
        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        ResourceLocation skinLocation;
        if (player instanceof AbstractClientPlayer) {
            skinLocation = ((AbstractClientPlayer) player).getLocationSkin();
        } else {
            skinLocation = new ResourceLocation("textures/entity/steve.png");
        }

        mc.getTextureManager().bindTexture(skinLocation);

        GlStateManager.enableBlend();
        drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);
        drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, size, size, 64.0F, 64.0F);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * 绘制物品图标（支持自定义大小）
     */
    private void drawItemStack(ItemStack stack, int x, int y, int size) {
        if (stack == null) return;

        RenderItem renderer = mc.getRenderItem();
        if (renderer == null) return;

        try {
            GlStateManager.pushMatrix();

            // 缩放到指定大小
            float scale = size / 16.0f; // 默认物品大小是16x16
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, 1.0f);

            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();

            renderer.renderItemAndEffectIntoGUI(stack, 0, 0);

            GlStateManager.disableDepth();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        } catch (Exception e) {
            GlStateManager.popMatrix();
        }
    }

    /**
     * 绘制物品图标（默认16x16大小，保留用于兼容）
     */
    private void drawItemStack(ItemStack stack, int x, int y) {
        drawItemStack(stack, x, y, 16);
    }

    /**
     * 处理鼠标输入（拖动窗口和滑块）
     */
    public boolean handleMouseInput(int mouseX, int mouseY, boolean mousePressed) {
        boolean justFinishedDragging = false;

        int sliderX = windowX + currentWindowWidth - SLIDER_WIDTH;

        if (mousePressed) {
            // 检查是否点击滑块区域
            if (isMouseOverSlider(mouseX, mouseY) && !isDragging) {
                isDraggingSlider = true;
                updateSliderValue(mouseY);
            }
            // 检查是否点击窗口（但不是滑块）
            else if (isMouseOverWindow(mouseX, mouseY) && !isDraggingSlider && mouseX < sliderX) {
                if (!isDragging) {
                    isDragging = true;
                    dragOffsetX = mouseX - windowX;
                    dragOffsetY = mouseY - windowY;
                }
            }
        } else {
            // 松开鼠标
            if (isDragging) {
                justFinishedDragging = true;
            }
            isDragging = false;
            if (isDraggingSlider) {
                isDraggingSlider = false;
                justFinishedDragging = true;
            }
        }

        // 拖动窗口
        if (isDragging) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
        }

        // 拖动滑块
        if (isDraggingSlider) {
            updateSliderValue(mouseY);
        }

        return justFinishedDragging;
    }

    /**
     * 更新滑块值（垂直）
     */
    private void updateSliderValue(int mouseY) {
        int trackY = windowY + 10;
        int trackHeight = WINDOW_HEIGHT - 20;

        int relativeY = mouseY - trackY;
        relativeY = Math.max(0, Math.min(relativeY, trackHeight));

        // 反转：上面=透明(0)，下面=不透明(255)
        float ratio = 1.0f - ((float) relativeY / trackHeight);
        bgAlpha = (int) (ratio * 255);
        bgAlpha = Math.max(0, Math.min(255, bgAlpha));
    }

    /**
     * 检查鼠标是否在滑块上
     */
    private boolean isMouseOverSlider(int mouseX, int mouseY) {
        int sliderX = windowX + currentWindowWidth - SLIDER_WIDTH;
        return mouseX >= sliderX && mouseX <= sliderX + SLIDER_WIDTH &&
                mouseY >= windowY && mouseY <= windowY + WINDOW_HEIGHT;
    }

    /**
     * 检查鼠标是否在窗口上
     */
    private boolean isMouseOverWindow(int mouseX, int mouseY) {
        return mouseX >= windowX && mouseX <= windowX + currentWindowWidth &&
                mouseY >= windowY && mouseY <= windowY + WINDOW_HEIGHT;
    }

    /**
     * 确保窗口在屏幕范围内
     */
    private void clampWindowPosition(ScaledResolution sr) {
        windowX = Math.max(0, Math.min(windowX, sr.getScaledWidth() - currentWindowWidth));
        windowY = Math.max(0, Math.min(windowY, sr.getScaledHeight() - WINDOW_HEIGHT));
    }

    /**
     * 绘制矩形
     */
    private void drawRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
    }

    /**
     * 绘制缩放的自定义UV矩形
     */
    private void drawScaledCustomSizeModalRect(int x, int y, float u, float v, int uWidth, int vHeight,
                                               int width, int height, float tileWidth, float tileHeight) {
        float f = 1.0F / tileWidth;
        float f1 = 1.0F / tileHeight;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u * f, (v + vHeight) * f1);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f((u + uWidth) * f, (v + vHeight) * f1);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f((u + uWidth) * f, v * f1);
        GL11.glVertex2f(x + width, y);
        GL11.glTexCoord2f(u * f, v * f1);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }
}
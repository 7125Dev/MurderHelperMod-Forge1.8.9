package me.dev7125.murderhelper.render;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.game.BowShotDetector;
import me.dev7125.murderhelper.game.KnifeThrownDetector;
import me.dev7125.murderhelper.util.ItemClassifier;
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
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * 密室谋杀游戏 - 可拖动玩家信息窗口
 * 垂直滑块 + 自适应宽度
 */
public class MurderMysteryHUD {

    private final Minecraft mc;
    private final KnifeThrownDetector knifeThrownDetector;
    private final BowShotDetector bowShotDetector;

    // 窗口尺寸（动态计算）
    private int currentWindowWidth = 120;
    private static final int MIN_WIDTH = 120;
    private static final int WINDOW_HEIGHT = 110; // 增加高度以容纳新行
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

    public MurderMysteryHUD(KnifeThrownDetector weaponDetector, BowShotDetector bowShotDetector) {
        this.mc = Minecraft.getMinecraft();
        this.knifeThrownDetector = weaponDetector;
        this.bowShotDetector = bowShotDetector;
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

        // 2. 玩家名称（在头像右侧）
        String playerName = targetPlayer.getName();
        mc.fontRendererObj.drawStringWithShadow(playerName, contentX + 30, contentY, 0xFFFFFF);

        // 3. 角色显示（在名称下方）
        MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(targetPlayer);
        String roleText = getRoleText(role);
        int roleColor = getRoleColor(role);
        mc.fontRendererObj.drawStringWithShadow(roleText, contentX + 30, contentY + 10, roleColor);

        // 4. 武器信息（分两行显示）
        contentY += 28;
        if (weapon != null) {
            drawItemStack(weapon, contentX, contentY - 4, 20);

            // 第一行：武器名称 + 类型
            String weaponNameAndType = getWeaponNameAndType(targetPlayer, weapon, role);
            mc.fontRendererObj.drawStringWithShadow(weaponNameAndType, contentX + 24, contentY, 0xFFFFFF);

            // 第二行：状态信息
            contentY += 10;
            String weaponStatus = getWeaponStatus(targetPlayer, weapon, role);
            mc.fontRendererObj.drawStringWithShadow(weaponStatus, contentX + 24, contentY, 0xFFFFFF);
        } else {
            // 显示最后记录的武器信息
            String lastWeaponInfo = getLastKnownWeaponInfo(targetPlayer, role);
            mc.fontRendererObj.drawStringWithShadow(lastWeaponInfo, contentX, contentY, 0xAAAAAA);
        }

        // 5. 距离（带颜色和状态）
        contentY += 14;
        int distanceColor = calculateDistanceColor(distance);
        String distanceText = String.format("%.1fm", distance);
        String statusText = getStatusText(distance);
        mc.fontRendererObj.drawStringWithShadow(distanceText, contentX, contentY, distanceColor);
        mc.fontRendererObj.drawString(statusText, contentX + 42, contentY, 0xFFFFFF);

        // 6. 飞刀位置信息（仅在凶手飞行中时显示，作为危险警告）
        if (role == MurderHelperMod.PlayerRole.MURDERER) {
            KnifeThrownDetector.WeaponInfo weaponInfo = knifeThrownDetector.getWeaponInfo(targetPlayer.getName());

            if (weaponInfo != null && weaponInfo.getKnifeState() == KnifeThrownDetector.KnifeState.IN_FLIGHT) {
                contentY += 12;
                Vec3 knifePos = weaponInfo.projectile.position;
                if (knifePos != null) {
                    double knifeDist = Math.sqrt(
                            Math.pow(knifePos.xCoord - mc.thePlayer.posX, 2) +
                                    Math.pow(knifePos.yCoord - mc.thePlayer.posY, 2) +
                                    Math.pow(knifePos.zCoord - mc.thePlayer.posZ, 2)
                    );

                    int knifeColor = knifeDist <= 5.0 ? 0xFF0000 : (knifeDist <= 15.0 ? 0xFFAA00 : 0xFFFF00);
                    String knifeInfo = String.format("§cKnife in Air: §f%.1fm", knifeDist);
                    mc.fontRendererObj.drawStringWithShadow(knifeInfo, contentX, contentY, knifeColor);
                }
            }
        }
        // 6b. 箭矢距离信息（仅SHOOTER和DETECTIVE显示）
        else if (role == MurderHelperMod.PlayerRole.SHOOTER || role == MurderHelperMod.PlayerRole.DETECTIVE) {
            String arrowInfo = getArrowDistanceInfo(targetPlayer, role);
            if (arrowInfo != null) {
                contentY += 12;
                mc.fontRendererObj.drawStringWithShadow(arrowInfo, contentX, contentY, 0xFFFFFF);
            }
        }

        // 7. 坐标
        contentY += 12;
        String coordText = String.format("§7X:§f%.0f §7Y:§f%.0f §7Z:§f%.0f",
                targetPlayer.posX, targetPlayer.posY, targetPlayer.posZ);
        mc.fontRendererObj.drawStringWithShadow(coordText, contentX, contentY, 0xFFFFFF);

        // 绘制垂直透明度滑块（右侧）
        drawOpacitySlider();

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * 获取角色文本
     */
    private String getRoleText(MurderHelperMod.PlayerRole role) {
        switch (role) {
            case MURDERER:
                return "§cMurderer";
            case DETECTIVE:
                return "§9Detective";
            case SHOOTER:
                return "§6Shooter";
            case INNOCENT:
            default:
                return "§aInnocent";
        }
    }

    /**
     * 获取角色颜色
     */
    private int getRoleColor(MurderHelperMod.PlayerRole role) {
        switch (role) {
            case MURDERER:
                return 0xFF5555;
            case DETECTIVE:
                return 0xFF55FFFF;
            case SHOOTER:
                return 0xFF0000AA;
            case INNOCENT:
            default:
                return 0xFF55FF55;
        }
    }

    /**
     * 获取最后已知的武器信息（当手上没有物品时）
     */
    private String getLastKnownWeaponInfo(EntityPlayer player, MurderHelperMod.PlayerRole role) {
        // 从PlayerTracker获取记录的武器
        ItemStack recordedWeapon = MurderHelperMod.playerTracker.getPlayerWeapon(player);

        if (recordedWeapon == null) {
            return "§7Unknown";
        }

        String displayName = recordedWeapon.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            displayName = displayName.replaceAll("§[0-9a-fk-or]", "");
        } else {
            displayName = "Unknown";
        }

        // 如果是射手或侦探，添加弓类型标签
        String typeText = "";
        if (role == MurderHelperMod.PlayerRole.SHOOTER || role == MurderHelperMod.PlayerRole.DETECTIVE) {
            ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(recordedWeapon);
            switch (bowCategory) {
                case DETECTIVE_BOW:
                    typeText = " §9[Detective]";
                    break;
                case KALI_BOW:
                    typeText = " §d[Kali/∞]";
                    break;
                case NORMAL_BOW:
                    typeText = " §a[Normal]";
                    break;
                case NONE:
                    break;
            }
        }

        return "§7" + displayName + typeText + " §7(Unarmed)";
    }

    /**
     * 获取武器名称和类型（第一行）
     */
    private String getWeaponNameAndType(EntityPlayer player, ItemStack weapon, MurderHelperMod.PlayerRole role) {
        if (weapon == null) {
            return "§7Unknown";
        }

        // 获取显示名并去除颜色代码
        String displayName = weapon.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            displayName = displayName.replaceAll("§[0-9a-fk-or]", "");
        } else {
            displayName = "Unknown";
        }

        String typeText = "";

        // 如果是射手或侦探，显示弓的类型
        if (role == MurderHelperMod.PlayerRole.SHOOTER || role == MurderHelperMod.PlayerRole.DETECTIVE) {
            ItemClassifier.BowCategory bowCategory = ItemClassifier.getBowCategory(weapon);

            switch (bowCategory) {
                case DETECTIVE_BOW:
                    typeText = " §9[Detective]";
                    break;
                case KALI_BOW:
                    typeText = " §d[Kali/∞]";
                    break;
                case NORMAL_BOW:
                    typeText = " §a[Normal]";
                    break;
                case NONE:
                    break;
            }
        }

        return displayName + typeText;
    }

    /**
     * 获取武器状态信息（第二行）
     */
    private String getWeaponStatus(EntityPlayer player, ItemStack weapon, MurderHelperMod.PlayerRole role) {
        if (weapon == null) {
            return "";
        }

        // 凶手的武器状态
        if (role == MurderHelperMod.PlayerRole.MURDERER) {
            KnifeThrownDetector.WeaponInfo info = knifeThrownDetector.getWeaponInfo(player.getName());

            if (info != null) {
                // 手持状态
                KnifeThrownDetector.HoldingState holdingState = info.getHoldingState();
                String holdingText = "";
                switch (holdingState) {
                    case HOLDING:
                        holdingText = "§a(Holding)";
                        break;
                    case NOT_HOLDING:
                        holdingText = "§7(Unarmed)";
                        break;
                }

                // 飞刀状态
                KnifeThrownDetector.KnifeState knifeState = info.getKnifeState();
                String knifeText = "";
                switch (knifeState) {
                    case IN_FLIGHT:
                        knifeText = " §c[Flying]";
                        break;
                    case COOLDOWN:
                        double cooldown = info.getCooldownRemainingSeconds();
                        // CD结束时显示Ready
                        if (cooldown <= 0) {
                            knifeText = " §a[Ready]";
                        } else {
                            knifeText = String.format(" §6[CD: %.1fs]", cooldown);
                        }
                        break;
                    case NONE:
                        knifeText = " §a[Ready]";
                        break;
                }

                return holdingText + knifeText;
            }
        }
        // 射手或侦探的弓状态
        else if (role == MurderHelperMod.PlayerRole.SHOOTER || role == MurderHelperMod.PlayerRole.DETECTIVE) {
            BowShotDetector.BowInfo bowInfo = bowShotDetector.getBowInfo(player.getName());

            if (bowInfo != null) {
                // 手持状态
                BowShotDetector.HoldingState holdingState = bowInfo.getHoldingState();
                String holdingText = "";
                switch (holdingState) {
                    case HOLDING:
                        holdingText = "§a(Holding)";
                        break;
                    case NOT_HOLDING:
                        holdingText = "§7(Unarmed)";
                        break;
                }

                // 拉弓状态（优先显示）
                BowShotDetector.DrawState drawState = bowInfo.getDrawState();
                String drawText = "";
                switch (drawState) {
                    case DRAWING:
                        drawText = " §e[Drawing]";
                        break;
                    case READY_TO_SHOOT:
                        drawText = " §c[Charged!]";
                        break;
                    case NONE:
                        // 不拉弓时才显示射击状态
                        BowShotDetector.ShotState shotState = bowInfo.getShotState();
                        switch (shotState) {
                            case COOLDOWN:
                                double cooldown = bowInfo.getCooldownRemainingSeconds();
                                // CD结束时显示Ready
                                if (cooldown <= 0) {
                                    drawText = " §a[Ready]";
                                } else {
                                    drawText = String.format(" §6[CD: %.1fs]", cooldown);
                                }
                                break;
                            case SHOT:
                                drawText = " §7(Shot)";
                                break;
                            case READY:
                                drawText = " §a[Ready]";
                                break;
                        }
                        break;
                }

                return holdingText + drawText;
            } else {
                // 没有弓信息，只显示手持状态
                ItemStack heldItem = player.getHeldItem();
                boolean isHolding = heldItem != null && areItemStacksEqual(weapon, heldItem);
                return isHolding ? "§a(Holding)" : "§7(Unarmed)";
            }
        }
        // 普通玩家
        else {
            ItemStack heldItem = player.getHeldItem();
            boolean isHolding = heldItem != null && areItemStacksEqual(weapon, heldItem);
            return isHolding ? "§a(Holding)" : "§7(Unarmed)";
        }

        return "";
    }

    /**
     * 获取箭矢信息文本（仅SHOOTER和DETECTIVE显示）
     */
    private String getArrowDistanceInfo(EntityPlayer player, MurderHelperMod.PlayerRole role) {
        if (role != MurderHelperMod.PlayerRole.SHOOTER && role != MurderHelperMod.PlayerRole.DETECTIVE) {
            return null;
        }

        BowShotDetector.ArrowInfo nearestArrow = bowShotDetector.getNearestArrowToLocalPlayer(player.getName());
        if (nearestArrow == null) {
            return null;
        }

        double arrowDist = nearestArrow.getDistanceToPlayer(mc.thePlayer);

        int arrowColor;
        String warningText;
        if (arrowDist <= 3.0) {
            arrowColor = 0xFF0000;
            warningText = "§c§lDANGER!";
        } else if (arrowDist <= 8.0) {
            arrowColor = 0xFFAA00;
            warningText = "§6Warning";
        } else if (arrowDist <= 15.0) {
            arrowColor = 0xFFFF00;
            warningText = "§eIncoming";
        } else {
            arrowColor = 0xAAAAAA;
            warningText = "§7Far";
        }

        return String.format("§cArrow: §f%.1fm %s", arrowDist, warningText);
    }

    /**
     * 比较两个 ItemStack 是否相同
     */
    private boolean areItemStacksEqual(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null && stack2 == null) return true;
        if (stack1 == null || stack2 == null) return false;
        return stack1.getItem() == stack2.getItem()
                && stack1.getDisplayName().equals(stack2.getDisplayName());
    }

    /**
     * 计算窗口需要的宽度（根据内容）
     */
    private int calculateWindowWidth(EntityPlayer player, ItemStack weapon) {
        int maxWidth = MIN_WIDTH;

        String playerName = player.getName();
        int nameWidth = mc.fontRendererObj.getStringWidth(playerName) + 38;
        maxWidth = Math.max(maxWidth, nameWidth);

        if (weapon != null) {
            MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(player);

            // 检查第一行宽度（名称+类型）
            String weaponNameAndType = getWeaponNameAndType(player, weapon, role);
            String cleanName = weaponNameAndType.replaceAll("§[0-9a-fk-or]", "");
            int nameLineWidth = mc.fontRendererObj.getStringWidth(cleanName) + 32;
            maxWidth = Math.max(maxWidth, nameLineWidth);

            // 检查第二行宽度（状态）
            String weaponStatus = getWeaponStatus(player, weapon, role);
            String cleanStatus = weaponStatus.replaceAll("§[0-9a-fk-or]", "");
            int statusLineWidth = mc.fontRendererObj.getStringWidth(cleanStatus) + 32;
            maxWidth = Math.max(maxWidth, statusLineWidth);
        }

        String coordText = String.format("X:%.0f Y:%.0f Z:%.0f",
                player.posX, player.posY, player.posZ);
        int coordWidth = mc.fontRendererObj.getStringWidth(coordText) + 16;
        maxWidth = Math.max(maxWidth, coordWidth);

        return maxWidth + SLIDER_WIDTH;
    }

    /**
     * 绘制垂直透明度滑块（窗口右侧）
     */
    private void drawOpacitySlider() {
        int sliderX = windowX + currentWindowWidth - SLIDER_WIDTH;
        int sliderY = windowY;

        int sliderBgColor = 0x80000000;
        drawRect(sliderX, sliderY, sliderX + SLIDER_WIDTH, sliderY + WINDOW_HEIGHT, sliderBgColor);

        int trackX = sliderX + 4;
        int trackY = sliderY + 10;
        int trackWidth = 2;
        int trackHeight = WINDOW_HEIGHT - 20;
        drawRect(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0xFF555555);

        float ratio = bgAlpha / 255.0f;
        int knobY = trackY + (int)(trackHeight * (1.0f - ratio));
        int knobX = sliderX + 2;
        int knobSize = 6;

        drawRect(knobX, knobY - knobSize/2, knobX + knobSize, knobY + knobSize/2, 0xFFFFFFFF);

        GlStateManager.pushMatrix();
        GlStateManager.translate(sliderX + 5, sliderY + WINDOW_HEIGHT / 2, 0);
        GlStateManager.rotate(-90, 0, 0, 1);
        String opacityText = String.format("%d%%", (int)(ratio * 100));
        int textWidth = mc.fontRendererObj.getStringWidth(opacityText);
        mc.fontRendererObj.drawStringWithShadow(opacityText, -textWidth / 2, 0, 0xFFFFFF);
        GlStateManager.popMatrix();
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

            float scale = size / 16.0f;
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
     * 处理鼠标输入（拖动窗口和滑块）
     */
    public boolean handleMouseInput(int mouseX, int mouseY, boolean mousePressed) {
        boolean justFinishedDragging = false;

        int sliderX = windowX + currentWindowWidth - SLIDER_WIDTH;

        if (mousePressed) {
            if (isMouseOverSlider(mouseX, mouseY) && !isDragging) {
                isDraggingSlider = true;
                updateSliderValue(mouseY);
            }
            else if (isMouseOverWindow(mouseX, mouseY) && !isDraggingSlider && mouseX < sliderX) {
                if (!isDragging) {
                    isDragging = true;
                    dragOffsetX = mouseX - windowX;
                    dragOffsetY = mouseY - windowY;
                }
            }
        } else {
            if (isDragging) {
                justFinishedDragging = true;
            }
            isDragging = false;
            if (isDraggingSlider) {
                isDraggingSlider = false;
                justFinishedDragging = true;
            }
        }

        if (isDragging) {
            windowX = mouseX - dragOffsetX;
            windowY = mouseY - dragOffsetY;
        }

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
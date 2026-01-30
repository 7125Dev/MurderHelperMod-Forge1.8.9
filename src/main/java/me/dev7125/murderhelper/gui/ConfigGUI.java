package me.dev7125.murderhelper.gui;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.handler.BowDropRenderHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConfigGUI extends GuiScreen {
    private GuiScreen parentScreen;

    // 按钮ID
    private static final int GLOBAL_TOGGLE = 1;
    private static final int RENDER_NAME_TAGS = 2;
    private static final int ENEMY_HUD = 3;
    private static final int BOW_ESP = 4;
    private static final int ALARM = 5;
    private static final int ENHANCED_HITBOXES = 6;
    private static final int SUSPECT_DETECTION = 13;
    private static final int SHOUT_MURDERER_TOGGLE = 7;
    private static final int EXPAND_MURDERER = 8;
    private static final int SHOUT_DROP_BOW_TOGGLE = 9;
    private static final int EXPAND_DROP_BOW = 10;
    private static final int SAVE_BUTTON = 11;
    private static final int BACK_BUTTON = 12;

    // 文本框
    private GuiTextField shoutMurdererField;
    private GuiTextField replaceMurdererFromField;
    private GuiTextField replaceMurdererToField;

    private GuiTextField shoutDropBowField;
    private GuiTextField replaceDropBowFromField;
    private GuiTextField replaceDropBowToField;

    // 按钮引用
    private GuiButton globalToggleButton;
    private GuiButton renderNameTagsButton;
    private GuiButton enemyHudButton;
    private GuiButton bowESPButton;
    private GuiButton alarmButton;
    private GuiButton enhancedHitboxesButton;
    private GuiButton suspectDetectionButton;
    private GuiButton shoutMurdererToggleButton;
    private GuiButton expandMurdererButton;
    private GuiButton shoutDropBowToggleButton;
    private GuiButton expandDropBowButton;

    private String[] renderNameTagsOptions = {"All Player", "Enemy Faction"};

    // 替换面板是否展开
    private boolean murdererReplaceExpanded = false;
    private boolean dropBowReplaceExpanded = false;

    public ConfigGUI(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int startY = 40;
        int fullWidth = 300;
        int halfWidth = 145; // (300 - 10间距) / 2
        int buttonHeight = 20;
        int spacing = 25;
        int currentY = startY;

        // 1. Enabled: ON (全宽)
        globalToggleButton = new GuiButton(GLOBAL_TOGGLE, centerX - fullWidth/2, currentY, fullWidth, buttonHeight,
                "Enabled: " + getToggleText(MurderHelperMod.config.globalEnabled));
        this.buttonList.add(globalToggleButton);
        currentY += spacing;

        // 2. Render NameTags: All Player (全宽)
        renderNameTagsButton = new GuiButton(RENDER_NAME_TAGS, centerX - fullWidth/2, currentY, fullWidth, buttonHeight,
                "Render NameTags: " + renderNameTagsOptions[MurderHelperMod.config.renderNameTags]);
        this.buttonList.add(renderNameTagsButton);
        currentY += spacing;

        // 3. Enemy HUD | Bow ESP (两个按钮)
        enemyHudButton = new GuiButton(ENEMY_HUD, centerX - fullWidth/2, currentY, halfWidth, buttonHeight,
                "Enemy HUD: " + getToggleText(MurderHelperMod.config.hudEnabled));
        this.buttonList.add(enemyHudButton);

        bowESPButton = new GuiButton(BOW_ESP, centerX - fullWidth/2 + halfWidth + 10, currentY, halfWidth, buttonHeight,
                "Bow ESP: " + getToggleText(MurderHelperMod.config.bowDropESPEnabled));
        this.buttonList.add(bowESPButton);
        currentY += spacing;

        // 4. Enhanced Hitboxes | Suspect Detection (两个按钮)
        enhancedHitboxesButton = new GuiButton(ENHANCED_HITBOXES, centerX - fullWidth/2, currentY, halfWidth, buttonHeight,
                "Enhanced Hitboxes: " + getToggleText(MurderHelperMod.config.enhancedHitboxes));
        this.buttonList.add(enhancedHitboxesButton);

        suspectDetectionButton = new GuiButton(SUSPECT_DETECTION, centerX - fullWidth/2 + halfWidth + 10, currentY, halfWidth, buttonHeight,
                "Suspect Detection: " + getToggleText(MurderHelperMod.config.suspectDetection));
        this.buttonList.add(suspectDetectionButton);
        currentY += spacing;

        // 5. Alarm (全宽)
        alarmButton = new GuiButton(ALARM, centerX - fullWidth/2, currentY, fullWidth, buttonHeight,
                "Alarm: " + getToggleText(MurderHelperMod.config.murderAlarm));
        this.buttonList.add(alarmButton);
        currentY += spacing;

        // 5. Shout Murderer 和 ... 按钮
        int shoutTextWidth = fullWidth - 30; // 为...按钮留出空间
        int shoutStartX = centerX - fullWidth/2;

        shoutMurdererToggleButton = new GuiButton(SHOUT_MURDERER_TOGGLE, shoutStartX, currentY, shoutTextWidth, buttonHeight,
                "Shout Murderer: " + getToggleText(MurderHelperMod.config.shoutEnabled));
        this.buttonList.add(shoutMurdererToggleButton);

        expandMurdererButton = new GuiButton(EXPAND_MURDERER, shoutStartX + shoutTextWidth + 5, currentY, 25, buttonHeight, "...");
        this.buttonList.add(expandMurdererButton);
        expandMurdererButton.enabled = MurderHelperMod.config.shoutEnabled;
        currentY += spacing;

        // Shout Murderer 文本框和替换面板
        if (MurderHelperMod.config.shoutEnabled) {
            shoutMurdererField = new GuiTextField(0, this.fontRendererObj, shoutStartX, currentY, fullWidth, buttonHeight);
            shoutMurdererField.setMaxStringLength(256);
            shoutMurdererField.setText(MurderHelperMod.config.shoutMessage);
            currentY += spacing;

            // 替换面板
            if (murdererReplaceExpanded) {
                int replaceFieldWidth = (fullWidth - 40) / 2; // 减去To标签和间距

                replaceMurdererFromField = new GuiTextField(1, this.fontRendererObj, shoutStartX, currentY, replaceFieldWidth, buttonHeight);
                replaceMurdererFromField.setMaxStringLength(100);
                replaceMurdererFromField.setText(MurderHelperMod.config.replaceFrom);

                int toFieldX = shoutStartX + replaceFieldWidth + 8 + 24 + 8;
                replaceMurdererToField = new GuiTextField(2, this.fontRendererObj, toFieldX, currentY, replaceFieldWidth, buttonHeight);
                replaceMurdererToField.setMaxStringLength(100);
                replaceMurdererToField.setText(MurderHelperMod.config.replaceTo);

                currentY += spacing;
            }
        } else {
            murdererReplaceExpanded = false;
        }

        // 6. Shout Drop Bow 和 ... 按钮
        shoutDropBowToggleButton = new GuiButton(SHOUT_DROP_BOW_TOGGLE, shoutStartX, currentY, shoutTextWidth, buttonHeight,
                "Shout Drop Bow: " + getToggleText(MurderHelperMod.config.shoutDropBowEnabled));
        this.buttonList.add(shoutDropBowToggleButton);

        expandDropBowButton = new GuiButton(EXPAND_DROP_BOW, shoutStartX + shoutTextWidth + 5, currentY, 25, buttonHeight, "...");
        this.buttonList.add(expandDropBowButton);
        expandDropBowButton.enabled = MurderHelperMod.config.shoutDropBowEnabled;
        currentY += spacing;

        // Shout Drop Bow 文本框和替换面板
        if (MurderHelperMod.config.shoutDropBowEnabled) {
            shoutDropBowField = new GuiTextField(3, this.fontRendererObj, shoutStartX, currentY, fullWidth, buttonHeight);
            shoutDropBowField.setMaxStringLength(256);
            shoutDropBowField.setText(MurderHelperMod.config.shoutDropBowMessage);
            currentY += spacing;

            // 替换面板
            if (dropBowReplaceExpanded) {
                int replaceFieldWidth = (fullWidth - 40) / 2;

                replaceDropBowFromField = new GuiTextField(4, this.fontRendererObj, shoutStartX, currentY, replaceFieldWidth, buttonHeight);
                replaceDropBowFromField.setMaxStringLength(100);
                replaceDropBowFromField.setText(MurderHelperMod.config.replaceDropBowFrom);

                int toFieldX = shoutStartX + replaceFieldWidth + 8 + 24 + 8;
                replaceDropBowToField = new GuiTextField(5, this.fontRendererObj, toFieldX, currentY, replaceFieldWidth, buttonHeight);
                replaceDropBowToField.setMaxStringLength(100);
                replaceDropBowToField.setText(MurderHelperMod.config.replaceDropBowTo);

                currentY += spacing;
            }
        } else {
            dropBowReplaceExpanded = false;
        }

        currentY += 5; // 额外间距

        // 7. Save | Back (两个按钮)
        this.buttonList.add(new GuiButton(SAVE_BUTTON, centerX - fullWidth/2, currentY, halfWidth, buttonHeight, "Save"));
        this.buttonList.add(new GuiButton(BACK_BUTTON, centerX - fullWidth/2 + halfWidth + 10, currentY, halfWidth, buttonHeight, "Back"));
    }

    private String getToggleText(boolean enabled) {
        return enabled ? (EnumChatFormatting.GREEN + "On") : (EnumChatFormatting.RED + "Off");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case GLOBAL_TOGGLE:
                MurderHelperMod.config.globalEnabled = !MurderHelperMod.config.globalEnabled;
                button.displayString = "Enabled: " + getToggleText(MurderHelperMod.config.globalEnabled);
                break;

            case RENDER_NAME_TAGS:
                MurderHelperMod.config.renderNameTags = (MurderHelperMod.config.renderNameTags + 1) % renderNameTagsOptions.length;
                button.displayString = "Render NameTags: " + renderNameTagsOptions[MurderHelperMod.config.renderNameTags];
                break;

            case ENEMY_HUD:
                MurderHelperMod.config.hudEnabled = !MurderHelperMod.config.hudEnabled;
                button.displayString = "Enemy HUD: " + getToggleText(MurderHelperMod.config.hudEnabled);
                break;

            case BOW_ESP:
                MurderHelperMod.config.bowDropESPEnabled = !MurderHelperMod.config.bowDropESPEnabled;
                BowDropRenderHandler.enabled = MurderHelperMod.config.bowDropESPEnabled;
                button.displayString = "Bow ESP: " + getToggleText(MurderHelperMod.config.bowDropESPEnabled);
                break;

            case ALARM:
                MurderHelperMod.config.murderAlarm = !MurderHelperMod.config.murderAlarm;
                button.displayString = "Alarm: " + getToggleText(MurderHelperMod.config.murderAlarm);
                break;

            case ENHANCED_HITBOXES:
                MurderHelperMod.config.enhancedHitboxes = !MurderHelperMod.config.enhancedHitboxes;
                button.displayString = "Enhanced Hitboxes: " + getToggleText(MurderHelperMod.config.enhancedHitboxes);
                break;

            case SUSPECT_DETECTION:
                MurderHelperMod.config.suspectDetection = !MurderHelperMod.config.suspectDetection;
                button.displayString = "Suspect Detection: " + getToggleText(MurderHelperMod.config.suspectDetection);
                break;

            case SHOUT_MURDERER_TOGGLE:
                MurderHelperMod.config.shoutEnabled = !MurderHelperMod.config.shoutEnabled;
                button.displayString = "Shout Murderer: " + getToggleText(MurderHelperMod.config.shoutEnabled);
                if (!MurderHelperMod.config.shoutEnabled) {
                    murdererReplaceExpanded = false;
                }
                initGui();
                break;

            case EXPAND_MURDERER:
                murdererReplaceExpanded = !murdererReplaceExpanded;
                initGui();
                break;

            case SHOUT_DROP_BOW_TOGGLE:
                MurderHelperMod.config.shoutDropBowEnabled = !MurderHelperMod.config.shoutDropBowEnabled;
                button.displayString = "Shout Drop Bow: " + getToggleText(MurderHelperMod.config.shoutDropBowEnabled);
                if (!MurderHelperMod.config.shoutDropBowEnabled) {
                    dropBowReplaceExpanded = false;
                }
                initGui();
                break;

            case EXPAND_DROP_BOW:
                dropBowReplaceExpanded = !dropBowReplaceExpanded;
                initGui();
                break;

            case SAVE_BUTTON:
                saveConfig();
                MurderHelperMod.saveConfig();
                this.mc.displayGuiScreen(parentScreen);
                break;

            case BACK_BUTTON:
                this.mc.displayGuiScreen(parentScreen);
                break;
        }
    }

    private void saveConfig() {
        // 保存 Shout Murderer 配置
        if (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) {
            MurderHelperMod.config.shoutMessage = shoutMurdererField.getText();
        }
        if (murdererReplaceExpanded && replaceMurdererFromField != null && replaceMurdererToField != null) {
            MurderHelperMod.config.replaceFrom = replaceMurdererFromField.getText();
            MurderHelperMod.config.replaceTo = replaceMurdererToField.getText();
        }

        // 保存 Shout Drop Bow 配置
        if (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null) {
            MurderHelperMod.config.shoutDropBowMessage = shoutDropBowField.getText();
        }
        if (dropBowReplaceExpanded && replaceDropBowFromField != null && replaceDropBowToField != null) {
            MurderHelperMod.config.replaceDropBowFrom = replaceDropBowFromField.getText();
            MurderHelperMod.config.replaceDropBowTo = replaceDropBowToField.getText();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);

        // Murderer文本框
        if (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) {
            shoutMurdererField.textboxKeyTyped(typedChar, keyCode);
        }
        if (murdererReplaceExpanded && replaceMurdererFromField != null && replaceMurdererToField != null) {
            replaceMurdererFromField.textboxKeyTyped(typedChar, keyCode);
            replaceMurdererToField.textboxKeyTyped(typedChar, keyCode);
        }

        // Drop Bow文本框
        if (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null) {
            shoutDropBowField.textboxKeyTyped(typedChar, keyCode);
        }
        if (dropBowReplaceExpanded && replaceDropBowFromField != null && replaceDropBowToField != null) {
            replaceDropBowFromField.textboxKeyTyped(typedChar, keyCode);
            replaceDropBowToField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Murderer文本框
        if (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) {
            shoutMurdererField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (murdererReplaceExpanded && replaceMurdererFromField != null && replaceMurdererToField != null) {
            replaceMurdererFromField.mouseClicked(mouseX, mouseY, mouseButton);
            replaceMurdererToField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Drop Bow文本框
        if (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null) {
            shoutDropBowField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (dropBowReplaceExpanded && replaceDropBowFromField != null && replaceDropBowToField != null) {
            replaceDropBowFromField.mouseClicked(mouseX, mouseY, mouseButton);
            replaceDropBowToField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // Murderer文本框
        if (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) {
            shoutMurdererField.updateCursorCounter();
        }
        if (murdererReplaceExpanded && replaceMurdererFromField != null && replaceMurdererToField != null) {
            replaceMurdererFromField.updateCursorCounter();
            replaceMurdererToField.updateCursorCounter();
        }

        // Drop Bow文本框
        if (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null) {
            shoutDropBowField.updateCursorCounter();
        }
        if (dropBowReplaceExpanded && replaceDropBowFromField != null && replaceDropBowToField != null) {
            replaceDropBowFromField.updateCursorCounter();
            replaceDropBowToField.updateCursorCounter();
        }
    }

    private List<String> getButtonTooltip(GuiButton button) {
        switch (button.id) {
            case GLOBAL_TOGGLE:
                return Arrays.asList("Toggle The Mod");
            case RENDER_NAME_TAGS:
                return Arrays.asList("Render Player NameTags");
            case ENEMY_HUD:
                return Arrays.asList("Show/Hide enemy information window",
                        "Displays nearest enemy's position and weapon",
                        "Window can be dragged to any position");
            case BOW_ESP:
                return Arrays.asList("Show/Hide detective's bow drop indicator",
                        "Displays orange text through walls showing:",
                        "- How long ago the bow was dropped",
                        "- Distance from you in meters");
            case ALARM:
                return Arrays.asList("When the distance to murder is less than",
                        "or equal to 50 blocks, an alarm will sound");
            case ENHANCED_HITBOXES:
                return Arrays.asList("Toggle enhanced hitbox rendering",
                        "ON: Bold blue outline (Mixin override)",
                        "OFF: Vanilla white outline");
            case SUSPECT_DETECTION:
                return Arrays.asList("Toggle automatic suspect detection",
                        "When enabled, players near corpses",
                        "will be marked as suspects");
            case SHOUT_MURDERER_TOGGLE:
                return Arrays.asList("Detected a murderer and automatically",
                        "sends a template message in chat");
            case EXPAND_MURDERER:
                return Arrays.asList("Show/Hide text replacement settings",
                        "for murderer shout message");
            case SHOUT_DROP_BOW_TOGGLE:
                return Arrays.asList("Detected bow drop and automatically",
                        "sends a template message in chat");
            case EXPAND_DROP_BOW:
                return Arrays.asList("Show/Hide text replacement settings",
                        "for bow drop shout message");
            case SAVE_BUTTON:
                return Arrays.asList("Save Config");
            case BACK_BUTTON:
                return Arrays.asList("Back to Game");
            default:
                return null;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // 标题
        this.drawCenteredString(this.fontRendererObj, "MurderHelperMod Made By 7125Dev", this.width / 2, 20, 0xFFFFFF);

        // 绘制 Shout Murderer 相关内容
        if (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) {
            this.drawString(this.fontRendererObj, "Murderer Message:", shoutMurdererField.xPosition, shoutMurdererField.yPosition - 12, 0xFFFFFF);
            shoutMurdererField.drawTextBox();

            if (murdererReplaceExpanded && replaceMurdererFromField != null && replaceMurdererToField != null) {
                replaceMurdererFromField.drawTextBox();
                if (replaceMurdererFromField.getText().isEmpty() && !replaceMurdererFromField.isFocused()) {
                    this.drawString(this.fontRendererObj, "Replace from...",
                            replaceMurdererFromField.xPosition + 4, replaceMurdererFromField.yPosition + 6, 0x808080);
                }

                int toX = replaceMurdererFromField.xPosition + replaceMurdererFromField.width + 8;
                int toY = replaceMurdererFromField.yPosition + 6;
                this.drawString(this.fontRendererObj, "To", toX, toY, 0xFFFFFF);

                replaceMurdererToField.drawTextBox();
                if (replaceMurdererToField.getText().isEmpty() && !replaceMurdererToField.isFocused()) {
                    this.drawString(this.fontRendererObj, "Replace to...",
                            replaceMurdererToField.xPosition + 4, replaceMurdererToField.yPosition + 6, 0x808080);
                }
            }
        }

        // 绘制 Shout Drop Bow 相关内容
        if (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null) {
            this.drawString(this.fontRendererObj, "Drop Bow Message:", shoutDropBowField.xPosition, shoutDropBowField.yPosition - 12, 0xFFFFFF);
            shoutDropBowField.drawTextBox();

            if (dropBowReplaceExpanded && replaceDropBowFromField != null && replaceDropBowToField != null) {
                replaceDropBowFromField.drawTextBox();
                if (replaceDropBowFromField.getText().isEmpty() && !replaceDropBowFromField.isFocused()) {
                    this.drawString(this.fontRendererObj, "Replace from...",
                            replaceDropBowFromField.xPosition + 4, replaceDropBowFromField.yPosition + 6, 0x808080);
                }

                int toX = replaceDropBowFromField.xPosition + replaceDropBowFromField.width + 8;
                int toY = replaceDropBowFromField.yPosition + 6;
                this.drawString(this.fontRendererObj, "To", toX, toY, 0xFFFFFF);

                replaceDropBowToField.drawTextBox();
                if (replaceDropBowToField.getText().isEmpty() && !replaceDropBowToField.isFocused()) {
                    this.drawString(this.fontRendererObj, "Replace to...",
                            replaceDropBowToField.xPosition + 4, replaceDropBowToField.yPosition + 6, 0x808080);
                }
            }
        }

        // 绘制占位符提示（右侧）
        boolean showPlaceholders = (MurderHelperMod.config.shoutEnabled && shoutMurdererField != null) ||
                (MurderHelperMod.config.shoutDropBowEnabled && shoutDropBowField != null);

        if (showPlaceholders) {
            int tipX = this.width / 2 + 170; // 右侧固定位置
            int tipY = 80;

            this.drawString(this.fontRendererObj, "Available placeholders:", tipX, tipY, 0xAAAAAA);
            tipY += 12;

            // Murderer相关占位符
            if (MurderHelperMod.config.shoutEnabled) {
                this.drawString(this.fontRendererObj, "%Murderer%", tipX, tipY, 0x88FF88);
                tipY += 10;
                this.drawString(this.fontRendererObj, " - Murderer name", tipX + 4, tipY, 0x888888);
                tipY += 12;
                this.drawString(this.fontRendererObj, "%Item%", tipX, tipY, 0x88FF88);
                tipY += 10;
                this.drawString(this.fontRendererObj, " - Weapon name", tipX + 4, tipY, 0x888888);
                tipY += 12;
                this.drawString(this.fontRendererObj, "%X%, %Y%, %Z%", tipX, tipY, 0x88FF88);
                tipY += 10;
                this.drawString(this.fontRendererObj, " - Murderer position", tipX + 4, tipY, 0x888888);
                tipY += 15;
            }

            // Drop Bow相关占位符
            if (MurderHelperMod.config.shoutDropBowEnabled) {
                this.drawString(this.fontRendererObj, "%Detective%", tipX, tipY, 0x88FFFF);
                tipY += 10;
                this.drawString(this.fontRendererObj, " - Detective name", tipX + 4, tipY, 0x888888);
                tipY += 12;
                this.drawString(this.fontRendererObj, "%bowX%, %bowY%, %bowZ%", tipX, tipY, 0x88FFFF);
                tipY += 10;
                this.drawString(this.fontRendererObj, " - Bow drop position", tipX + 4, tipY, 0x888888);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 绘制鼠标悬浮提示
        for (GuiButton button : this.buttonList) {
            if (button.visible && button.isMouseOver()) {
                List<String> tooltip = getButtonTooltip(button);
                if (tooltip != null && !tooltip.isEmpty()) {
                    this.drawHoveringText(tooltip, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
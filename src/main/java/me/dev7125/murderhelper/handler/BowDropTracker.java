package me.dev7125.murderhelper.handler;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.feature.ShoutMessageBuilder;
import me.dev7125.murderhelper.render.BowDropRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * 弓箭追踪和渲染处理器
 * 支持两种形式：EntityItem（掉落物）和 EntityArmorStand（盔甲架持弓）
 */
public class BowDropTracker {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // 记录弓实体及其检测时间（支持EntityItem和EntityArmorStand）
    private Map<Entity, Long> bowEntities = new HashMap<>();

    // 记录已经喊过话的弓位置（使用字符串格式 "x,y,z"），避免重复发送
    private Set<String> shoutedBowPositions = new HashSet<>();

    // 位置判定的容差范围（格）
    private static final double POSITION_TOLERANCE = 2.0;

    // 配置项：是否启用弓箭显示
    public static boolean enabled = true;

    /**
     * 每tick检测和清理弓
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // 只在游戏世界存在且模组启用时运行
        if (!enabled || !MurderHelperMod.config.globalEnabled || mc.theWorld == null) {
            return;
        }

        // 只在游戏真正开始后才追踪
        if (!MurderHelperMod.isGameActuallyStarted()) {
            // 如果游戏未开始，清空所有追踪
            if (!bowEntities.isEmpty()) {
                bowEntities.clear();
                MurderHelperMod.logger.info("BowDropTracker cleared (game not started)");
            }
            return;
        }

        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        // 清理已消失的弓实体
        Iterator<Map.Entry<Entity, Long>> iterator = bowEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, Long> entry = iterator.next();
            Entity entity = entry.getKey();

            // 如果实体已死亡或不在世界中，移除追踪
            if (entity.isDead || !mc.theWorld.loadedEntityList.contains(entity)) {
                iterator.remove();
                // 注意：不从 shoutedBowPositions 中移除，因为同位置可能再生成新弓
                MurderHelperMod.logger.info("Removed dead/missing bow entity: " + entity.getClass().getSimpleName());
            }
        }

        // 检测新的弓实体
        for (Object obj : mc.theWorld.loadedEntityList) {
            // 情况1：掉落物形式的弓
            if (obj instanceof EntityItem) {
                EntityItem entityItem = (EntityItem) obj;
                ItemStack stack = entityItem.getEntityItem();

                if (stack != null && stack.getItem() == Items.bow) {
                    if (!bowEntities.containsKey(entityItem)) {
                        bowEntities.put(entityItem, System.currentTimeMillis());
                        MurderHelperMod.logger.info("=== NEW BOW DROP (EntityItem) DETECTED ===");
                        MurderHelperMod.logger.info("Position: " + String.format("(%.1f, %.1f, %.1f)",
                                entityItem.posX, entityItem.posY, entityItem.posZ));
                        MurderHelperMod.logger.info("Entity ID: " + entityItem.getEntityId());

                        // 自动喊话(如果启用且该位置未喊过)
                        if (MurderHelperMod.config.shoutDropBowEnabled &&
                                !hasShoutedAtPosition(entityItem.posX, entityItem.posY, entityItem.posZ)) {
                            onBowDetected(entityItem);
                            markPositionAsShouted(entityItem.posX, entityItem.posY, entityItem.posZ);
                        }
                    }
                }
            }
            // 情况2：盔甲架持弓
            else if (obj instanceof EntityArmorStand) {
                EntityArmorStand armorStand = (EntityArmorStand) obj;

                // 检查是否持有弓
                ItemStack heldItem = armorStand.getEquipmentInSlot(0);
                if (heldItem == null || heldItem.getItem() != Items.bow) {
                    continue;
                }

                // 确保只有弓，没有穿戴盔甲（排除NPC）
                boolean hasArmor = false;
                for (int i = 1; i <= 4; i++) { // Slot 1-4 是盔甲槽
                    ItemStack equipment = armorStand.getEquipmentInSlot(i);
                    if (equipment != null) {
                        hasArmor = true;
                        break;
                    }
                }

                if (hasArmor) {
                    continue; // 有盔甲，是NPC，跳过
                }

                // 必须是隐形的
                if (!armorStand.isInvisible()) {
                    continue;
                }

                // 判断是否为弓掉落（兼容两种模式）
                boolean isBowDrop = false;

                // 模式1：新版本 - 无重力 + 有底座 + 标记模式(Hypixel检测)
                if (armorStand.hasNoGravity() && !armorStand.hasNoBasePlate() && armorStand.hasMarker()) {
                    isBowDrop = true;
                }

                // 模式2：旧版本 - 有重力 + 无底座(Mineberry和Mineblaze检测)
                if (!isBowDrop && !armorStand.hasNoGravity() && armorStand.hasNoBasePlate()) {
                    isBowDrop = true;
                }

                // 这是真正的弓掉落
                if (isBowDrop && !bowEntities.containsKey(armorStand)) {
                    bowEntities.put(armorStand, System.currentTimeMillis());
                    MurderHelperMod.logger.info("=== NEW BOW DROP (ArmorStand) DETECTED ===");
                    MurderHelperMod.logger.info("Position: " + String.format("(%.1f, %.1f, %.1f)",
                            armorStand.posX, armorStand.posY, armorStand.posZ));
                    MurderHelperMod.logger.info("Entity ID: " + armorStand.getEntityId());

                    // 自动喊话(如果启用且该位置未喊过)
                    if (MurderHelperMod.config.shoutDropBowEnabled &&
                            !hasShoutedAtPosition(armorStand.posX, armorStand.posY, armorStand.posZ)) {
                        onBowDetected(armorStand);
                        markPositionAsShouted(armorStand.posX, armorStand.posY, armorStand.posZ);
                    }
                }
            }
        }
    }

    /**
     * 检查某个位置是否已经喊过话
     */
    private boolean hasShoutedAtPosition(double x, double y, double z) {
        for (String posKey : shoutedBowPositions) {
            String[] parts = posKey.split(",");
            double savedX = Double.parseDouble(parts[0]);
            double savedY = Double.parseDouble(parts[1]);
            double savedZ = Double.parseDouble(parts[2]);

            // 计算距离，如果在容差范围内则认为是同一位置
            double distance = Math.sqrt(
                    Math.pow(x - savedX, 2) +
                            Math.pow(y - savedY, 2) +
                            Math.pow(z - savedZ, 2)
            );

            if (distance < POSITION_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标记某个位置已经喊过话
     */
    private void markPositionAsShouted(double x, double y, double z) {
        String posKey = String.format("%.1f,%.1f,%.1f", x, y, z);
        shoutedBowPositions.add(posKey);
        MurderHelperMod.logger.info("Marked position as shouted: " + posKey);
    }

    /**
     * 渲染所有追踪的弓实体文字
     */
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // 检查前置条件
        if (!enabled || !MurderHelperMod.config.globalEnabled ||
                !MurderHelperMod.isGameActuallyStarted() ||
                mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // 遍历所有追踪的弓实体，渲染文字
        for (Map.Entry<Entity, Long> entry : bowEntities.entrySet()) {
            Entity bowEntity = entry.getKey();
            Long detectedTime = entry.getValue();

            // 再次检查实体是否有效
            if (bowEntity.isDead || !mc.theWorld.loadedEntityList.contains(bowEntity)) {
                continue;
            }

            // 调用渲染器渲染文字
            // 对于EntityItem使用原有方法，对于EntityArmorStand创建临时EntityItem进行渲染
            if (bowEntity instanceof EntityItem) {
                BowDropRenderer.renderBowDropText((EntityItem) bowEntity, detectedTime, event.partialTicks);
            } else if (bowEntity instanceof EntityArmorStand) {
                // 为盔甲架创建虚拟的EntityItem用于渲染
                // 注意：这里只是借用渲染方法，实际上是在盔甲架位置渲染
                EntityArmorStand armorStand = (EntityArmorStand) bowEntity;
                renderArmorStandBow(armorStand, detectedTime, event.partialTicks);
            }
        }
    }

    /**
     * 渲染盔甲架上的弓
     * 直接调用BowDropRenderer，传递盔甲架的位置信息
     */
    private void renderArmorStandBow(EntityArmorStand armorStand, Long detectedTime, float partialTicks) {
        // 创建一个临时的EntityItem用于渲染（不添加到世界中）
        ItemStack bowStack = new ItemStack(Items.bow);
        EntityItem tempItem = new EntityItem(mc.theWorld, armorStand.posX, armorStand.posY, armorStand.posZ, bowStack);

        // 复制盔甲架的位置信息到临时EntityItem
        tempItem.posX = armorStand.posX;
        tempItem.posY = armorStand.posY + 1.0; // 盔甲架比较高，向上偏移1格
        tempItem.posZ = armorStand.posZ;
        tempItem.lastTickPosX = armorStand.lastTickPosX;
        tempItem.lastTickPosY = armorStand.lastTickPosY + 1.0;
        tempItem.lastTickPosZ = armorStand.lastTickPosZ;

        // 使用BowDropRenderer渲染
        BowDropRenderer.renderBowDropText(tempItem, detectedTime, partialTicks);
    }

    /**
     * 清空所有追踪的弓实体（游戏结束或切换世界时调用）
     */
    public void clear() {
        bowEntities.clear();
        shoutedBowPositions.clear(); // 清空位置记录
        MurderHelperMod.logger.info("BowDropTracker cleared manually");
    }

    /**
     * 获取当前追踪的弓实体数量
     */
    public int getTrackedBowCount() {
        return bowEntities.size();
    }

    /**
     * 手动添加一个弓实体追踪
     */
    public void addBowEntity(Entity entity) {
        if (entity != null) {
            boolean isValidBow = false;

            if (entity instanceof EntityItem) {
                EntityItem item = (EntityItem) entity;
                ItemStack stack = item.getEntityItem();
                isValidBow = (stack != null && stack.getItem() == Items.bow);
            } else if (entity instanceof EntityArmorStand) {
                EntityArmorStand armorStand = (EntityArmorStand) entity;
                for (int i = 0; i < 5; i++) {
                    ItemStack equipment = armorStand.getEquipmentInSlot(i);
                    if (equipment != null && equipment.getItem() == Items.bow) {
                        isValidBow = true;
                        break;
                    }
                }
            }

            if (isValidBow) {
                bowEntities.put(entity, System.currentTimeMillis());
                MurderHelperMod.logger.info("Manually added bow entity tracking: " + entity.getClass().getSimpleName());
            }
        }
    }

    /**
     * 手动添加一个弓箭掉落物追踪（保留兼容性）
     * @param entityItem 弓箭掉落物实体
     */
    public void addBowDrop(EntityItem entityItem) {
        addBowEntity(entityItem);
    }

    /**
     * 移除指定的弓实体追踪
     */
    public void removeBowEntity(Entity entity) {
        if (bowEntities.remove(entity) != null) {
            MurderHelperMod.logger.info("Manually removed bow entity tracking: " + entity.getClass().getSimpleName());
        }
    }

    /**
     * 移除指定的弓箭掉落物追踪（保留兼容性）
     * @param entityItem 要移除的弓箭掉落物实体
     */
    public void removeBowDrop(EntityItem entityItem) {
        removeBowEntity(entityItem);
    }

    /**
     * 当检测到弓时的回调（用于自动喊话）
     */
    private void onBowDetected(Entity bowEntity) {
        MurderHelperMod.logger.info("=== onBowDetected called ===");
        MurderHelperMod.logger.info("shoutDropBowEnabled: " + MurderHelperMod.config.shoutDropBowEnabled);
        MurderHelperMod.logger.info("mc.thePlayer != null: " + (mc.thePlayer != null));

        if (!MurderHelperMod.config.shoutDropBowEnabled || mc.thePlayer == null) {
            MurderHelperMod.logger.info("Shout disabled or player is null, returning");
            return;
        }

        // 查找掉弓的侦探（通过最近的侦探玩家推测）
        String detectiveName = findNearestDetective(bowEntity);
        MurderHelperMod.logger.info("Found nearest detective: " + detectiveName);

        // 构建喊话消息
        String message = buildBowShoutMessage(detectiveName, bowEntity);
        MurderHelperMod.logger.info("Built message: '" + message + "'");
        MurderHelperMod.logger.info("Message length: " + message.length());
        MurderHelperMod.logger.info("Message after trim: '" + message.trim() + "'");
        MurderHelperMod.logger.info("Is empty after trim: " + message.trim().isEmpty());

        if (!message.trim().isEmpty()) {
            mc.thePlayer.sendChatMessage(message);
            MurderHelperMod.logger.info("Auto shout bow detected: " + message);
        } else {
            MurderHelperMod.logger.warn("Message is empty, not sending chat message");
        }
    }

    /**
     * 查找掉弓的侦探（通过对比Tab栏和侦探列表找出缺失的侦探）
     * @param bowEntity 弓实体
     * @return 侦探名字，如果找不到返回 "Unknown"
     */
    private String findNearestDetective(Entity bowEntity) {
        if (mc.theWorld == null) {
            return "Unknown";
        }

        // 收集当前Tab栏中所有侦探的名字
        Set<String> currentDetectives = new HashSet<>();

        for (Object obj : mc.theWorld.playerEntities) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) obj;
                String playerName = player.getName();

                // 使用正确的方法获取玩家角色
                MurderHelperMod.PlayerRole role = MurderHelperMod.getPlayerRole(player);
                if (role == MurderHelperMod.PlayerRole.DETECTIVE) {
                    currentDetectives.add(playerName);
                }
            }
        }

        // 获取所有被锁定的侦探列表
        Set<String> lockedDetectives = MurderHelperMod.playerTracker.getLockedDetectives();

        // 找出缺失的侦探（在锁定列表中但不在当前Tab栏中）
        for (String lockedDetective : lockedDetectives) {
            if (!currentDetectives.contains(lockedDetective)) {
                // 这个侦探不在当前列表中，说明他可能掉弓了或者已经死了
                MurderHelperMod.logger.info("Found missing detective: " + lockedDetective);
                return lockedDetective;
            }
        }

        // 如果没有找到缺失的侦探，可能是弓刚生成，所有侦探都在
        // 这种情况下返回最近的侦探作为备选方案
        if (!currentDetectives.isEmpty()) {
            double nearestDistance = Double.MAX_VALUE;
            String nearestDetective = "Unknown";

            for (Object obj : mc.theWorld.playerEntities) {
                if (obj instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) obj;
                    String playerName = player.getName();

                    if (currentDetectives.contains(playerName)) {
                        double distance = player.getDistanceToEntity(bowEntity);
                        if (distance < nearestDistance && distance < 20.0) {
                            nearestDistance = distance;
                            nearestDetective = playerName;
                        }
                    }
                }
            }

            MurderHelperMod.logger.info("No missing detective found, using nearest: " + nearestDetective);
            return nearestDetective;
        }

        return "Unknown";
    }

    /**
     * 构建弓喊话消息
     * @param detectiveName 侦探名字
     * @param bowEntity 弓实体
     * @return 喊话消息
     */
    private String buildBowShoutMessage(String detectiveName, Entity bowEntity) {
        return ShoutMessageBuilder.buildBowDropShout(
                MurderHelperMod.config.shoutDropBowMessage,
                detectiveName,
                bowEntity.posX,
                bowEntity.posY,
                bowEntity.posZ,
                MurderHelperMod.config.replaceDropBowFrom,
                MurderHelperMod.config.replaceDropBowTo,
                MurderHelperMod.logger
        );
    }
}
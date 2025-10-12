package me.dev7125.murderhelper.feature;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * 警报系统
 * 负责检测敌人接近并发出警报
 */
public class AlarmSystem {
    
    private final Minecraft mc = Minecraft.getMinecraft();
    
    // 上次检测到的敌人
    private EntityPlayer lastDetectedEnemy = null;
    
    // 警报音效计数器
    private int alarmTicks = 0;
    
    // 警报范围（方块）
    private static final double ALARM_RANGE = 50.0;
    
    /**
     * 检查并处理警报
     * 应该在每tick调用
     */
    public void checkAndAlarm() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        
        EntityPlayer closestEnemy = findClosestEnemy();
        
        if (closestEnemy != null) {
            double distance = mc.thePlayer.getDistanceToEntity(closestEnemy);
            
            // 只在首次检测到敌人或敌人发生变化时输出消息
            if (lastDetectedEnemy != closestEnemy) {
                notifyEnemyDetected(closestEnemy);
                lastDetectedEnemy = closestEnemy;
            }
            
            // 播放警报音效
            playAlarmSound(distance);
        } else {
            // 没有检测到敌人，重置状态
            alarmTicks = 0;
            lastDetectedEnemy = null;
        }
    }
    
    /**
     * 查找最近的敌人
     * @return 最近的敌人，如果没有则返回null
     */
    private EntityPlayer findClosestEnemy() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        
        EntityPlayer closestEnemy = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            
            // 只检查在Tab列表中的玩家
            if (!MurderHelperMod.isPlayerInTabList(player)) continue;
            
            // 检查是否是敌人
            if (MurderHelperMod.isEnemy(player.getName())) {
                double distance = mc.thePlayer.getDistanceToEntity(player);
                
                // 在警报范围内且比当前最近的敌人更近
                if (distance < ALARM_RANGE && distance < closestDistance) {
                    closestDistance = distance;
                    closestEnemy = player;
                }
            }
        }
        
        return closestEnemy;
    }
    
    /**
     * 发送敌人检测通知
     */
    private void notifyEnemyDetected(EntityPlayer enemy) {
        String message = EnumChatFormatting.RED + "⚠ Enemy Detected: " + enemy.getName();
        mc.thePlayer.addChatComponentMessage(new ChatComponentText(message));
    }
    
    /**
     * 播放警报音效
     * @param distance 敌人距离
     */
    private void playAlarmSound(double distance) {
        alarmTicks++;
        
        // 根据距离调整音效间隔（距离越近，间隔越短）
        int soundInterval = Math.max(10, (int)(distance / 2));
        
        if (alarmTicks % soundInterval == 0) {
            // 根据距离调整音高（距离越近，音高越高）
            float pitch = 2.0F - ((float)distance / (float)ALARM_RANGE);
            
            mc.thePlayer.playSound(
                "note.pling",
                1.0F,    // 音量
                pitch    // 音高
            );
        }
    }
    
    /**
     * 重置警报系统
     */
    public void reset() {
        lastDetectedEnemy = null;
        alarmTicks = 0;
    }
    
    /**
     * 获取当前检测到的敌人
     */
    public EntityPlayer getLastDetectedEnemy() {
        return lastDetectedEnemy;
    }
    
    /**
     * 设置警报范围（高级用法）
     */
    public static double getAlarmRange() {
        return ALARM_RANGE;
    }
}
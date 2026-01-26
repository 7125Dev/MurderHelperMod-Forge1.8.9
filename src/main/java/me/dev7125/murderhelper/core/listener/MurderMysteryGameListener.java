package me.dev7125.murderhelper.core.listener;

import me.dev7125.murderhelper.MurderHelperMod;
import me.dev7125.murderhelper.core.annotation.PacketListener;
import me.dev7125.murderhelper.game.KnifeThrownDetector;
import me.dev7125.murderhelper.game.BowShotDetector;
import me.dev7125.murderhelper.game.CorpseDetector;
import me.dev7125.murderhelper.game.SuspectTracker;
import net.minecraft.network.play.server.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据包监听器 - 直接通过数据包判断游戏状态和飞刀/弓箭/尸体/嫌疑人状态
 */
public class MurderMysteryGameListener {
    private final Map<String, Set<String>> teams = new HashMap<>();
    private final Set<String> teamName = new HashSet<>();

    // 飞刀检测器实例
    private final KnifeThrownDetector weaponDetector;

    // 弓箭检测器实例
    private final BowShotDetector bowDetector;

    // 尸体检测器实例
    private final CorpseDetector corpseDetector;

    // 嫌疑人追踪器实例
    private final SuspectTracker suspectTracker;

    public MurderMysteryGameListener(
            KnifeThrownDetector weaponDetector,
            BowShotDetector bowDetector,
            CorpseDetector corpseDetector,
            SuspectTracker suspectTracker) {
        this.weaponDetector = weaponDetector;
        this.bowDetector = bowDetector;
        this.corpseDetector = corpseDetector;
        this.suspectTracker = suspectTracker;
    }

    // ==================== 游戏状态检测 ====================

    @PacketListener(S3EPacketTeams.class)
    public void listenS3EPacketTeams(S3EPacketTeams packet) {
        String name = packet.getName();
        int action = packet.getAction();
        String displayName = packet.getDisplayName();
        Collection<String> players = packet.getPlayers();
        String nameTagVisibility = packet.getNameTagVisibility();

        teams.computeIfAbsent(name, k -> new HashSet<>()).addAll(players);
        if ("never".equals(nameTagVisibility)) {
            teamName.add(name);
        }

        Set<String> hideNamePlayers = teams.entrySet().stream()
                .filter(entry -> teamName.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());

        if (hideNamePlayers.contains(MurderHelperMod.playerName)) {
            // 名字被隐藏 = 游戏开始
            MurderHelperMod.gameState.onGameStart();
        } else {
            // 名字可见 = 游戏准备阶段
            MurderHelperMod.gameState.onGamePreparing();
        }
    }

    @PacketListener(S01PacketJoinGame.class)
    public void listenS01PacketJoinGame(S01PacketJoinGame packet) {
        // 收到这个数据包表示：
        // 1. 刚进入服务器 -> 应该进入 PREPARING 状态
        // 2. 被传送到新的游戏世界（上一局游戏已结束）-> 应该进入 PREPARING 状态

        MurderHelperMod.logger.info("teamName:{}", teamName);
        teams.clear();
        teamName.clear();

        // 重置游戏数据，但设置为 PREPARING 状态（而不是 IDLE）
        MurderHelperMod.gameState.onGamePreparing();
        MurderHelperMod.clearGameData();

        MurderHelperMod.logger.info("[Packet] Joined game world, entering PREPARING state");
    }

    // ==================== 飞刀和弓箭检测数据包监听 ====================

    /**
     * 监听实体装备数据包
     * 检测玩家装备/卸下飞刀和弓箭，以及盔甲架装备飞刀/尸体头部
     */
    @PacketListener(S04PacketEntityEquipment.class)
    public void listenS04PacketEntityEquipment(S04PacketEntityEquipment packet) {
        weaponDetector.handleEntityEquipment(packet);
        bowDetector.handleEntityEquipment(packet);
        corpseDetector.handleEntityEquipment(packet);
    }

    /**
     * 监听生成对象数据包
     * 检测盔甲架（飞刀投掷物/尸体）和箭矢的创建
     */
    @PacketListener(S0EPacketSpawnObject.class)
    public void listenS0EPacketSpawnObject(S0EPacketSpawnObject packet) {
        weaponDetector.handleSpawnObject(packet);
        bowDetector.handleSpawnObject(packet);
        corpseDetector.handleSpawnObject(packet);
    }

    /**
     * 监听实体元数据数据包
     * 用于检测盔甲架的特殊标记、拉弓动作和尸体标记
     */
    @PacketListener(S1CPacketEntityMetadata.class)
    public void listenS1CPacketEntityMetadata(S1CPacketEntityMetadata packet) {
        weaponDetector.handleEntityMetadata(packet);
        bowDetector.handleEntityMetadata(packet);
        corpseDetector.handleEntityMetadata(packet);
    }

    /**
     * 监听实体相对移动数据包
     * 更新飞刀投掷物和箭矢位置
     */
    @PacketListener(S14PacketEntity.S15PacketEntityRelMove.class)
    public void listenS15PacketEntityRelMove(S14PacketEntity.S15PacketEntityRelMove packet) {
        weaponDetector.handleEntityRelMove(packet);
        bowDetector.handleEntityRelMove(packet);
    }

    /**
     * 监听实体传送数据包
     * 更新飞刀投掷物和箭矢位置
     */
    @PacketListener(S18PacketEntityTeleport.class)
    public void listenS18PacketEntityTeleport(S18PacketEntityTeleport packet) {
        weaponDetector.handleEntityTeleport(packet);
        bowDetector.handleEntityTeleport(packet);
    }

    /**
     * 监听实体销毁数据包
     * 检测飞刀投掷物、箭矢和尸体消失
     */
    @PacketListener(S13PacketDestroyEntities.class)
    public void listenS13PacketDestroyEntities(S13PacketDestroyEntities packet) {
        weaponDetector.handleDestroyEntities(packet);
        bowDetector.handleDestroyEntities(packet);
        corpseDetector.handleDestroyEntities(packet);
    }

    /**
     * 监听实体属性数据包
     * 检测移动速度减少（持刀减速效果）
     */
    @PacketListener(S20PacketEntityProperties.class)
    public void listenS20PacketEntityProperties(S20PacketEntityProperties packet) {
        weaponDetector.handleEntityProperties(packet);
    }

    // ==================== 尸体检测数据包监听 ====================

    /**
     * 监听玩家生成数据包
     * 检测玩家实体尸体
     */
    @PacketListener(S0CPacketSpawnPlayer.class)
    public void listenS0CPacketSpawnPlayer(S0CPacketSpawnPlayer packet) {
        corpseDetector.handleSpawnPlayer(packet);
    }

    /**
     * 监听玩家使用床数据包
     * 确认玩家实体尸体
     */
    @PacketListener(S0APacketUseBed.class)
    public void listenS0APacketUseBed(S0APacketUseBed packet) {
        corpseDetector.handleUseBed(packet);
    }
}
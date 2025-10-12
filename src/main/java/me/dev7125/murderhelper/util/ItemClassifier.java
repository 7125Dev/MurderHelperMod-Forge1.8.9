package me.dev7125.murderhelper.util;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * 物品分类器 - 用于判断物品类型
 */
/**
 * 物品分类器 - 用于判断物品类型
 */
public class ItemClassifier {

    // 凶器列表（杀手专属武器）
    private static final Set<String> MURDER_WEAPONS = new HashSet<>();

    // 公共物品列表（所有角色都可能持有）
    private static final Set<String> COMMON_ITEMS = new HashSet<>();

    // 排除列表（包含凶器关键词但不是凶器的物品）
    private static final Set<String> EXCLUDED_ITEMS = new HashSet<>();

    static {
        // 初始化凶器列表
        MURDER_WEAPONS.add("iron_sword");      // 铁剑
        MURDER_WEAPONS.add("golden_shovel");   // 金铲
        MURDER_WEAPONS.add("diamond_pickaxe"); // 钻石镐
        MURDER_WEAPONS.add("golden_axe");      // 金斧
        MURDER_WEAPONS.add("sapling");         // 丛林树苗
        MURDER_WEAPONS.add("jungle_sapling");  // 丛林树苗
        MURDER_WEAPONS.add("book");            // 书本
        MURDER_WEAPONS.add("golden_apple");    // 金苹果
        MURDER_WEAPONS.add("speckled_melon");  // 金西瓜
        MURDER_WEAPONS.add("boat");            // 船
        MURDER_WEAPONS.add("bread");           // 面包
        MURDER_WEAPONS.add("rose_red");        // 玫瑰红
        MURDER_WEAPONS.add("dye");             // 染料（包括玫瑰红）
        MURDER_WEAPONS.add("fish");            // 生鱼
        MURDER_WEAPONS.add("salmon");          // 生鲑鱼
        MURDER_WEAPONS.add("shears");          // 剪刀
        MURDER_WEAPONS.add("diamond_hoe");     // 钻石锄
        MURDER_WEAPONS.add("diamond_sword");   // 钻石剑
        MURDER_WEAPONS.add("golden_sword");    // 金剑
        MURDER_WEAPONS.add("lapis_lazuli");    // 青金石
        MURDER_WEAPONS.add("golden_hoe");      // 金锄头
        MURDER_WEAPONS.add("record");          // 唱片
        MURDER_WEAPONS.add("cooked_chicken");  // 熟鸡肉
        MURDER_WEAPONS.add("nether_brick");    // 地狱砖
        MURDER_WEAPONS.add("netherbrick");     // 地狱砖
        MURDER_WEAPONS.add("cooked_beef");     // 熟牛排
        MURDER_WEAPONS.add("prismarine_shard"); // 海晶碎片
        MURDER_WEAPONS.add("double_plant");    // 玫瑰丛
        MURDER_WEAPONS.add("rose_bush");       // 玫瑰丛
        MURDER_WEAPONS.add("diamond_axe");     // 钻石斧
        MURDER_WEAPONS.add("cookie");          // 曲奇
        MURDER_WEAPONS.add("golden_carrot");   // 金胡萝卜
        MURDER_WEAPONS.add("bone");            // 骨头
        MURDER_WEAPONS.add("flint");           // 燧石
        MURDER_WEAPONS.add("coal");            // 煤炭
        MURDER_WEAPONS.add("charcoal");        // 木炭
        MURDER_WEAPONS.add("name_tag");        // 命名牌
        MURDER_WEAPONS.add("leather");         // 皮革
        MURDER_WEAPONS.add("golden_pickaxe");  // 金镐
        MURDER_WEAPONS.add("pumpkin_pie");     // 南瓜派
        MURDER_WEAPONS.add("quartz");          // 下界石英
        MURDER_WEAPONS.add("diamond_shovel");  // 钻石铲
        MURDER_WEAPONS.add("blaze_rod");       // 烈焰棒
        MURDER_WEAPONS.add("stone_shovel");    // 石铲
        MURDER_WEAPONS.add("reeds");           // 甘蔗
        MURDER_WEAPONS.add("sugar_cane");      // 甘蔗
        MURDER_WEAPONS.add("deadbush");        // 枯死的灌木
        MURDER_WEAPONS.add("dead_bush");       // 枯死的灌木
        MURDER_WEAPONS.add("wooden_sword");    // 木剑
        MURDER_WEAPONS.add("wood_sword");      // 木剑
        MURDER_WEAPONS.add("wooden_axe");      // 木斧
        MURDER_WEAPONS.add("wood_axe");        // 木斧
        MURDER_WEAPONS.add("stick");           // 木棍
        MURDER_WEAPONS.add("iron_shovel");     // 铁铲
        MURDER_WEAPONS.add("stone_sword");     // 石剑

        // 初始化公共物品列表
        COMMON_ITEMS.add("map");               // 地图
        COMMON_ITEMS.add("filled_map");        // 地图
        COMMON_ITEMS.add("armor_stand");       // 盔甲架
        COMMON_ITEMS.add("bed");               // 床
        COMMON_ITEMS.add("bookshelf");         // 书架
        COMMON_ITEMS.add("slime_ball");        // 粘液球
        COMMON_ITEMS.add("slimeball");         // 粘液球
        COMMON_ITEMS.add("magma_cream");       // 岩浆膏

        // 初始化排除列表（包含凶器关键词但不是凶器的物品）
        EXCLUDED_ITEMS.add("fishing_rod");           // 钓鱼竿（包含fish但不是凶器）
        EXCLUDED_ITEMS.add("bookshelf");             // 书架（包含book但不是凶器）
        EXCLUDED_ITEMS.add("book_and_quill");        // 书与笔（包含book但不是凶器）
        EXCLUDED_ITEMS.add("writable_book");         // 书与笔（包含book但不是凶器）
        EXCLUDED_ITEMS.add("enchanted_book");        // 附魔书（包含book但不是凶器）
        EXCLUDED_ITEMS.add("written_book");          // 成书（包含book但不是凶器）
        EXCLUDED_ITEMS.add("coal_ore");              // 煤矿石（包含coal但不是凶器）
        EXCLUDED_ITEMS.add("coal_block");            // 煤炭块（包含coal但不是凶器）
        EXCLUDED_ITEMS.add("carrot_on_a_stick");     // 胡萝卜钓竿（包含stick但不是凶器）
        EXCLUDED_ITEMS.add("carrot_stick");          // 胡萝卜钓竿（包含stick但不是凶器）
        EXCLUDED_ITEMS.add("sticky_piston");         // 粘性活塞（包含stick但不是凸器）
        EXCLUDED_ITEMS.add("piston_sticky");         // 粘性活塞（包含stick但不是凶器）
        EXCLUDED_ITEMS.add("blaze_powder");          // 烈焰粉（包含blaze但不是凶器）
        EXCLUDED_ITEMS.add("nether_brick_fence");    // 地狱砖栅栏（包含nether_brick但不是凶器）
        EXCLUDED_ITEMS.add("nether_brick_stairs");   // 地狱砖楼梯（包含nether_brick但不是凶器）
    }

    /**
     * 判断物品是否是弓
     */
    public static boolean isBow(ItemStack item) {
        if (item == null) return false;
        String registryName = item.getItem().getRegistryName();
        if (registryName == null) return false;

        return registryName.contains("bow");
    }

    /**
     * 判断物品是否是凶器（杀手武器）
     * 先精确匹配，再模糊匹配（排除已知的误判情况）
     */
    public static boolean isMurderWeapon(ItemStack item) {
        if (item == null) return false;
        String registryName = item.getItem().getRegistryName();
        if (registryName == null) return false;

        // 移除 minecraft: 前缀
        String itemName = registryName.replace("minecraft:", "").toLowerCase();

        // 步骤0：检查排除列表，如果在排除列表中直接返回false
        if (EXCLUDED_ITEMS.contains(itemName)) {
            return false;
        }

        // 步骤1：优先精确匹配
        if (MURDER_WEAPONS.contains(itemName)) {
            return true;
        }

        // 步骤2：模糊匹配（contains）
        for (String weapon : MURDER_WEAPONS) {
            String weaponLower = weapon.toLowerCase();
            if (itemName.contains(weaponLower)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断物品是否是公共物品
     * 先精确匹配，再模糊匹配
     */
    public static boolean isCommonItem(ItemStack item) {
        if (item == null) return false;
        String registryName = item.getItem().getRegistryName();
        if (registryName == null) return false;

        // 移除 minecraft: 前缀
        String itemName = registryName.replace("minecraft:", "").toLowerCase();

        // 步骤1：优先精确匹配
        if (COMMON_ITEMS.contains(itemName)) {
            return true;
        }

        // 步骤2：模糊匹配（contains）
        for (String common : COMMON_ITEMS) {
            String commonLower = common.toLowerCase();
            if (itemName.contains(commonLower)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 根据物品判断玩家角色（初步判断，不考虑角色锁定）
     * @param item 玩家手持的物品
     * @return 玩家角色
     */
    public static MurderHelperMod.PlayerRole determineRole(ItemStack item) {
        if (item == null) {
            // 没有手持物品 → 无法判断，返回 null
            return null;
        }

        // 有凶器 → 杀手（优先判断凶器）
        if (isMurderWeapon(item)) {
            return MurderHelperMod.PlayerRole.MURDERER;
        }

        // 有弓 → 侦探
        if (isBow(item)) {
            return MurderHelperMod.PlayerRole.DETECTIVE;
        }

        // 有公共物品 → 平民
        if (isCommonItem(item)) {
            return MurderHelperMod.PlayerRole.INNOCENT;
        }

        // 未知物品 → 返回 null，表示无法确定
        return null;
    }

    /**
     * 获取物品的类型描述（用于调试）
     */
    public static String getItemTypeDescription(ItemStack item) {
        if (item == null) return "Empty";
        if (isBow(item)) return "Bow (Detective)";
        if (isMurderWeapon(item)) return "Murder Weapon";
        if (isCommonItem(item)) return "Common Item";
        return "Unknown Item";
    }
}
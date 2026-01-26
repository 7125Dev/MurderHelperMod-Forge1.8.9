package me.dev7125.murderhelper.util;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * 物品分类器 - 用于判断物品类型
 */
public class ItemClassifier {

    public enum BowCategory {
        NORMAL_BOW, //通过金锭换来的弓
        DETECTIVE_BOW, //侦探的弓
        KALI_BOW, //kali祝福得到的弓
        NONE //没有弓箭，无辜者还没获得到弓箭
    }

    public static BowCategory getBowCategory(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return BowCategory.NONE;
        }

        if (!"minecraft:bow".equals(stack.getItem().getRegistryName())) {
            return BowCategory.NONE;
        }

        if (!stack.hasTagCompound()) {
            return BowCategory.NORMAL_BOW;
        }

        NBTTagCompound nbt = stack.getTagCompound();

        /* ① Kali Bow：Infinity 是绝对特征 */
        if (nbt.hasKey("ench", Constants.NBT.TAG_LIST)) {
            NBTTagList enchList = nbt.getTagList("ench", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < enchList.tagCount(); i++) {
                NBTTagCompound ench = enchList.getCompoundTagAt(i);
                if (ench.hasKey("id", Constants.NBT.TAG_SHORT)
                        && ench.getShort("id") == 51) {
                    return BowCategory.KALI_BOW;
                }
            }
        }

        /* ② Lore 判断（插件差异只体现在这里） */
        if (nbt.hasKey("display", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound display = nbt.getCompoundTag("display");

            if (display.hasKey("Lore", Constants.NBT.TAG_LIST)) {
                NBTTagList loreList =
                        display.getTagList("Lore", Constants.NBT.TAG_STRING);

                int loreSize = loreList.tagCount();

                if (loreSize == 1) {
                    return BowCategory.DETECTIVE_BOW;
                }
                if (loreSize == 2) {
                    return BowCategory.NORMAL_BOW;
                }
            }
        }

        /* ③ 兜底：什么都不满足 */
        return BowCategory.NORMAL_BOW;
    }



    public static boolean isMurderWeapon(ItemStack item) {
        if (item == null || !item.hasTagCompound()) {
            return false;
        }

        NBTTagCompound nbt = item.getTagCompound();

        // 首先判断是否有 ExtraAttributes.MELEE 标识
        if (nbt.hasKey("ExtraAttributes", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound extraAttr = nbt.getCompoundTag("ExtraAttributes");
            if (extraAttr.hasKey("MELEE", Constants.NBT.TAG_BYTE)) {
                //MELEE=1是武器，MELEE=0是kali诅咒之剑
                return extraAttr.getBoolean("MELEE");
            }
        }

        // 接着判断如果是 minecraft:iron_sword 并且有 display 的 NBT
        if (item.getItem().getRegistryName().equals("minecraft:iron_sword")) {
            printItemStackInfo(item);
            if (nbt.hasKey("display", Constants.NBT.TAG_COMPOUND)) {
                return true;
            }
        }

        // 其他情况返回 false
        return false;
    }

    // 打印完整的ItemStack信息
    private static void printItemStackInfo(ItemStack item) {
        if (item == null) {
            MurderHelperMod.logger.info("ItemStack: null");
            return;
        }

        MurderHelperMod.logger.info("=== ItemStack Info ===");
        MurderHelperMod.logger.info("Item: {}", item.getItem().getRegistryName());
        MurderHelperMod.logger.info("Count: {}", item.stackSize);
        MurderHelperMod.logger.info("Damage: {}", item.getItemDamage());
        MurderHelperMod.logger.info("Display Name: {}", item.getDisplayName());

        // 打印完整NBT
        NBTTagCompound nbt = item.getTagCompound();
        if (nbt != null) {
            MurderHelperMod.logger.info("Full NBT: {}", nbt.toString());

            // 打印display标签（包含Name和Lore）
            if (nbt.hasKey("display", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound display = nbt.getCompoundTag("display");

                // 打印Name
                if (display.hasKey("Name", Constants.NBT.TAG_STRING)) {
                    MurderHelperMod.logger.info("Custom Name: {}", display.getString("Name"));
                }

                // 打印Lore
                if (display.hasKey("Lore", Constants.NBT.TAG_LIST)) {
                    NBTTagList lore = display.getTagList("Lore", Constants.NBT.TAG_STRING);
                    MurderHelperMod.logger.info("Lore ({} lines):", lore.tagCount());
                    for (int i = 0; i < lore.tagCount(); i++) {
                        MurderHelperMod.logger.info("  [{}] {}", i, lore.getStringTagAt(i));
                    }
                }
            }

            // 打印其他常见标签
            if (nbt.hasKey("ExtraAttributes", Constants.NBT.TAG_COMPOUND)) {
                MurderHelperMod.logger.info("ExtraAttributes: {}", nbt.getCompoundTag("ExtraAttributes"));
            }

            if (nbt.hasKey("Unbreakable", Constants.NBT.TAG_BYTE)) {
                MurderHelperMod.logger.info("Unbreakable: {}", nbt.getBoolean("Unbreakable"));
            }

            if (nbt.hasKey("HideFlags", Constants.NBT.TAG_INT)) {
                MurderHelperMod.logger.info("HideFlags: {}", nbt.getInteger("HideFlags"));
            }
        } else {
            MurderHelperMod.logger.info("NBT: null");
        }
        MurderHelperMod.logger.info("=====================");
    }
}
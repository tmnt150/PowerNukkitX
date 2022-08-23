package cn.nukkit.block;

import cn.nukkit.api.PowerNukkitOnly;
import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.blockproperty.BlockProperties;
import cn.nukkit.blockproperty.value.WoodType;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public class BlockMangroveLeaves extends BlockLeaves{

    public static final BlockProperties PROPERTIES = new BlockProperties(PERSISTENT, UPDATE);

    @Override
    public String getName() {
        return "Mangrove Leaves";
    }

    @Override
    public int getId() {
        return MANGROVE_LEAVES;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShears()) {
            return new Item[]{
                    toItem()
            };
        }

        List<Item> drops = new ArrayList<>(1);
        Enchantment fortuneEnchantment = item.getEnchantment(Enchantment.ID_FORTUNE_DIGGING);

        int fortune = fortuneEnchantment != null ? fortuneEnchantment.getLevel() : 0;
        int appleOdds;
        int stickOdds;
        switch (fortune) {
            case 0 -> {
                appleOdds = 200;
                stickOdds = 50;
            }
            case 1 -> {
                appleOdds = 180;
                stickOdds = 45;
            }
            case 2 -> {
                appleOdds = 160;
                stickOdds = 40;
            }
            default -> {
                appleOdds = 120;
                stickOdds = 30;
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (canDropApple() && random.nextInt(appleOdds) == 0) {
            drops.add(Item.get(ItemID.APPLE));
        }
        if (random.nextInt(stickOdds) == 0) {
            drops.add(Item.get(ItemID.STICK));
        }
        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item) {
        //todo: 实现红树树叶催化
        return true;
    }

    @Since("1.4.0.0-PN")
    @PowerNukkitOnly
    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    /*这里写木质类型为OAK只是为了获取凋落物时的概率正确，并不代表真的就是橡木*/
    @Override
    public WoodType getType() {
        return WoodType.OAK;
    }
}

package xyz.nucleoid.plasmid.fake;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

public class FakeBlockItem extends Item implements FakeItem {

    private final Block block;
    private final Item proxyItem;

    public FakeBlockItem(Block block, Item proxyItem, Settings settings) {
        super(settings);
        this.block = block;
        this.proxyItem = proxyItem;
    }

    @Override
    public Item asProxy() {
        return this.proxyItem;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        context.getWorld().setBlockState(context.getBlockPos(), this.block.getDefaultState());
        return ActionResult.SUCCESS;
    }
}

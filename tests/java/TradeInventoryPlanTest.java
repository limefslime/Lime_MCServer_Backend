import java.util.List;
import net.minecraft.world.item.ItemStack;
import com.namanseul.farmingmod.server.shop.TradeInventoryPlan;

public class TradeInventoryPlanTest {
    private static ItemStack stack(String components, int count, int max) { return new ItemStack("fish",components,count,max); }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        ItemStack original = stack("named",60,64), empty = stack("",0,64), returning = stack("named",10,64);
        var planned = TradeInventoryPlan.insert(List.of(original,empty),List.of(returning));
        check(planned.get(0).getCount()==64 && planned.get(1).getCount()==6);
        check(original.getCount()==60 && returning.getCount()==10 && empty.isEmpty());
        ItemStack different = stack("enchanted",10,64);
        planned = TradeInventoryPlan.insert(List.of(original,empty),List.of(different));
        check(planned.get(0).getCount()==60 && planned.get(1).getCount()==10);
        check(ItemStack.isSameItemSameComponents(planned.get(1),different));
        check(TradeInventoryPlan.insert(List.of(original),List.of(returning))==null);
        check(original.getCount()==60 && returning.getCount()==10);
        check(TradeInventoryPlan.insert(List.of(empty),List.of(returning,different))==null);
        check(empty.isEmpty() && returning.getCount()==10 && different.getCount()==10);
        planned = TradeInventoryPlan.insert(List.of(empty,empty),List.of(stack("rod",2,1)));
        check(planned.get(0).getCount()==1 && planned.get(1).getCount()==1);
        check(TradeInventoryPlan.insert(List.of(empty),List.of(stack("rod",2,1)))==null);
        check(TradeInventoryPlan.insert(List.of(original),List.of()).get(0).getCount()==60);
        System.out.println("Inventory planning passed (fixture ItemStack)");
    }
}

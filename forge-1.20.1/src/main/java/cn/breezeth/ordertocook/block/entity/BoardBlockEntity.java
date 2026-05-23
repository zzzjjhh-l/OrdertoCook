package cn.breezeth.ordertocook.block.entity;

import cn.breezeth.ordertocook.config.ConfigManager;
import cn.breezeth.ordertocook.registry.ModBlockEntities;
import cn.breezeth.ordertocook.util.ImplementedInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;


public class BoardBlockEntity extends BlockEntity implements MenuProvider, ImplementedInventory {
    private final NonNullList<ItemStack> templates = NonNullList.withSize(150, ItemStack.EMPTY);
    private boolean defaultsInitialized = false;
    private int sortMode = 0; // 0: by nutrition desc, 1: by id asc then nutrition desc, 2: by last-set-time desc

    public BoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD.get(), pos, state);
        applyDefaultTemplates();
        defaultsInitialized = true;
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return templates;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        ContainerHelper.saveAllItems(nbt, templates);
        nbt.putBoolean("DefaultsInitialized", defaultsInitialized);
        nbt.putInt("SortMode", sortMode);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        ContainerHelper.loadAllItems(nbt, templates);
        defaultsInitialized = nbt.getBoolean("DefaultsInitialized");
        sortMode = nbt.contains("SortMode") ? nbt.getInt("SortMode") : 0;
        if (!defaultsInitialized && isTemplatesEmpty()) {
            applyDefaultTemplates();
            defaultsInitialized = true;
        }
        compactAndSort();
    }

    public CompoundTag toCompactItemNbt() {
        CompoundTag out = new CompoundTag();
        out.putBoolean("DefaultsInitialized", defaultsInitialized);
        out.putInt("SortMode", sortMode);
        ListTag list = new ListTag();
        ListTag times = new ListTag();
        HashSet<String> seen = new HashSet<>();
        for (ItemStack s : templates) {
            if (s.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            if (seen.add(id)) {
                list.add(StringTag.valueOf(id));
                times.add(LongTag.valueOf(lastSetTimeOf(s)));
            }
        }
        out.put("OtcBoardTemplateIds", list);
        out.put("OtcBoardTemplateTimes", times);
        return out;
    }

    public void applyFromCompactItemNbt(CompoundTag nbt) {
        if (nbt == null) return;
        if (nbt.contains("SortMode")) sortMode = nbt.getInt("SortMode");
        defaultsInitialized = true;
        for (int i = 0; i < templates.size(); i++) {
            templates.set(i, ItemStack.EMPTY);
        }
        if (nbt.contains("OtcBoardTemplateIds", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("OtcBoardTemplateIds", Tag.TAG_STRING);
            ListTag times = nbt.contains("OtcBoardTemplateTimes", Tag.TAG_LIST)
                    ? nbt.getList("OtcBoardTemplateTimes", Tag.TAG_LONG) : null;
            HashSet<ResourceLocation> seen = new HashSet<>();
            int write = 0;
            for (int i = 0; i < list.size() && write < templates.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id == null) continue;
                if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
                if (!seen.add(id)) continue;
                Item item = BuiltInRegistries.ITEM.get(id);
                ItemStack stack = new ItemStack(item);
                stack.setCount(1);
                long ts = 0L;
                if (times != null && i < times.size()) {
                    ts = ((LongTag) times.get(i)).getAsLong();
                }
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.putLong("OtcLastSetTime", ts);
                cn.breezeth.ordertocook.util.DataCompat.set(stack, tag);
                templates.set(write++, stack);
            }
        }
        if (isTemplatesEmpty()) {
            defaultsInitialized = false;
            applyDefaultTemplates();
            defaultsInitialized = true;
        } else {
            compactAndSort();
        }
        setChanged();
    }

    private void applyDefaultTemplates() {
        templates.set(0, new ItemStack(Items.COOKED_PORKCHOP));
        templates.set(1, new ItemStack(Items.COOKED_CHICKEN));
        templates.set(2, new ItemStack(Items.COOKED_MUTTON));
        templates.set(3, new ItemStack(Items.BREAD));
        compactAndSort();
    }

    private boolean isTemplatesEmpty() {
        for (ItemStack s : templates) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    private static final java.util.List<String> RTDD_FOOD_KEYWORDS = java.util.List.of(
            "plate", "bowl", "meat", "noodle", "juice", "dumplings", "soup", "cake", "bamboo",
            "fish", "rice", "chicken", "beef", "pork", "shrimp", "prawn", "mushroom",
            "egg", "tofu", "tea", "milk", "fruit", "stew", "bread", "vegetable",
            "sweet", "pie", "cookie", "icecream", "sushi", "curry", "pizza", "burger",
            "sandwich", "salad", "porridge", "pancake", "waffle", "pudding", "yogurt",
            "cheese", "butter", "cream", "chocolate", "coffee", "wine", "beer", "sauce",
            "baijiu", "liquor", "vodka", "whiskey", "rum", "cocktail", "sake",
            "greentea", "blacktea", "milktea", "herbal", "oolong", "matcha", "latte",
            "pastry", "mooncake", "onigiri", "riceball", "bun", "baozi", "mantou",
            "zongzi", "tangyuan", "shaomai", "shumai", "springroll", "wonton",
            "hotpot", "bbq", "barbecue", "grill", "skewer", "kebab",
            "ramen", "udon", "soba", "pasta", "spaghetti", "friedrice", "chowmein",
            "donburi", "congee", "soymilk", "youtiao", "potsticker", "gyoza",
            "xiaolongbao", "shengjian", "scallion", "mala", "spicy", "snack",
            "wellington", "steak", "filet", "ribeye", "tenderloin", "wagyu", "lamb",
            "lobster", "caviar", "truffle", "foiegras", "escargot", "oyster",
            "baguette", "croissant", "crepe", "quiche", "fondue", "souffle",
            "mousse", "cremebrulee", "eclair", "macaron", "tartare", "bearnaise",
            "bagel", "pretzel", "brioche", "focaccia", "ciabatta", "sourdough",
            "risotto", "gnocchi", "bruschetta", "carpaccio", "prosciutto", "tiramisu",
            "champagne", "brandy", "cognac", "portwine"
    );

    private static final java.util.LinkedHashMap<String, Integer> RTDD_NUTRITION_MAP = new java.util.LinkedHashMap<>() {{
        put("wellington", 9);
        put("wagyu", 9);
        put("caviar", 9);
        put("foiegras", 9);
        put("truffle", 9);
        put("beef", 8);
        put("meat", 8);
        put("hotpot", 8);
        put("steak", 8);
        put("filet", 8);
        put("ribeye", 8);
        put("tenderloin", 8);
        put("lobster", 8);
        put("lamb", 8);
        put("pork", 7);
        put("burger", 7);
        put("pizza", 7);
        put("chicken", 7);
        put("curry", 7);
        put("bbq", 7);
        put("barbecue", 7);
        put("grill", 7);
        put("oyster", 7);
        put("escargot", 6);
        put("stew", 6);
        put("fish", 6);
        put("shrimp", 6);
        put("prawn", 6);
        put("dumplings", 6);
        put("cake", 6);
        put("pie", 6);
        put("sandwich", 6);
        put("skewer", 6);
        put("kebab", 6);
        put("xiaolongbao", 6);
        put("shengjian", 6);
        put("gyoza", 6);
        put("potsticker", 6);
        put("tartare", 6);
        put("carpaccio", 6);
        put("prosciutto", 6);
        put("risotto", 5);
        put("gnocchi", 5);
        put("sweet", 5);
        put("chocolate", 5);
        put("noodle", 5);
        put("rice", 5);
        put("bread", 5);
        put("pancake", 5);
        put("waffle", 5);
        put("pudding", 5);
        put("sushi", 5);
        put("ramen", 5);
        put("udon", 5);
        put("soba", 5);
        put("pasta", 5);
        put("spaghetti", 5);
        put("friedrice", 5);
        put("chowmein", 5);
        put("donburi", 5);
        put("onigiri", 5);
        put("riceball", 5);
        put("mooncake", 5);
        put("pastry", 5);
        put("springroll", 5);
        put("shaomai", 5);
        put("shumai", 5);
        put("wonton", 5);
        put("baozi", 5);
        put("baguette", 5);
        put("focaccia", 5);
        put("ciabatta", 5);
        put("croissant", 4);
        put("brioche", 4);
        put("sourdough", 4);
        put("bagel", 4);
        put("pretzel", 4);
        put("crepe", 4);
        put("quiche", 4);
        put("bruschetta", 4);
        put("bun", 4);
        put("egg", 4);
        put("tofu", 4);
        put("cheese", 4);
        put("butter", 4);
        put("cream", 4);
        put("plate", 4);
        put("bowl", 4);
        put("cookie", 4);
        put("yogurt", 4);
        put("porridge", 4);
        put("congee", 4);
        put("mantou", 4);
        put("zongzi", 4);
        put("tangyuan", 4);
        put("youtiao", 4);
        put("scallion", 4);
        put("fondue", 4);
        put("souffle", 4);
        put("bearnaise", 4);
        put("mushroom", 3);
        put("bamboo", 3);
        put("vegetable", 3);
        put("fruit", 3);
        put("salad", 3);
        put("milk", 3);
        put("coffee", 3);
        put("sauce", 3);
        put("soup", 3);
        put("milktea", 3);
        put("latte", 3);
        put("soymilk", 3);
        put("mala", 3);
        put("spicy", 3);
        put("snack", 3);
        put("mousse", 3);
        put("cremebrulee", 3);
        put("eclair", 3);
        put("macaron", 3);
        put("tiramisu", 3);
        put("tea", 2);
        put("greentea", 2);
        put("blacktea", 2);
        put("oolong", 2);
        put("matcha", 2);
        put("herbal", 2);
        put("juice", 2);
        put("wine", 2);
        put("beer", 2);
        put("icecream", 2);
        put("sake", 2);
        put("champagne", 2);
        put("baijiu", 1);
        put("liquor", 1);
        put("vodka", 1);
        put("whiskey", 1);
        put("rum", 1);
        put("cocktail", 1);
        put("brandy", 1);
        put("cognac", 1);
        put("portwine", 1);
    }};

    public static boolean isRtddFoodItem(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath();
        if (!path.startsWith("rtdd")) return false;
        for (String keyword : RTDD_FOOD_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static int getRtddNutrition(ResourceLocation id) {
        if (id == null) return 0;
        String path = id.getPath();
        int best = 0;
        for (var entry : RTDD_NUTRITION_MAP.entrySet()) {
            if (path.contains(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best > 0 ? best : 4;
    }

    public boolean tryAddTemplate(ItemStack source) {
        if (source.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(source.getItem());
        boolean isRtddFood = isRtddFoodItem(id);
        if (!isRtddFood && nutritionOf(source) <= 0) return false;
        Item item = source.getItem();
        for (ItemStack s : templates) {
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(id)) {
                return false;
            }
        }
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).isEmpty()) {
                ItemStack copy = source.copy();
                copy.setCount(1);
                net.minecraft.nbt.CompoundTag tag = cn.breezeth.ordertocook.util.DataCompat.copy(copy);
                if (tag == null) tag = new net.minecraft.nbt.CompoundTag();
                tag.putLong("OtcLastSetTime", System.currentTimeMillis());
                cn.breezeth.ordertocook.util.DataCompat.set(copy, tag);
                templates.set(i, copy);
                compactAndSort();
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void removeAt(int absoluteIndex) {
        if (absoluteIndex < 0 || absoluteIndex >= templates.size()) return;
        templates.set(absoluteIndex, ItemStack.EMPTY);
        compactAndSort();
        setChanged();
    }

    public int getSortMode() {
        return sortMode;
    }

    public void setSortMode(int mode) {
        int next = (mode == 1) ? 1 : (mode == 2 ? 2 : 0);
        if (sortMode == next) return;
        sortMode = next;
        compactAndSort();
        setChanged();
    }

    public void toggleSortMode() {
        setSortMode(sortMode == 0 ? 1 : (sortMode == 1 ? 2 : 0));
    }

    private int nutritionOf(ItemStack s) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (isRtddFoodItem(id)) {
            return getRtddNutrition(id);
        }
        FoodProperties fc = s.getItem().getFoodProperties();
        if (fc != null && fc.getNutrition() > 0) {
            return fc.getNutrition();
        }
        return ConfigManager.getCustomMenuNutrition(s);
    }

    private String idOf(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    private long lastSetTimeOf(ItemStack s) {
        net.minecraft.nbt.CompoundTag tag = cn.breezeth.ordertocook.util.DataCompat.copy(s);
        if (tag == null) return 0L;
        return tag.contains("OtcLastSetTime") ? tag.getLong("OtcLastSetTime") : 0L;
    }

    public int getTotalNutrition() {
        int total = 0;
        for (ItemStack s : templates) {
            if (s.isEmpty()) continue;
            total += nutritionOf(s);
        }
        return total;
    }

    private void compactAndSort() {
        int write = 0;
        for (int read = 0; read < templates.size(); read++) {
            ItemStack s = templates.get(read);
            if (!s.isEmpty()) {
                if (read != write) {
                    templates.set(write, s);
                    templates.set(read, ItemStack.EMPTY);
                }
                write++;
            }
        }
        templates.sort(new Comparator<ItemStack>() {
            @Override
            public int compare(ItemStack a, ItemStack b) {
                boolean ea = a.isEmpty();
                boolean eb = b.isEmpty();
                if (ea && eb) return 0;
                if (ea) return 1;
                if (eb) return -1;
                if (sortMode == 2) {
                    return Long.compare(lastSetTimeOf(b), lastSetTimeOf(a));
                } else if (sortMode == 1) {
                    int byId = idOf(a).compareTo(idOf(b));
                    if (byId != 0) return byId;
                    return Integer.compare(nutritionOf(b), nutritionOf(a));
                }
                int byNut = Integer.compare(nutritionOf(b), nutritionOf(a));
                if (byNut != 0) return byNut;
                return idOf(a).compareTo(idOf(b));
            }
        });
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ordertocook.board");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new cn.breezeth.ordertocook.screen.BoardScreenHandler(syncId, playerInventory, this);
    }
}

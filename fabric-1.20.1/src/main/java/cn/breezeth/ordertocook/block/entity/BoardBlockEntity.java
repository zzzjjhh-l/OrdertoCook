package cn.breezeth.ordertocook.block.entity;

import cn.breezeth.ordertocook.config.ConfigManager;
import cn.breezeth.ordertocook.registry.ModBlockEntities;
import cn.breezeth.ordertocook.util.ImplementedInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtLong;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;


public class BoardBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
    private final DefaultedList<ItemStack> templates = DefaultedList.ofSize(150, ItemStack.EMPTY);
    private boolean defaultsInitialized = false;
    private int sortMode = 0;

    public BoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOARD, pos, state);
        applyDefaultTemplates();
        defaultsInitialized = true;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return templates;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, templates);
        nbt.putBoolean("DefaultsInitialized", defaultsInitialized);
        nbt.putInt("SortMode", sortMode);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, templates);
        defaultsInitialized = nbt.getBoolean("DefaultsInitialized");
        sortMode = nbt.contains("SortMode") ? nbt.getInt("SortMode") : 0;
        if (!defaultsInitialized && isTemplatesEmpty()) {
            applyDefaultTemplates();
            defaultsInitialized = true;
        }
        compactAndSort();
    }

    public NbtCompound toCompactItemNbt() {
        NbtCompound out = new NbtCompound();
        out.putBoolean("DefaultsInitialized", defaultsInitialized);
        out.putInt("SortMode", sortMode);
        NbtList list = new NbtList();
        NbtList times = new NbtList();
        HashSet<String> seen = new HashSet<>();
        for (ItemStack s : templates) {
            if (s.isEmpty()) continue;
            String id = Registries.ITEM.getId(s.getItem()).toString();
            if (seen.add(id)) {
                list.add(NbtString.of(id));
                times.add(NbtLong.of(lastSetTimeOf(s)));
            }
        }
        out.put("OtcBoardTemplateIds", list);
        out.put("OtcBoardTemplateTimes", times);
        return out;
    }

    public void applyFromCompactItemNbt(NbtCompound nbt) {
        if (nbt == null) return;
        if (nbt.contains("SortMode")) sortMode = nbt.getInt("SortMode");
        defaultsInitialized = true;
        for (int i = 0; i < templates.size(); i++) {
            templates.set(i, ItemStack.EMPTY);
        }
        if (nbt.contains("OtcBoardTemplateIds", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("OtcBoardTemplateIds", NbtElement.STRING_TYPE);
            NbtList times = nbt.contains("OtcBoardTemplateTimes", NbtElement.LIST_TYPE)
                    ? nbt.getList("OtcBoardTemplateTimes", NbtElement.LONG_TYPE) : null;
            HashSet<Identifier> seen = new HashSet<>();
            int write = 0;
            for (int i = 0; i < list.size() && write < templates.size(); i++) {
                Identifier id = Identifier.tryParse(list.getString(i));
                if (id == null) continue;
                if (!Registries.ITEM.containsId(id)) continue;
                if (!seen.add(id)) continue;
                Item item = Registries.ITEM.get(id);
                ItemStack stack = new ItemStack(item);
                stack.setCount(1);
                long ts = 0L;
                if (times != null && i < times.size()) {
                    ts = ((NbtLong) times.get(i)).longValue();
                }
                NbtCompound tag = stack.getNbt();
                if (tag == null) tag = new NbtCompound();
                tag.putLong("OtcLastSetTime", ts);
                stack.setNbt(tag);
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
        markDirty();
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

    public static boolean isRtddFoodItem(Identifier id) {
        if (id == null) return false;
        String namespace = id.getNamespace();
        String path = id.getPath();
        boolean isRtdd = namespace.equals("rtdd") || path.startsWith("rtdd");
        if (!isRtdd) return false;
        for (String keyword : RTDD_FOOD_KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static int getRtddNutrition(Identifier id) {
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
        Identifier id = Registries.ITEM.getId(source.getItem());
        boolean isRtddFood = isRtddFoodItem(id);
        if (!isRtddFood && nutritionOf(source) <= 0) return false;
        Item item = source.getItem();
        for (ItemStack s : templates) {
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).equals(id)) {
                return false;
            }
        }
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).isEmpty()) {
                ItemStack copy = source.copy();
                copy.setCount(1);
                NbtCompound tag = copy.getNbt();
                if (tag == null) tag = new NbtCompound();
                tag.putLong("OtcLastSetTime", System.currentTimeMillis());
                copy.setNbt(tag);
                templates.set(i, copy);
                compactAndSort();
                markDirty();
                return true;
            }
        }
        return false;
    }

    public void removeAt(int absoluteIndex) {
        if (absoluteIndex < 0 || absoluteIndex >= templates.size()) return;
        templates.set(absoluteIndex, ItemStack.EMPTY);
        compactAndSort();
        markDirty();
    }

    public int getSortMode() {
        return sortMode;
    }

    public void setSortMode(int mode) {
        int next = (mode == 1) ? 1 : (mode == 2 ? 2 : 0);
        if (sortMode == next) return;
        sortMode = next;
        compactAndSort();
        markDirty();
    }

    public void toggleSortMode() {
        setSortMode(sortMode == 0 ? 1 : (sortMode == 1 ? 2 : 0));
    }

    private int nutritionOf(ItemStack s) {
        Identifier id = Registries.ITEM.getId(s.getItem());
        if (isRtddFoodItem(id)) {
            return getRtddNutrition(id);
        }
        FoodComponent fc = s.getItem().getFoodComponent();
        if (fc != null && fc.getHunger() > 0) {
            return fc.getHunger();
        }
        return ConfigManager.getCustomMenuNutrition(s);
    }

    private String idOf(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }

    private long lastSetTimeOf(ItemStack s) {
        NbtCompound tag = s.getNbt();
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
    public Text getDisplayName() {
        return Text.translatable("block.ordertocook.board");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new cn.breezeth.ordertocook.screen.BoardScreenHandler(syncId, playerInventory, this);
    }
}

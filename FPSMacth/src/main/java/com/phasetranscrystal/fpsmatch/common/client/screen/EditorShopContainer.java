package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.util.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.common.item.EditorShopCapabilityProvider;
import com.phasetranscrystal.fpsmatch.common.item.ShopEditTool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;


import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class EditorShopContainer extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private static final int ROWS = EditorShopCapabilityProvider.ROWS;
    private static final int COLS = EditorShopCapabilityProvider.COLS;
    private static final int d = 10; // 设定间隔
    public static final int PLAYER_INV_START = ROWS * COLS;
    public static final int PLAYER_HOTBAR_END = ROWS * COLS + 36;
    private static final int CUSTOM_CONTAINER_START = 0;
    private static final int CUSTOM_CONTAINER_END = ROWS * COLS - 1;
    private final ItemStack guiItemStack; // 存储打开 GUI 的物品
    private final ItemStackHandler itemStackHandler;
    private List<ShopSlot> shopSlots = new ArrayList<>();


    public EditorShopContainer(int containerId, Inventory playerInventory,FriendlyByteBuf buf)
    {
        this(containerId, playerInventory, buf.readItem());
    }
    public EditorShopContainer(int containerId, Inventory playerInventory, ItemStack stack) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);
        this.guiItemStack = stack;
        this.itemStackHandler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .filter(h -> h instanceof ItemStackHandler) // 确保是 ItemStackHandler
                .map(h -> (ItemStackHandler) h) // 强制转换
                .orElse(new ItemStackHandler(5 * 5)); // 默认提供一个空的 25 格存储

        this.shopSlots = this.getAllSlots();
        int startX = (176 - (COLS * (SLOT_SIZE + 4 * d) - d)) / 2; // 居中于默认 GUI 宽度 -d微调原理还不清楚
        int startY = 18;

        // 创建 5×5 格子并加入间隔
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                ItemStack slotItem = this.shopSlots.get(col + row * COLS).process();
                this.addSlot(new SlotItemHandler(
                                        itemStackHandler,
                                        col + row * COLS,
                                        startX + col * (SLOT_SIZE + 4 * d), // **加上间隔 d**
                                        startY + row * (SLOT_SIZE + d)  // **加上间隔 d**
                                )
                        )//从框架读取值
                        .set(slotItem.isEmpty() ? ItemStack.EMPTY : slotItem)
                ;
            }
        }

        // **玩家物品栏（下移，避免与 GUI 重叠）**
        addPlayerInventory(playerInventory, (176 - 9 * SLOT_SIZE - 4 * d) / 2, 163);// 居中于默认 GUI 宽度 -4d微调原理还不清楚
    }


    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
        }
    }


    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    //打开二级菜单

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotIndex, int button, @NotNull ClickType clickType, @NotNull Player player) {
        boolean isCustomContainer = slotIndex >= CUSTOM_CONTAINER_START && slotIndex <= CUSTOM_CONTAINER_END;
        if (isCustomContainer) {
            this.openSecondMenu(player, this.shopSlots.get(slotIndex), slotIndex);
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    //关闭GUI时数据保存
    @Override
    public void removed(@NotNull Player pPlayer) {
        super.removed(pPlayer);
        if (pPlayer instanceof ServerPlayer) {
            guiItemStack.getOrCreateTag().put("ShopItems", itemStackHandler.serializeNBT());
        }
    }

    private FPSMShop<? extends Enum<?>> getShop() {
        if (guiItemStack.getItem() instanceof ShopEditTool shopEditTool) {
            BaseMap map = FPSMCore.getInstance().getMapByName(shopEditTool.getTag(guiItemStack, ShopEditTool.MAP_TAG));
            if (map instanceof ShopMap<?> shopMap) {
                return shopMap.getShop(shopEditTool.getTag(guiItemStack, ShopEditTool.SHOP_TAG)).orElse(null);
            }
        }
        return null;
    }

    public List<ShopSlot> getAllSlots() {
        //遍历 0 到 maxRow - 1 的索引，模拟逐行读取数据
        return IntStream.range(0,
                        Objects.requireNonNull(this.getShop()).getDefaultShopDataMap().values().stream()
                                .mapToInt(List::size)
                                .max().orElse(0))  // 获取最大行数
                //按列顺序遍历行
                .mapToObj(row -> this.getShop().getDefaultShopDataMap().entrySet().stream()//按列创建流
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal())) // 确保列顺序
                        .map(Map.Entry::getValue)
                        .filter(slotList -> row < slotList.size())  // 过滤掉短列 【遗留问题，是否存在占位符？】
                        .map(slotList -> slotList.get(row)))  // 取出当前行的元素
                .flatMap(Function.identity())  // 展开所有元素
                .toList();  // 转换成 List<ShopSlot>
    }

    private void openSecondMenu(Player player, ShopSlot shopSlot, int repoIndex) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new EditShopSlotMenu(windowId, inv, shopSlot, guiItemStack, repoIndex),
                            Component.translatable("gui.fpsm.edit_shop_slot.title")
                    ),
                    buf -> {
                        // 在服务器端通过 buf 写入数据，传递给客户端
                        String json = new Gson().toJson(FPSMCodec.encodeToJson(ShopSlot.CODEC,shopSlot));
                        buf.writeUtf(json); // 写入 JSON 数据
                        buf.writeItem(guiItemStack);
                        buf.writeInt(repoIndex);
                    }
            );
        }
    }

}

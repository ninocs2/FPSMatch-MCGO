package com.phasetranscrystal.fpsmatch.core.shop.event;

import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.server.level.ServerPlayer;


// ShopSlot内部调用 不传Forge事件
public class ShopSlotChangeEvent{
    public final ShopSlot shopSlot;
    public final ServerPlayer player;
    public final int flag;
    private int money;
    private boolean cancelLogic = false;

    /**
     * @param player 玩家
     * @param flag 变化标志。
     * @apiNote flag 规范：>0表示购入，<0表示返回。数值对应数量。*/
    public ShopSlotChangeEvent(ShopSlot shopSlot, ServerPlayer player,int money, int flag) {
        this.shopSlot = shopSlot;
        this.player = player;
        this.money = money;
        this.flag = flag;
    }

    public void setCancelLogic(boolean cancelLogic){
        this.cancelLogic = cancelLogic;
    }

    public boolean isCancelLogic(){
        return cancelLogic;
    }


    public void addMoney(int count){
        this.money += count;
    }

    public void removeMoney(int count){
        this.money -= count;
    }

    public int getMoney() {
        return money;
    }
}
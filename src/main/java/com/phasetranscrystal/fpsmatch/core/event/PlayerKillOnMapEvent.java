package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 玩家在地图中击杀其他玩家的事件
 * 用于通知其他系统处理击杀相关逻辑，如：
 * - 更新击杀数据统计
 * - 触发击杀音效
 * - 更新记分板
 * - 计算MVP数据
 */
public class PlayerKillOnMapEvent extends Event {
    private final BaseMap map;        // 事件发生的地图
    private final ServerPlayer dead;   // 被击杀的玩家
    private final ServerPlayer killer; // 击杀者

    /**
     * 创建一个新的击杀事件
     * @param map 事件发生的地图实例
     * @param dead 被击杀的玩家实例
     * @param killer 击杀者实例
     */
    public PlayerKillOnMapEvent(BaseMap map,ServerPlayer dead,ServerPlayer killer){
        this.map = map;
        this.dead = dead;
        this.killer = killer;
    }

    /**
     * 此事件不可取消
     * 因为击杀事件是已经发生的事实，不应该被取消
     * @return false 表示事件不可取消
     */
    @Override
    public boolean isCancelable()
    {
        return false;
    }

    /**
     * 获取事件发生的地图
     * @return 地图实例
     */
    public BaseMap getBaseMap() {
        return map;
    }

    /**
     * 获取被击杀的玩家
     * @return 被击杀的玩家实例
     */
    public ServerPlayer getDead(){
        return dead;
    }

    /**
     * 获取击杀者
     * @return 击杀者实例
     */
    public ServerPlayer getKiller() {
        return killer;
    }
}

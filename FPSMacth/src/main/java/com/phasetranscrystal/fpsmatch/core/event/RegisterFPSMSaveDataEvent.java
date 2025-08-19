package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import net.minecraftforge.eventbus.api.Event;

/**
 * 注册 FPSMatch 可保存数据类型的事件。
 */
public class RegisterFPSMSaveDataEvent extends Event {
    private final FPSMDataManager dataManager;

    public RegisterFPSMSaveDataEvent(FPSMDataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * 使用数据管理器注册存档数据。
     *
     * @param clazz 被保存数据的类
     * @param folderName 数据将被保存的文件夹名称
     * @param iSavedData 包装后的数据处理层
     */
    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> iSavedData) {
        this.dataManager.registerData(clazz, folderName, iSavedData);
    }

}
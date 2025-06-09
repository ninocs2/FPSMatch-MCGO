package com.phasetranscrystal.fpsmatch.mcgo.config;

public class APIConfig {
    private String apiEndpoint;
    private String saveMatch;
    private String weaponConfigure;
    private String apiAuthHeader;
    private String apiAuthValue;

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getSaveMatch() {
        return saveMatch;
    }

    public void setSaveMatch(String saveMatch) {
        this.saveMatch = saveMatch;
    }

    public String getWeaponConfigure() {
        return weaponConfigure;
    }

    public void setWeaponConfigure(String weaponConfigure) {
        this.weaponConfigure = weaponConfigure;
    }

    public String getApiAuthHeader() {
        return apiAuthHeader;
    }

    public void setApiAuthHeader(String apiAuthHeader) {
        this.apiAuthHeader = apiAuthHeader;
    }

    public String getApiAuthValue() {
        return apiAuthValue;
    }

    public void setApiAuthValue(String apiAuthValue) {
        this.apiAuthValue = apiAuthValue;
    }
}
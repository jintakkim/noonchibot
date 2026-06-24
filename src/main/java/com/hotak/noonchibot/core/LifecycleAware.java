package com.hotak.noonchibot.core;

public interface LifecycleAware {
    void onStart();
    void onShutdown();

    /**
     * onStart -> phase 작은 것부터
     * onShutdown -> phase 큰 것부터
     * 같은 phase는 순서 보장 안 됨
     */
    default int phase() {
        return Integer.MAX_VALUE;
    }
}

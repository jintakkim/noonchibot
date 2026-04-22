package com.hotak.noonchibot.connector;

import java.util.ArrayList;
import java.util.List;

public class ExchangeLifeCycleRegistry {
    private final List<LifecycleComponent> lifecycleComponents = new ArrayList<>();

    /**
     * 등록 순서대로 앱 시작시 호출
     */
    public <T extends LifecycleComponent> T register(T worker) {
        lifecycleComponents.add(worker);
        return worker;
    }

    public void startAll() {
        lifecycleComponents.forEach(LifecycleComponent::start);
    }

    public void stopAll() {
        // 역순 종료
        for (int i = lifecycleComponents.size() - 1; i >= 0; i--) {
            lifecycleComponents.get(i).shutdown();
        }
    }
}

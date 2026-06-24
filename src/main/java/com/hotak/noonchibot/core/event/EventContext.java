package com.hotak.noonchibot.core.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventContext {
    private static final ScopedValue<EventMetadata> CURRENT = ScopedValue.newInstance();

    /**
     * 현재 bind된 metadata. 없으면 새 root.
     */
    public static EventMetadata currentOrRoot() {
        return CURRENT.isBound() ? CURRENT.get() : EventMetadata.newRoot();
    }

    /**
     * 핸들러 실행을 metadata bind 스코프로 감싼다. EventBus가 dispatch 시 호출.
     */
    static void runWith(EventMetadata md, Runnable body) {
        ScopedValue.where(CURRENT, md).run(body);
    }
}

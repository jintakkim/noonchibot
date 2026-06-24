package com.hotak.noonchibot.core.event;

public sealed interface ExecutionPolicy {
    record Sequential(String key) implements ExecutionPolicy {}

    record Concurrent() implements ExecutionPolicy {
        public static final Concurrent INSTANCE = new Concurrent();
    }

    record Inline() implements ExecutionPolicy {
        public static final Inline INSTANCE = new Inline();
    }

    /**
     * 같은 key의 핸들러들은 하나의 단일 스레드 시퀀서에서 직렬 실행한다.
     */
    static ExecutionPolicy sequential(String key) {
        return new Sequential(key);
    }

    static ExecutionPolicy sequential() {
        return new Sequential(EventConstants.DOMAIN_KEY);
    }

    /**
     * 비동기/병렬 실행 순서 보장 X. ex) IO/blocking 작업에 사용
     */
    static ExecutionPolicy concurrent() {
        return Concurrent.INSTANCE;
    }

    /**
     * publish 호출 스레드에서 즉시 실행.  매우 가벼운 핸들러일때 사용.
     */
    static ExecutionPolicy inline() {
        return Inline.INSTANCE;
    }
}

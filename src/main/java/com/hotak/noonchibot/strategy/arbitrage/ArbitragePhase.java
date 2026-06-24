package com.hotak.noonchibot.strategy.arbitrage;

public enum ArbitragePhase {
    /**
     * 타켓 물량까지 도달하지 못하였고 목표 갭, 펀딩비까지 도달할때까지 대기하는 상태
     * 갭이 포착되었다면 ENTER 페이즈로 이동한다.
     */
    OPPORTUNITY_DETECTION,

    ENTER,
    /**
     * 비동기 작업 중일때
     */
    EXECUTING,
    /**
     * Leg risk 해결을 위한 체크 단계
     */
    LEG_RISK_CONTROL,
    /**
     * 모든 타겟 물량이 채워지고
     */
    HOLD,
    EXIT,
    /**
     *
     */
    STOP_LOSS,

}

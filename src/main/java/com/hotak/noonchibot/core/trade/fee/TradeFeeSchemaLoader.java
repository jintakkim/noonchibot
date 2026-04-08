package com.hotak.noonchibot.core.trade.fee;

public interface TradeFeeSchemaLoader {
    /**
     * 개별 페어별 수수료 api 조회가 가능하다면 해당 api를 사용하여 자세한 수수료율 제공한다.
     * ex) 거래소 별로 이벤트, 일부 페어의 경우 수수료 무료정책을 적용중이기 때문에 필요시 사용.
     */
    TradeFeeSchema get(String tradingPair);
}

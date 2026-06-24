package com.hotak.noonchibot.connector.transfer;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * currently supports inner transfer only
 */
@RequiredArgsConstructor
public class FundTransferService {
    private final List<TransferRouter> routers;

    /**
     * 전송 경로에 따라 매우 오래 걸릴 수도 있다.(특히, 온체인 전송)
     */
    public CompletableFuture<TransferResult> transfer(TransferRoute route) {
        Optional<TransferRouter> optRouter = routers.stream().filter(router -> router.canHandle(route)).findAny();
        if(optRouter.isEmpty()) {
            CompletableFuture.failedFuture(throw new FundTransferException("지원하지 않는 경로 입니다." + route));
        }
        return optRouter.get().handle(route);
    }
}

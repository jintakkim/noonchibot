package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.transfer.FundTransferException;
import com.hotak.noonchibot.connector.transfer.TransferResult;
import com.hotak.noonchibot.connector.transfer.TransferRoute;
import com.hotak.noonchibot.connector.transfer.TransferRouter;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hyperliquid master ↔ sub-account 간 USDC 이체.
 *
 * 항상 master account가 서명한다. Sub-account는 private key가 없으므로
 * master agent를 통해서만 작동한다.
 *
 * 하나의 트랜잭션으로 sub → sub 이체는 불가능하다. master를 경유해야 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class DeriviativeInnerTransfer implements TransferRouter {

    private static final BigDecimal USDC_DECIMAL_FACTOR = new BigDecimal("1000000");
    private final RestAssistantImpl restAssistant;
    private final IoExecutor ioExecutor;

    @Override
    public CompletableFuture<TransferResult> handle(TransferRoute route) {
        if(!canHandle(route)) throw new FundTransferException("지원하지 않는 route 입니다." + route);
        boolean masterToSub = isMasterToSub(route);
        String subAddress = masterToSub ? route.to().identifier() : route.from().identifier();
        return ioExecutor.submitCompletable(() -> execute(subAddress, route.amount(), masterToSub));
    }

    @Override
    public boolean canHandle(TransferRoute route) {
        return route.from().exchange() == Exchange.HYPERLIQUID_DERIVATIVE &&
                route.to().exchange() == Exchange.HYPERLIQUID_DERIVATIVE &&
                route.asset().equals("USDC") &&
                // Only support master to sub or sub to master
                (isMasterToSub(route) || isSubToMaster(route));
    }

    private boolean isMasterToSub(TransferRoute route) {
        return route.from().role().equals("MASTER") && route.to().role().equals("SUB");
    }

    private boolean isSubToMaster(TransferRoute route) {
        return route.from().role().equals("SUB") && route.to().role().equals("MASTER");
    }

    /**
     * Master → Sub-account로 USDC 이체.
     *
     * @param subAccountAddress 받을 sub-account의 0x 주소
     * @param amount 이체할 USDC 양 (예: 1.5 = $1.5)
     * @return 이체 결과
     * @throws FundTransferException 이체 실패
     */
    public TransferResult transferMasterToSub(
            String subAccountAddress,
            BigDecimal amount
    ) {
        return execute(subAccountAddress, amount, true);
    }

    /**
     * Sub-account → Master로 USDC 이체.
     *
     * @param subAccountAddress 보낼 sub-account의 0x 주소
     * @param amount 이체할 USDC 양
     * @return 이체 결과
     * @throws FundTransferException 이체 실패
     */
    public TransferResult transferSubToMaster(
            String subAccountAddress,
            BigDecimal amount
    ) {
        return execute(subAccountAddress, amount, false);
    }

    private TransferResult execute(
            String subAccountAddress,
            BigDecimal amount,
            boolean masterToSub
    ) {
        validateInput(subAccountAddress, amount);
        long usdMicros = toMicroUsdc(amount);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "subAccountTransfer");
        action.put("subAccountUser", subAccountAddress);
        action.put("isDeposit", masterToSub);
        action.put("usd", usdMicros);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.EXCHANGE_PATH_URL)
                .body(Map.of("action", action))
                .authRequired(true)
                .build();

        log.info("Executing subAccountTransfer {}: address={} amount={}",
                masterToSub ? "master→sub" : "sub→master", subAccountAddress, amount);

        try {
            JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
            return parseResponse(response, subAccountAddress, amount, masterToSub);
        } catch (FundTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FundTransferException("Transfer failed: " + e.getMessage(), e);
        }
    }

    private TransferResult parseResponse(
            JsonNode response,
            String subAccountAddress,
            BigDecimal amount,
            boolean masterToSub
    ) {
        String status = response.path("status").asString();
        if (!"ok".equals(status)) {
            String errorMessage = response.path("response").asString("Unknown error");
            log.error("Transfer failed: {}", errorMessage);
            throw new FundTransferException("Transfer failed: " + errorMessage);
        }
        log.info("Transfer succeeded: address={} amount={} masterToSub={}", subAccountAddress, amount, masterToSub);
        return new TransferResult(true, subAccountAddress, amount, masterToSub, response.toString());
    }

    private void validateInput(String subAccountAddress, BigDecimal amount) {
        if (subAccountAddress == null || subAccountAddress.isBlank()) {
            throw new IllegalArgumentException("subAccountAddress required");
        }
        if (!subAccountAddress.startsWith("0x") || subAccountAddress.length() != 42) {
            throw new IllegalArgumentException("Invalid sub-account address format: " + subAccountAddress);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        // 너무 작은 금액은 micro 변환에서 0이 될 수 있음
        if (amount.compareTo(new BigDecimal("0.000001")) < 0) {
            throw new IllegalArgumentException("Amount too small (below micro-USDC precision): " + amount);
        }
    }

    /**
     * USDC 양을 micro-USDC 정수로 변환.
     * Hyperliquid는 USDC를 6자리 정수로 받음 ($1 = 1,000,000).
     * rounding 정책으로 DOWN 사용 (사용자에게 유리한 방향, 잔여 micro 발생 안 함).
     */
    private long toMicroUsdc(BigDecimal amount) {
        BigDecimal scaled = amount.multiply(USDC_DECIMAL_FACTOR)
                .setScale(0, RoundingMode.DOWN);
        return scaled.longValueExact();
    }
}
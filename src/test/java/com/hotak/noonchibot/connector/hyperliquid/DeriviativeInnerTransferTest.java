package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.transfer.FundTransferException;
import com.hotak.noonchibot.connector.transfer.TransferDispatcher;
import com.hotak.noonchibot.connector.transfer.TransferResult;
import com.hotak.noonchibot.connector.transfer.TransferRoute;
import com.hotak.noonchibot.connector.transfer.TransferableAccount;
import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.transfer.TransferEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeriviativeInnerTransferTest extends RestClientTest {
    private static final String SUB_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";

    private TestEventPublisher eventPublisher;
    private TestEventSubscriber eventSubscriber;
    private DeriviativeInnerTransfer transfer;
    private TransferDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        eventSubscriber = new TestEventSubscriber();
        transfer = new DeriviativeInnerTransfer(restAssistant);
        dispatcher = new TransferDispatcher(List.of(transfer), eventPublisher, eventSubscriber);
    }

    @Test
    @DisplayName("지원하는 Hyperliquid master → sub USDC route를 처리할 수 있다")
    void canHandle_masterToSubUsdc() {
        assertThat(transfer.canHandle(route(master(), sub(), "1.5"))).isTrue();
    }

    @Test
    @DisplayName("지원하지 않는 asset route는 처리하지 않는다")
    void canHandle_unsupportedAsset() {
        assertThat(transfer.canHandle(route(master(), sub(), "1.5", "BTC"))).isFalse();
    }

    @Test
    @DisplayName("지원하는 route를 실행하면 TransferResult를 반환한다")
    void execute_supportedRoute_returnsTransferResult() {
        TransferRoute route = route(master(), sub(), "1.5");

        TransferResult result = runTransfer(route);

        assertThat(result).isEqualTo(new TransferResult(
                true,
                route.from(),
                route.to(),
                "USDC",
                new BigDecimal("1.5"),
                BigDecimal.ZERO
        ));
    }

    @Test
    @DisplayName("TransferDispatcher는 TransferEvent.Requested를 구독한다")
    void dispatcherOnStart_subscribesTransferRequested() {
        dispatcher.onStart();

        assertThat(eventSubscriber.isSubscribed(TransferEvent.Requested.class)).isTrue();
        assertThat(dispatcher.phase()).isEqualTo(Phases.TRANSFER_SETUP);
    }

    @Test
    @DisplayName("TransferDispatcher는 지원하는 route 성공 시 Completed 이벤트를 발행한다")
    void dispatcher_requestedSupportedRoute_publishesCompleted() {
        TransferRoute route = route(master(), sub(), "1.5");

        runWith(HyperliquidFixture.subAccountTransferSuccess(SUB_ADDRESS, new BigDecimal("1.5"), true),
                () -> dispatcher.onEvent(new TransferEvent.Requested(route)));

        assertThat(eventPublisher.only(TransferEvent.Completed.class).result())
                .isEqualTo(new TransferResult(
                        true,
                        route.from(),
                        route.to(),
                        "USDC",
                        new BigDecimal("1.5"),
                        BigDecimal.ZERO
                ));
    }

    @Test
    @DisplayName("TransferDispatcher는 지원하지 않는 route면 Failed 이벤트를 발행한다")
    void dispatcher_requestedUnsupportedRoute_publishesFailed() {
        TransferRoute route = route(master(), sub(), "1.5", "BTC");

        dispatcher.onEvent(new TransferEvent.Requested(route));

        TransferEvent.Failed failed = eventPublisher.only(TransferEvent.Failed.class);
        assertThat(failed.route()).isEqualTo(route);
        assertThat(failed.cause()).isInstanceOf(FundTransferException.class);
    }

    private TransferResult runTransfer(TransferRoute route) {
        final TransferResult[] result = new TransferResult[1];
        runWith(HyperliquidFixture.subAccountTransferSuccess(SUB_ADDRESS, new BigDecimal("1.5"), true),
                () -> result[0] = transfer.execute(route));
        return result[0];
    }

    private TransferRoute route(TransferableAccount from, TransferableAccount to, String amount) {
        return route(from, to, amount, "USDC");
    }

    private TransferRoute route(TransferableAccount from, TransferableAccount to, String amount, String asset) {
        return new TransferRoute(from, to, asset, new BigDecimal(amount));
    }

    private TransferableAccount master() {
        return new TransferableAccount(Exchange.HYPERLIQUID_DERIVATIVE, null, "MASTER");
    }

    private TransferableAccount sub() {
        return new TransferableAccount(Exchange.HYPERLIQUID_DERIVATIVE, SUB_ADDRESS, "SUB");
    }
}

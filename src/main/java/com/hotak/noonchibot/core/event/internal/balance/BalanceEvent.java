package com.hotak.noonchibot.core.event.internal.balance;

import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

import java.util.Map;

public sealed interface BalanceEvent extends CoreEvent {
    record SnapshotReceived(Map<String, AssetState> assets) implements BalanceEvent {}
    record UpdateReceived(Map<String, AssetState> assets) implements BalanceEvent {}
}

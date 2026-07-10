package com.hotak.noonchibot.core;

import com.hotak.noonchibot.core.event.SequentialDispatcher;

public interface Clock {
    void addIterator(TimeIterator iterator, SequentialDispatcher dispatcher);
    void removeIterator(TimeIterator iterator);
}

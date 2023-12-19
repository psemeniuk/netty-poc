package com.psemeniuk;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import sun.nio.ch.SelectorProviderImpl;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MeasureWaitTimeSelectorProvider extends SelectorProviderImpl {

    //FIXME compile error `java: package sun.nio.ch does not exist` in IDEA - https://youtrack.jetbrains.com/issue/IDEA-201168/Cannot-compile-java-package-sun.misc-does-not-exist

    private static final SelectorProvider delegate = SelectorProvider.provider();

    @Override
    public AbstractSelector openSelector() throws IOException {
        return new MeasureWaitTimeSelector(delegate.openSelector());
    }

    private static class MeasureWaitTimeSelector extends AbstractSelector {

        private final AbstractSelector delegate;
        private final Method delegateRegister;
        private final Method delegateImplCloseSelector;
        private final Timer selects = Metrics.timer("netty.eventloop.selects");

        public MeasureWaitTimeSelector(AbstractSelector delegate) {
            super(delegate.provider());
            this.delegate = delegate;
            try {
                this.delegateRegister = AbstractSelector.class.getDeclaredMethod("register", AbstractSelectableChannel.class, int.class, Object.class);
                this.delegateImplCloseSelector = AbstractSelector.class.getDeclaredMethod("implCloseSelector");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            delegateRegister.setAccessible(true);
        }

        @Override
        protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
            try {
                //TODO check perf degradation impact
                return (SelectionKey) delegateRegister.invoke(delegate, ch, ops, att);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<SelectionKey> keys() {
            return this.delegate.keys();
        }

        @Override
        public Set<SelectionKey> selectedKeys() {
            return this.delegate.selectedKeys();
        }

        @Override
        public int selectNow() throws IOException {
            long startNanos = System.nanoTime();
            int res = this.delegate.selectNow();
            selects.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            return res;
        }

        @Override
        public int select(long timeout) throws IOException {
            long startNanos = System.nanoTime();
            int res = this.delegate.select(timeout);
            selects.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            return res;
        }

        @Override
        public int select() throws IOException {
            long startNanos = System.nanoTime();
            int res = this.delegate.select();
            selects.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
            return res;
        }

        @Override
        public Selector wakeup() {
            return this.delegate.wakeup();
        }

        @Override
        protected void implCloseSelector() {
            try {
                delegateImplCloseSelector.invoke(delegate);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

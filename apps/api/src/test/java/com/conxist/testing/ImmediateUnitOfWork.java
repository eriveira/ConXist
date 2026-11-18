package com.conxist.testing;

import java.util.function.Supplier;
import com.conxist.platform.UnitOfWork;

/** Runs the work directly; counts transactions so tests can check what was grouped together. */
public class ImmediateUnitOfWork implements UnitOfWork {

    private int transactions;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        transactions++;
        return work.get();
    }

    public int transactions() {
        return transactions;
    }
}

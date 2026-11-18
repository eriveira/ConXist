package com.conxist.platform.adapter;

import java.util.function.Supplier;
import com.conxist.platform.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactions;

    SpringUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}

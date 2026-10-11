package com.sevenb.user_manager.service;

import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** TransactionManager en memoria con la sincronizacion real de Spring; el commit puede forzarse a fallar. */
final class FakeTransactionManager extends AbstractPlatformTransactionManager {

    private final boolean failOnCommit;
    int commits;
    int rollbacks;

    FakeTransactionManager(boolean failOnCommit) {
        this.failOnCommit = failOnCommit;
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (failOnCommit) {
            throw new TransactionSystemException("commit fallido");
        }
        commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
    }
}

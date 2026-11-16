package com.yangaobo.servicedesk.ticket.component;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;

import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本地短事务与原键回执恢复的业务边界。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class BusinessTransaction {
    private final PlatformTransactionManager transactionManager;

    /**
     * 执行完整短事务；并发唯一冲突后在新事务核对原回执，不盲目重做。
     */
    public <T> T execute(Supplier<T> action, Supplier<T> receipt) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return transaction.execute(status -> action.get());
        } catch (BusinessException failure) {
            T existing = transaction.execute(status -> receipt.get());
            if (existing != null) return existing;
            throw failure;
        } catch (DuplicateKeyException conflict) {
            T existing = transaction.execute(status -> receipt.get());
            if (existing != null) return existing;
            throw new BusinessException(CommonErrorCode.DUPLICATE_VALUE);
        }
    }
}

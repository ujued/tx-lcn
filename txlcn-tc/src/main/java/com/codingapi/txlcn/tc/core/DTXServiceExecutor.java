/*
 * Copyright 2017-2019 CodingApi .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codingapi.txlcn.tc.core;


import com.codingapi.txlcn.common.exception.BeforeBusinessException;
import com.codingapi.txlcn.common.util.Transactions;
import com.codingapi.txlcn.logger.TxLogger;
import com.codingapi.txlcn.tc.core.propagation.DefaultTransactionSeparator;
import com.codingapi.txlcn.tc.support.TXLCNTransactionBeanHelper;
import com.codingapi.txlcn.tc.core.context.TCGlobalContext;
import com.codingapi.txlcn.tc.core.propagation.TXLCNTransactionSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * LCN分布式事务业务执行器
 * Created by lorne on 2017/6/8.
 */
@Component
@Slf4j
public class DTXServiceExecutor {

    private final TCGlobalContext globalContext;

    private final TxLogger txLogger;

    private final TXLCNTransactionBeanHelper txlcnTransactionBeanHelper;

    @Autowired
    private DefaultTransactionSeparator defaultTransactionSeparator;

    @Autowired
    public DTXServiceExecutor(TxLogger txLogger, TXLCNTransactionBeanHelper txlcnTransactionBeanHelper,
                              TCGlobalContext globalContext) {
        this.txLogger = txLogger;
        this.txlcnTransactionBeanHelper = txlcnTransactionBeanHelper;
        this.globalContext = globalContext;
    }

    /**
     * 事务业务执行
     *
     * @param info info
     * @return Object
     * @throws Throwable Throwable
     */
    public Object transactionRunning(TxTransactionInfo info) throws Throwable {

        // 1. 获取事务类型
        String transactionType = info.getTransactionType();

        // 2. 事务状态抉择器
//        TXLCNTransactionSeparator lcnTransactionSeparator =
//                txlcnTransactionBeanHelper.loadLCNTransactionStateResolver(transactionType);

        // 3. 获取事务状态
        DTXLogicState lcnTransactionState = defaultTransactionSeparator.loadTransactionState(info);
        // 3.1 如果不参与分布式事务立即终止
        if (lcnTransactionState.equals(DTXLogicState.NON)) {
            return info.getBusinessCallback().call();
        }

        // 4. 获取bean
        DTXLocalControl lcnTransactionControl =
                txlcnTransactionBeanHelper.loadLCNTransactionControl(transactionType, lcnTransactionState);

        // 5. 织入事务操作
        try {
            // 5.1 记录事务类型到事务上下文
            Set<String> transactionTypeSet = globalContext.txContext(info.getGroupId()).getTransactionTypes();
            transactionTypeSet.add(transactionType);

            // 5.2 业务执行前
            txLogger.info(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "pre service business code, add transaction type: %s, types: %s",
                    transactionType, transactionTypeSet);
            lcnTransactionControl.preBusinessCode(info);

            // 5.3 执行业务
            txLogger.info(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "do service business code");
            Object result = lcnTransactionControl.doBusinessCode(info);

            // 5.4 业务执行成功
            txLogger.info(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "service business success");
            lcnTransactionControl.onBusinessCodeSuccess(info, result);
            return result;
        } catch (BeforeBusinessException e) {
            txLogger.error(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "before business code error");
            throw e;
        } catch (Throwable e) {
            // 5.5 业务执行失败
            txLogger.error(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "business code error");
            lcnTransactionControl.onBusinessCodeError(info, e);
            throw e;
        } finally {
            // 5.6 业务执行完毕
            lcnTransactionControl.postBusinessCode(info);
        }
    }


}
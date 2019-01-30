package com.codingapi.txlcn.tm.txmsg.transaction;

import com.codingapi.txlcn.common.exception.TxManagerException;
import com.codingapi.txlcn.txmsg.params.DTXLockParams;
import com.codingapi.txlcn.tm.txmsg.RpcExecuteService;
import com.codingapi.txlcn.tm.txmsg.TransactionCmd;
import com.codingapi.txlcn.tm.core.storage.FastStorage;
import com.codingapi.txlcn.common.exception.FastStorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Description:
 * Date: 19-1-22 下午3:02
 *
 * @author ujued
 */
@Component("rpc_release-dtx-lock")
public class ReleaseDTXLockExecuteService implements RpcExecuteService {

    private final FastStorage fastStorage;

    @Autowired
    public ReleaseDTXLockExecuteService(FastStorage fastStorage) {
        this.fastStorage = fastStorage;
    }

    @Override
    public Serializable execute(TransactionCmd transactionCmd) throws TxManagerException {
        DTXLockParams dtxLockParams = transactionCmd.getMsg().loadBean(DTXLockParams.class);
        try {
            fastStorage.releaseLocks(dtxLockParams.getContextId(), dtxLockParams.getLocks());
            return true;
        } catch (FastStorageException e) {
            throw new TxManagerException(e);
        }
    }
}
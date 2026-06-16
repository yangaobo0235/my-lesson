package com.yangaobo.ai.approval.exception;

public class ApprovalNotFoundException extends RuntimeException {

    public ApprovalNotFoundException() {
        super("审批任务不存在");
    }
}

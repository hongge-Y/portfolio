package com.campus.portfolio.dto;

import jakarta.validation.constraints.NotNull;

/** 审批入参 */
public class LeaveApproveDTO {

    @NotNull(message = "请假ID不能为空")
    private Long leaveId;

    /** 1 批准 / 2 驳回 */
    @NotNull(message = "审批结果不能为空")
    private Integer result;

    private String reason;

    public Long getLeaveId() { return leaveId; }
    public void setLeaveId(Long leaveId) { this.leaveId = leaveId; }
    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

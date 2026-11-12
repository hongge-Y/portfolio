package com.campus.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class LeaveVO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeNo;
    private String className;
    private Long classOrgId;
    private Integer type;
    private String typeText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal days;
    /** 学委视角会被置空（脱敏） */
    private String reason;
    private Integer status;
    private String statusText;
    private Integer currentStep;
    private LocalDateTime returnTime;
    private LocalDateTime createTime;
    private List<Attachment> attachments;
    private List<ApprovalNode> approvals;

    public static class Attachment {
        private String fileUrl;
        private String fileName;
        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }

    public static class ApprovalNode {
        private Integer step;
        private String approverRole;
        private String approverName;
        private Integer result;
        private String resultText;
        private String reason;
        private Integer status;
        private LocalDateTime handleTime;
        public Integer getStep() { return step; }
        public void setStep(Integer step) { this.step = step; }
        public String getApproverRole() { return approverRole; }
        public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
        public String getApproverName() { return approverName; }
        public void setApproverName(String approverName) { this.approverName = approverName; }
        public Integer getResult() { return result; }
        public void setResult(Integer result) { this.result = result; }
        public String getResultText() { return resultText; }
        public void setResultText(String resultText) { this.resultText = resultText; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public LocalDateTime getHandleTime() { return handleTime; }
        public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public Long getClassOrgId() { return classOrgId; }
    public void setClassOrgId(Long classOrgId) { this.classOrgId = classOrgId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getTypeText() { return typeText; }
    public void setTypeText(String typeText) { this.typeText = typeText; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getDays() { return days; }
    public void setDays(BigDecimal days) { this.days = days; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public LocalDateTime getReturnTime() { return returnTime; }
    public void setReturnTime(LocalDateTime returnTime) { this.returnTime = returnTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }
    public List<ApprovalNode> getApprovals() { return approvals; }
    public void setApprovals(List<ApprovalNode> approvals) { this.approvals = approvals; }
}

package com.campus.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/** 请假提交 / 修改 */
public class LeaveApplyDTO {

    /** 为空=新增，不为空=修改（仅 30 已驳回可改） */
    private Long id;

    @NotNull(message = "请假类型不能为空")
    private Integer type;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotBlank(message = "请假原因不能为空")
    private String reason;

    private List<Attachment> attachments;

    public static class Attachment {
        private String fileUrl;
        private String fileName;
        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }
}

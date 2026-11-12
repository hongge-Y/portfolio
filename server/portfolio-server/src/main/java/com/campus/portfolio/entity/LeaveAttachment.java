package com.campus.portfolio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 请假附件（leave_attachment） */
@TableName("leave_attachment")
public class LeaveAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long leaveId;
    private String fileUrl;
    private String fileName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLeaveId() { return leaveId; }
    public void setLeaveId(Long leaveId) { this.leaveId = leaveId; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}

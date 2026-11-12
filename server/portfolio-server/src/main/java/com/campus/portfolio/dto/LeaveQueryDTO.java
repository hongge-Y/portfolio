package com.campus.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 通用：分页 + 请假筛选 */
public class LeaveQueryDTO {

    private int pageNum = 1;
    private int pageSize = 10;
    private Search searchModel = new Search();

    public static class Search {
        private Integer status;
        private Integer type;
        private String startTime;
        private String endTime;
        private Boolean pendingOnly;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getType() { return type; }
        public void setType(Integer type) { this.type = type; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public Boolean getPendingOnly() { return pendingOnly; }
        public void setPendingOnly(Boolean pendingOnly) { this.pendingOnly = pendingOnly; }
    }

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public Search getSearchModel() { return searchModel; }
    public void setSearchModel(Search searchModel) { this.searchModel = searchModel; }
}

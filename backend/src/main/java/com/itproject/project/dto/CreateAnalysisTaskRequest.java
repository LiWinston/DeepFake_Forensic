package com.itproject.project.dto;

import com.itproject.analysis.entity.AnalysisTask;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAnalysisTaskRequest {
    
    @Size(max = 255, message = "任务名称不能超过255个字符")
    private String taskName;
    
    @NotNull(message = "分析类型不能为空")
    private AnalysisTask.AnalysisType analysisType;
    
    @Size(max = 1000, message = "任务描述不能超过1000个字符")
    private String description;
    
    @Size(max = 2000, message = "备注不能超过2000个字符")
    private String notes;
    
    @NotNull(message = "项目ID不能为空")
    private Long projectId;
}

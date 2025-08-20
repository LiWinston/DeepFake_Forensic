package com.itproject.project.dto;

import com.itproject.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateProjectRequest {
    
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过200个字符")
    private String name;
    
    @Size(max = 1000, message = "项目描述不能超过1000个字符")
    private String description;
    
    @Size(max = 100, message = "案件编号不能超过100个字符")
    private String caseNumber;
    
    @Size(max = 200, message = "委托方名称不能超过200个字符")
    private String clientName;
    
    @Size(max = 255, message = "委托方联系方式不能超过255个字符")
    private String clientContact;
    
    private Project.ProjectType projectType = Project.ProjectType.GENERAL;
    
    private LocalDateTime deadline;
    
    private LocalDateTime caseDate;
    
    @Size(max = 1000, message = "证据描述不能超过1000个字符")
    private String evidenceDescription;
    
    @Size(max = 2000, message = "备注不能超过2000个字符")
    private String notes;
    
    @Size(max = 500, message = "标签不能超过500个字符")
    private String tags;
}

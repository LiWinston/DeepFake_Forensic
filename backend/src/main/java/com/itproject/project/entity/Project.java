package com.itproject.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.itproject.auth.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Project entity representing a forensic case or investigation project
 */
@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectType projectType = ProjectType.GENERAL;
    
    @Column(length = 100)
    private String caseNumber; // 案件编号
    
    @Column(length = 200)
    private String clientName; // 委托方
    
    @Column(length = 255)
    private String clientContact; // 委托方联系方式
    
    @Column(length = 500)
    private String tags; // 标签，逗号分隔
    
    @Column
    private LocalDateTime deadline; // 截止日期
    
    @Column
    private LocalDateTime caseDate; // 案件发生日期
    
    @Column(length = 1000)
    private String evidenceDescription; // 证据描述
    
    @Column(length = 2000)
    private String notes; // 备注
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // User relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;
    
    public enum ProjectStatus {
        ACTIVE,      // 进行中
        COMPLETED,   // 已完成
        SUSPENDED,   // 暂停
        ARCHIVED     // 已归档
    }
    
    public enum ProjectType {
        GENERAL,           // 一般调查
        CRIMINAL,          // 刑事案件
        CIVIL,             // 民事案件
        CORPORATE,         // 企业调查
        ACADEMIC_RESEARCH  // 学术研究
    }
}

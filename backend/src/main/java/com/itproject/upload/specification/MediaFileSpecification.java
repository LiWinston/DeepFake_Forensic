package com.itproject.upload.specification;

import com.itproject.upload.entity.MediaFile;
import com.itproject.auth.entity.User;
import com.itproject.project.entity.Project;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification utility for MediaFile dynamic queries
 */
public class MediaFileSpecification {

    /**
     * Build dynamic specification for file search
     */
    public static Specification<MediaFile> buildSpecification(
            User user, 
            Project project, 
            String status, 
            String type, 
            String search) {
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always filter by user for security
            if (user != null) {
                predicates.add(criteriaBuilder.equal(root.get("user"), user));
            }
            
            // Filter by project if specified
            if (project != null) {
                predicates.add(criteriaBuilder.equal(root.get("project"), project));
            }
            
            // Filter by upload status
            if (StringUtils.hasText(status)) {
                try {
                    MediaFile.UploadStatus uploadStatus = MediaFile.UploadStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("uploadStatus"), uploadStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore this condition
                }
            }
            
            // Filter by media type
            if (StringUtils.hasText(type)) {
                try {
                    MediaFile.MediaType mediaType = MediaFile.MediaType.valueOf(type.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("mediaType"), mediaType));
                } catch (IllegalArgumentException e) {
                    // Invalid type, ignore this condition
                }
            }
            
            // Search in filename (both original and stored filename)
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate fileNamePredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("fileName")), searchPattern);
                Predicate originalFileNamePredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("originalFileName")), searchPattern);
                
                predicates.add(criteriaBuilder.or(fileNamePredicate, originalFileNamePredicate));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Convenience method for user-based search
     */
    public static Specification<MediaFile> forUser(User user, String status, String type, String search) {
        return buildSpecification(user, null, status, type, search);
    }
    
    /**
     * Convenience method for project-based search
     */
    public static Specification<MediaFile> forProject(Project project, String status, String type, String search) {
        return buildSpecification(project.getUser(), project, status, type, search);
    }
}

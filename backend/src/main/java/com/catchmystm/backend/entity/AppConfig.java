package com.catchmystm.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfig {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }
}
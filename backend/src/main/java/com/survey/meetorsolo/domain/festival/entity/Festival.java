package com.survey.meetorsolo.domain.festival.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "festivals")
public class Festival {
    @Id
    private Long id;

    @Column(name = "meeting_radius_meters", nullable = false)
    private Integer meetingRadiusMeters;

    protected Festival() {
    }

    public Long getId() {
        return id;
    }

    public Integer getMeetingRadiusMeters() {
        return meetingRadiusMeters;
    }
}

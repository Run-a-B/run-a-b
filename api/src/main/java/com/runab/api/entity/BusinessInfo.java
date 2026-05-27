package com.runab.api.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;


    @Column(name = "business_status", nullable = false)
    private Boolean businessStatus; // true =사업중 , false= 사업안함/ 준비중


    @Column(name = "job_category", nullable = false, length = 50)
    private String jobCategory; // 직군/ 업종


    @Column(nullable = false, length = 50)
    private String region; // 지역


    @Column(name="annual_revenue")
    private Long annualRevenue;  // 연매출 | 사업 중일때만, 아니면 null


    @Column(name = "employee_count")
    private Integer employeeCount; // 직원수


    @Builder
    public BusinessInfo(User user, Boolean businessStatus, String jobCategory,
                        String region, Long annualRevenue, Integer employeeCount) {
        this.user = user;
        this.businessStatus = businessStatus;
        this.jobCategory = jobCategory;
        this.region = region;
        this.annualRevenue = annualRevenue;
        this.employeeCount = employeeCount;
    }

    // 비지니스 메서드 :  사업 정보 업데이트
    public void update(Boolean businessStatus, String jobCategory, String region,
                       Long annualRevenue, Integer employeeCount) {
        if (businessStatus != null)
            this.businessStatus = businessStatus;

        if (jobCategory != null)
            this.jobCategory = jobCategory;

        if (region != null)
            this.region = region;

        // business_status=false면 매출 /직원수 강제 null
        if (Boolean.FALSE.equals(this.businessStatus)) {
            this.annualRevenue = null;
            this.employeeCount = null;

        } else {
            if (annualRevenue != null)
                this.annualRevenue = annualRevenue;
            if (employeeCount != null)
                this.employeeCount = employeeCount;
        }
    }
}

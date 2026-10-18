package com.runab.api.service;

import com.runab.api.dto.user.*;
import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import com.runab.api.exception.BusinessException;
import com.runab.api.exception.ErrorCode;
import com.runab.api.repository.BusinessInfoRepository;
import com.runab.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessFieldConverter converter;

    // ===== 1) 내 정보 조회 =====
    public UserDetailResponse getMyInfo(Long userId) {
        User user = findUser(userId);
        return UserDetailResponse.from(user);
    }

    // ===== 2) 내 정보 수정 (이름/나이/비밀번호) =====
    @Transactional
    public UserDetailResponse updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);

        // 2-1) 이름 변경 (입력된 경우만)
        if (request.getName() != null && !request.getName().isBlank()) {
            user.changeUsername(request.getName());
        }

        // 2-2) 나이 변경 (입력된 경우만)
        if (request.getAge() != null) {
            user.changeAge(request.getAge());
        }

        // 2-3) 비밀번호 변경 (newPassword 입력된 경우만)
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            // 현재 비밀번호 입력 안 했으면 에러
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new BusinessException(ErrorCode.CURRENT_PASSWORD_REQUIRED);
            }
            // 현재 비밀번호가 틀리면 에러
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException(ErrorCode.WRONG_PASSWORD);
            }
            // 새 비밀번호 해싱 후 저장
            user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        }

        // JPA 변경 감지(dirty checking)로 자동 UPDATE — save() 호출 불필요
        return UserDetailResponse.from(user);
    }

    // ===== 3) 내 사업 정보 조회 =====
    public BusinessInfoResponse getMyBusinessInfo(Long userId) {
        BusinessInfo info = businessInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_INFO_NOT_FOUND));

        // DB의 숫자 → 프론트용 텍스트로 변환
        String revenueText = converter.formatRevenue(info.getAnnualRevenue());
        String employeeText = converter.formatEmployeeCount(info.getEmployeeCount());

        return BusinessInfoResponse.from(info, revenueText, employeeText);
    }

    // ===== 4) 내 사업 정보 저장 (upsert: 없으면 생성, 있으면 수정) =====
    @Transactional
    public BusinessInfoResponse updateMyBusinessInfo(Long userId, BusinessInfoUpdateRequest request) {
        BusinessInfo info = businessInfoRepository.findByUserId(userId).orElse(null);
        // PATCH에서 생략한 값은 유지하되, 명시한 빈 값은 검증 없이 기존 값으로 대체하지 않는다.
        Boolean businessStatus = request.getBusinessStatus() != null ? request.getBusinessStatus()
                : info == null ? null : info.getBusinessStatus();
        String jobCategory = request.getJobCategory() != null ? request.getJobCategory()
                : info == null ? null : info.getJobCategory();
        String region = request.getRegion() != null ? request.getRegion()
                : info == null ? null : info.getRegion();

        if (businessStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_STATUS);
        }
        if (jobCategory == null || jobCategory.isBlank() || jobCategory.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_JOB_CATEGORY);
        }
        if (region == null || region.isBlank() || region.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_REGION);
        }

        Long revenue = null;
        Integer employees = null;
        if (businessStatus) {
            revenue = request.getAnnualRevenue() != null ? converter.parseRevenue(request.getAnnualRevenue())
                    : info == null ? null : info.getAnnualRevenue();
            employees = request.getEmployeeCount() != null ? converter.parseEmployeeCount(request.getEmployeeCount())
                    : info == null ? null : info.getEmployeeCount();
            if (revenue == null || employees == null) {
                throw new BusinessException(ErrorCode.BUSINESS_FIELD_REQUIRED);
            }
        }

        // 유효한 최종 상태가 확인된 후에만 기존 행을 변경하거나 신규 행을 저장한다.
        if (info == null) {
            info = businessInfoRepository.save(BusinessInfo.builder()
                    .user(findUser(userId)).businessStatus(businessStatus)
                    .jobCategory(jobCategory).region(region)
                    .annualRevenue(revenue).employeeCount(employees).build());
        } else {
            info.update(businessStatus, jobCategory, region, revenue, employees);
        }
        return BusinessInfoResponse.from(info, converter.formatRevenue(info.getAnnualRevenue()),
                converter.formatEmployeeCount(info.getEmployeeCount()));
    }

    // ===== 5) 회원 탈퇴 (soft delete) =====
    @Transactional
    public void deleteMyAccount(Long userId) {
        User user = findUser(userId);
        user.softDelete();
    }


    // ===== 공통: 사용자 조회 =====
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

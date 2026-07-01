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
        Long revenue = request.getAnnualRevenue() != null
                ? converter.parseRevenue(request.getAnnualRevenue()) : null;
        Integer employees = request.getEmployeeCount() != null
                ? converter.parseEmployeeCount(request.getEmployeeCount()) : null;

        // 이메일 가입은 signup 2단계(POST /auth/signup/business)에서 BusinessInfo가 미리 생성되지만,
        // 구글 로그인 가입은 그 단계를 안 거쳐 row가 없어 최초 저장 시 404가 났음(BUSINESS_INFO_NOT_FOUND).
        // → 없으면 새로 생성(upsert)한다.
        BusinessInfo info = businessInfoRepository.findByUserId(userId)
                .orElseGet(() -> createBusinessInfo(userId, request));

        // 생성/수정 공통: 값 반영 (null인 필드는 update() 내부에서 무시, businessStatus=false면 매출/직원수 null)
        info.update(
                request.getBusinessStatus(),
                request.getJobCategory(),
                request.getRegion(),
                revenue,
                employees
        );

        // 변경 감지로 자동 UPDATE/INSERT
        String revenueText = converter.formatRevenue(info.getAnnualRevenue());
        String employeeText = converter.formatEmployeeCount(info.getEmployeeCount());
        return BusinessInfoResponse.from(info, revenueText, employeeText);
    }

    // 최초 저장 시 BusinessInfo 신규 생성. businessStatus/jobCategory/region은 엔티티상 nullable=false라 필수값 검증한다.
    private BusinessInfo createBusinessInfo(Long userId, BusinessInfoUpdateRequest request) {
        if (request.getBusinessStatus() == null) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_STATUS);
        }
        if (request.getJobCategory() == null || request.getJobCategory().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_JOB_CATEGORY);
        }
        if (request.getRegion() == null || request.getRegion().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REGION);
        }
        User user = findUser(userId);
        // 매출/직원수는 아래 공통 update()에서 채우므로 여기선 필수값만 세팅해 저장
        return businessInfoRepository.save(BusinessInfo.builder()
                .user(user)
                .businessStatus(request.getBusinessStatus())
                .jobCategory(request.getJobCategory())
                .region(request.getRegion())
                .build());
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
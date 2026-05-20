package com.runab.api.repository;

import com.runab.api.entity.BusinessInfo;
import com.runab.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {

    // 특정 유저의 사업 정보 조회
    Optional<BusinessInfo> findByUser(User user);

    // userId로 조회 (User 객체 없을 때)
    Optional<BusinessInfo> findByUserId(Long userId);
}

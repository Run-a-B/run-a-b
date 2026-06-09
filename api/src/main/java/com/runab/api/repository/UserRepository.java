package com.runab.api.repository;


import com.runab.api.entity.AuthProvider;
import com.runab.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User ,Long> {

    // 로그인할때 이메일로 사용자를 찾음
    Optional<User> findByEmail(String email);

    // 로그인용: 탈퇴하지 않은 사용자만
    Optional<User> findByEmailAndDeletedFalse(String email);

    // 회원가입할때 이메일 중복 체크함
    boolean existsByEmail(String email);

    // 이건 구글 오스로 로그인할때 사용자를 찾음
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

}

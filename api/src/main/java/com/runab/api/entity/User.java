package com.runab.api.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Integer age;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING) // enum의 값을 문자열로 DB에 저장 | ORDINAL로 기본값이 되면 db에 숫자가 저장됨
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column
    private String password;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //--------------------------------------------------------------------------------------------------------

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BusinessInfo businessInfo;

    @Builder
    public User(String email, Integer age ,String password, AuthProvider provider, String providerId, String username){
        this.email = email;
        this.age = age;
        this.password = password;
        this.provider = provider;
        this.providerId = providerId;
        this.username = username;


    }

    // 생성및 수정 시간 자동 세팅
    @PrePersist // 엔티티가 영속화되기전 직전에 실행이되며, 데이터베이스에 저장되기 전에 자동으로 호출합니다.
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 비지니스 메서드 : 비밀 번호 변경
    public void changePassword(String newPassword){
        this.password = newPassword;
    }

    // 비즈니스 매서드: 유저명 변경
    public void changeUsername(String newUsername){
        this.username = newUsername;
    }
}

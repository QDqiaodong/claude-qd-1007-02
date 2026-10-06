package com.amusement.park.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.amusement.park.entity.SeasonPass;

public interface SeasonPassRepository extends JpaRepository<SeasonPass, Long> {

    /**
     * 悲观锁当前读：办卡/续期串行化同一会员的并发请求，
     * 保证“先查不存在 → 插入”不会并发插入两张卡（主键约束为第二道防线）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SeasonPass p where p.memberId = :memberId")
    Optional<SeasonPass> findByIdForUpdate(@Param("memberId") Long memberId);
}

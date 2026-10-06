package com.amusement.park.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.amusement.park.entity.RideInspection;

public interface RideInspectionRepository extends JpaRepository<RideInspection, Long> {

    /** 批次历史（新→旧），含已失效批次，用于追溯 */
    List<RideInspection> findByRideIdOrderByIdDesc(Long rideId);

    List<RideInspection> findByRideIdAndStatusInOrderByIdDesc(Long rideId, Collection<String> statuses);

    /** 悲观锁当前读：提交/放行/失效时串行化同一批次的并发操作 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RideInspection b where b.id = :id")
    Optional<RideInspection> findByIdForUpdate(@Param("id") Long id);
}

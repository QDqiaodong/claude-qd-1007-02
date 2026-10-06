package com.amusement.park.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.amusement.park.entity.Ride;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByStatus(String status);

    List<Ride> findByRideCode(String rideCode);

    /** 悲观锁当前读：放行/生成批次时串行化同一设施的并发操作 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);
}

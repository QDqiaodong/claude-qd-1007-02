package com.amusement.park.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.amusement.park.entity.Fnb;

public interface FnbRepository extends JpaRepository<Fnb, Long> {

    List<Fnb> findByType(String type);

    List<Fnb> findByStatus(String status);

    Optional<Fnb> findByOutletCode(String outletCode);
}

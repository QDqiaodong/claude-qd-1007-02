package com.amusement.park.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.amusement.park.entity.Show;

public interface ShowRepository extends JpaRepository<Show, Long> {
}

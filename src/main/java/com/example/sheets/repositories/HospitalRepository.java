package com.example.sheets.repositories;

import com.example.sheets.models.db.Hospital;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, String> {
    List<Hospital> findAllByNameIn(List<String> names);

    @Query("SELECT h FROM Hospital h WHERE (:lastId IS NULL OR h.id > :lastId) ORDER BY h.id ASC")
    List<Hospital> findNextBatch(@Param("lastId") String lastId, Pageable pageable);
}

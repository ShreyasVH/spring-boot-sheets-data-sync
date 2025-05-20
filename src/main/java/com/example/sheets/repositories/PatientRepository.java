package com.example.sheets.repositories;

import com.example.sheets.models.db.Patient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {
    @Query("SELECT p FROM Patient p WHERE (:lastId IS NULL OR p.id > :lastId) ORDER BY p.id ASC")
    List<Patient> findNextBatch(@Param("lastId") String lastId, Pageable pageable);
}

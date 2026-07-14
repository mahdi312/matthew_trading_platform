package com.mst.matt.marketservice.repository;
import com.mst.matt.marketservice.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByTicker(String ticker);
}

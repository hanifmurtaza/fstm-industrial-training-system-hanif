package com.example.itsystem.repository;

import com.example.itsystem.model.VisitEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisitEvaluationRepository extends JpaRepository<VisitEvaluation, Long> {

    // 判断该 visit 是否已经有评价记录
    boolean existsByVisitId(Long visitId);

    // ① 返回列表（你现在的 Controller 用的就是这个）
    List<VisitEvaluation> findByVisitId(Long visitId);

    // ② 如果只取一条，也可以用这个更方便
    Optional<VisitEvaluation> findFirstByVisitId(Long visitId);
}

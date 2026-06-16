package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.rag.model.RetrievalCandidate;
import com.yangaobo.ai.rag.model.SearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReciprocalRankFusionService {

    private static final double RRF_CONSTANT = 60.0;

    public FusionResult fuse(
            List<RetrievalCandidate> vectorHits,
            List<RetrievalCandidate> keywordHits,
            int limit) {
        Map<String, MutableFusedHit> fused = new LinkedHashMap<>();
        addRanking(fused, vectorHits);
        addRanking(fused, keywordHits);

        List<RetrievalCandidate> results = fused.values().stream()
                .sorted(Comparator
                        .comparingDouble(MutableFusedHit::rrfScore)
                        .thenComparingDouble(MutableFusedHit::evidenceScore)
                        .reversed())
                .limit(limit)
                .map(MutableFusedHit::toCandidate)
                .toList();
        double highestEvidence = results.stream()
                .mapToDouble(RetrievalCandidate::evidenceScore)
                .max()
                .orElse(0.0);
        return new FusionResult(results, highestEvidence);
    }

    private void addRanking(
            Map<String, MutableFusedHit> fused,
            List<RetrievalCandidate> ranking) {
        for (int index = 0; index < ranking.size(); index++) {
            RetrievalCandidate candidate = ranking.get(index);
            double contribution = 1.0 / (RRF_CONSTANT + index + 1);
            fused.compute(
                    candidate.fusionKey(),
                    (key, existing) -> existing == null
                            ? new MutableFusedHit(candidate, contribution)
                            : existing.merge(candidate, contribution));
        }
    }

    public record FusionResult(
            List<RetrievalCandidate> candidates,
            double highestEvidenceScore
    ) {
    }

    private static final class MutableFusedHit {

        private RetrievalCandidate candidate;
        private double rrfScore;
        private double evidenceScore;

        private MutableFusedHit(
                RetrievalCandidate candidate,
                double rrfScore) {
            this.candidate = candidate;
            this.rrfScore = rrfScore;
            this.evidenceScore = candidate.evidenceScore();
        }

        private MutableFusedHit merge(
                RetrievalCandidate incoming,
                double contribution) {
            rrfScore += contribution;
            evidenceScore = Math.max(
                    evidenceScore,
                    incoming.evidenceScore());
            if (incoming.hit().snippet().length()
                    > candidate.hit().snippet().length()) {
                candidate = incoming;
            }
            return this;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private double evidenceScore() {
            return evidenceScore;
        }

        private RetrievalCandidate toCandidate() {
            SearchHit fusedHit = candidate.hit().withScore(rrfScore);
            return new RetrievalCandidate(
                    fusedHit,
                    candidate.fusionKey(),
                    evidenceScore);
        }
    }
}

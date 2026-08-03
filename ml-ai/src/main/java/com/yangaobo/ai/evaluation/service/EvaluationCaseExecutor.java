package com.yangaobo.ai.evaluation.service;

import com.yangaobo.ai.evaluation.model.EvaluationCase;
import com.yangaobo.ai.evaluation.model.EvaluationCaseResult;

public interface EvaluationCaseExecutor {

    EvaluationCaseResult execute(EvaluationCase evaluationCase);
}

package org.ggomes.myleo.decision.onnx.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DecisionSuiteTest {

    @Test
    fun cases_haveTheAdvertisedSplit() {
        assertThat(DecisionSuite.Cases).hasSize(18)
        assertThat(DecisionSuite.of(QuestionType.CHOICE)).hasSize(DecisionSuite.CHOICE_COUNT)
        assertThat(DecisionSuite.of(QuestionType.SCORE)).hasSize(DecisionSuite.SCORE_COUNT)
        assertThat(DecisionSuite.of(QuestionType.NOUL)).hasSize(DecisionSuite.NOUL_COUNT)
        assertThat(DecisionSuite.CHOICE_COUNT).isEqualTo(8)
        assertThat(DecisionSuite.SCORE_COUNT).isEqualTo(5)
        assertThat(DecisionSuite.NOUL_COUNT).isEqualTo(5)
    }

    @Test
    fun cases_haveUniqueIds() {
        assertThat(DecisionSuite.Cases.map { it.id }).doesNotHaveDuplicates()
    }

    @Test
    fun cases_areValidDecisionRequests() {
        DecisionSuite.Cases.forEach { case ->
            assertThat(case.state).isNotBlank()
            assertThat(case.question.type).isEqualTo(case.group)
            assertThat(case.question.instructions).isNotBlank()
            assertThat(case.question.options).hasSizeBetween(2, 20)
            assertThat(case.question.options).allSatisfy { option ->
                assertThat(option.text).isNotBlank()
            }
            // Constructing the request is the same validation the ViewModel performs.
            DecisionRequest(case.state, case.question)
        }
    }

    @Test
    fun choiceCases_offerAtLeastTwoDistinctIds() {
        DecisionSuite.of(QuestionType.CHOICE).forEach { case ->
            assertThat(case.question.options.map { it.id }).doesNotHaveDuplicates()
            assertThat(case.question.options.size).isGreaterThanOrEqualTo(2)
        }
    }

    @Test
    fun scoreCases_useACompactOrderedRubric() {
        DecisionSuite.of(QuestionType.SCORE).forEach { case ->
            assertThat(case.question.options.map { it.id })
                .containsExactlyElementsOf(case.question.options.indices.map { it.toString() })
            assertThat(case.question.options.size).isBetween(3, 5)
        }
    }

    @Test
    fun noulCases_areExactlyFalseThenTrue() {
        DecisionSuite.of(QuestionType.NOUL).forEach { case ->
            assertThat(case.question.options.map { it.id }).containsExactly("false", "true")
        }
    }

    @Test
    fun of_preservesTheRunOrder() {
        assertThat(DecisionSuite.Cases.take(DecisionSuite.CHOICE_COUNT).map { it.group })
            .containsOnly(QuestionType.CHOICE)
        assertThat(DecisionSuite.of(QuestionType.SCORE).map { it.id })
            .containsExactlyElementsOf(
                DecisionSuite.Cases.filter { it.group == QuestionType.SCORE }.map { it.id },
            )
    }
}

package org.ggomes.myleo.decision.litert.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DecisionPresetsTest {

    @Test
    fun presets_eachHaveAStateAndThreeQuestions() {
        assertThat(DecisionPresets.All).hasSize(3)
        DecisionPresets.All.forEach { preset ->
            assertThat(preset.name).isNotBlank()
            assertThat(preset.state).isNotBlank()
            assertThat(preset.questions).hasSize(3)
        }
    }

    @Test
    fun presets_coverEveryQuestionType() {
        DecisionPresets.All.forEach { preset ->
            assertThat(preset.questions.map { it.type })
                .containsExactly(QuestionType.CHOICE, QuestionType.SCORE, QuestionType.NOUL)
        }
    }

    @Test
    fun presets_everyQuestionIsAValidDecisionRequest() {
        DecisionPresets.All.forEach { preset ->
            preset.questions.forEach { question ->
                val request = DecisionRequest(preset.state, question)
                assertThat(request.question.options).hasSizeBetween(2, 20)
                assertThat(request.question.options).allSatisfy { option ->
                    assertThat(option.text).isNotBlank()
                }
            }
        }
    }

    @Test
    fun presets_choiceAndScoreOptionsAreDistinct() {
        DecisionPresets.All.forEach { preset ->
            preset.questions.forEach { question ->
                assertThat(question.options.map { it.id }).doesNotHaveDuplicates()
            }
        }
    }
}

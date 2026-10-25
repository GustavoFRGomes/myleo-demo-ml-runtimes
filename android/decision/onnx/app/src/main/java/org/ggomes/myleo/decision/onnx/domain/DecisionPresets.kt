package org.ggomes.myleo.decision.onnx.domain

/** A ready-made state + question set; one graph call per question when the user taps Decide. */
data class DecisionPreset(
    val name: String,
    val state: String,
    val questions: List<DecisionQuestion>,
)

/**
 * Three invented scenarios that exercise all three question types. Content is illustrative, not
 * training data: the model scores the supplied descriptions, it does not know these tickets.
 */
object DecisionPresets {
    val All: List<DecisionPreset> =
        listOf(
            DecisionPreset(
                name = "Support ticket",
                state =
                    "Order #4417 from Larkspur Home Goods was charged twice. The customer, Mira " +
                        "Okafor, wants the extra charge refunded before Friday.",
                questions =
                    listOf(
                        DecisionQuestion.choice(
                            "Which team should handle this request?",
                            listOf(
                                "billing" to "Billing and payment disputes",
                                "shipping" to "Shipping and delivery",
                                "access" to "Account access and login",
                            ),
                        ),
                        DecisionQuestion.score(
                            "When does the customer need a reply?",
                            listOf("No deadline given", "This week", "Today", "Within the hour"),
                        ),
                        DecisionQuestion.noul(
                            "Is the customer asking for money back?",
                            falseText = "No refund is requested",
                            trueText = "A refund is requested",
                        ),
                    ),
            ),
            DecisionPreset(
                name = "Agent run",
                state =
                    "The deployment agent updated the checkout service in production. The canary " +
                        "error rate rose from 0.2% to 1.8% and the rollback button has not been pressed.",
                questions =
                    listOf(
                        DecisionQuestion.choice(
                            "What should the supervisor do with this agent run?",
                            listOf(
                                "continue" to "Let the agent continue on its own",
                                "human_review" to "Pause and ask a person to review before the next step",
                                "stop" to "Stop the run and roll back",
                            ),
                        ),
                        DecisionQuestion.score(
                            "How risky is the next step of this run?",
                            listOf(
                                "No risk: read-only work",
                                "Low risk: reversible change",
                                "Medium risk: production change with a rollback path",
                                "High risk: irreversible or costly change",
                            ),
                        ),
                        DecisionQuestion.noul(
                            "Has the run exceeded its spending budget?",
                            falseText = "The spend is within the budget",
                            trueText = "The spend exceeds the budget",
                        ),
                    ),
            ),
            DecisionPreset(
                name = "Product review",
                state =
                    "The jacket arrived two days late, but the fit is perfect and the colour " +
                        "matches the listing. The seller answered my sizing question within an hour.",
                questions =
                    listOf(
                        DecisionQuestion.choice(
                            "What is the overall sentiment of this review?",
                            listOf(
                                "positive" to "Mostly satisfied",
                                "mixed" to "Some praise and some complaints",
                                "negative" to "Mostly dissatisfied",
                            ),
                        ),
                        DecisionQuestion.score(
                            "How many stars would this reviewer give?",
                            listOf("1 star", "2 stars", "3 stars", "4 stars", "5 stars"),
                        ),
                        DecisionQuestion.noul(
                            "Did the seller answer the customer?",
                            falseText = "The seller has not answered",
                            trueText = "The seller answered",
                        ),
                    ),
            ),
        )
}

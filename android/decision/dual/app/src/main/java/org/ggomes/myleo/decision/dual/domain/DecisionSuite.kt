package org.ggomes.myleo.decision.dual.domain

/** One built-in prompt: a state plus the question asked about it. */
data class SuiteCase(
    val id: String,
    val group: QuestionType,
    val state: String,
    val question: DecisionQuestion,
)

/** The answer to one suite prompt, kept with the prompt for display. */
data class SuiteResult(
    val case: SuiteCase,
    val outcome: DecisionOutcome,
)

/**
 * 18 fixed prompts covering the three question types: 8 multi-answer choices, 5 ordered
 * scores and 5 yes/no decisions. They are examples, not training data — every state is
 * invented, and the model only scores the options it is given.
 */
object DecisionSuite {
    const val CHOICE_COUNT = 8
    const val SCORE_COUNT = 5
    const val NOUL_COUNT = 5

    val Cases: List<SuiteCase> =
        listOf(
            // MARK: - Multi-answer choice (8)
            SuiteCase(
                id = "route-billing",
                group = QuestionType.CHOICE,
                state =
                    "Order #4417 from Larkspur Home Goods was charged twice. The customer, Mira " +
                        "Okafor, wants the extra charge refunded before Friday.",
                question =
                    DecisionQuestion.choice(
                        "Which team should handle this request?",
                        listOf(
                            "billing" to "Billing and payment disputes",
                            "shipping" to "Shipping and delivery",
                            "returns" to "Returns and refunds",
                            "access" to "Account access and login",
                        ),
                    ),
            ),
            SuiteCase(
                id = "agent-supervisor",
                group = QuestionType.CHOICE,
                state =
                    "The deployment agent updated the checkout service in production. The canary " +
                        "error rate rose from 0.2% to 1.8% and the rollback button has not been pressed.",
                question =
                    DecisionQuestion.choice(
                        "What should the supervisor do with this agent run?",
                        listOf(
                            "continue" to "Let the agent continue on its own",
                            "human_review" to "Pause and ask a person to review before the next step",
                            "stop" to "Stop the run and roll back",
                        ),
                    ),
            ),
            SuiteCase(
                id = "review-sentiment",
                group = QuestionType.CHOICE,
                state =
                    "The jacket arrived two days late, but the fit is perfect and the colour " +
                        "matches the listing. The seller answered my sizing question within an hour.",
                question =
                    DecisionQuestion.choice(
                        "What is the overall sentiment of this review?",
                        listOf(
                            "positive" to "Mostly satisfied",
                            "mixed" to "Some praise and some complaints",
                            "negative" to "Mostly dissatisfied",
                        ),
                    ),
            ),
            SuiteCase(
                id = "language-french",
                group = QuestionType.CHOICE,
                state = "Bonjour, ma commande est arrivée en retard et le colis est abîmé.",
                question =
                    DecisionQuestion.choice(
                        "Which language is this message written in?",
                        listOf(
                            "french" to "French",
                            "spanish" to "Spanish",
                            "italian" to "Italian",
                            "portuguese" to "Portuguese",
                        ),
                    ),
            ),
            SuiteCase(
                id = "queue-escalation",
                group = QuestionType.CHOICE,
                state =
                    "Subject: App crashes on launch. Body: I have reinstalled twice and I am " +
                        "cancelling my subscription today if this is not fixed.",
                question =
                    DecisionQuestion.choice(
                        "Which queue should this ticket go to?",
                        listOf(
                            "escalation" to "Urgent escalation queue",
                            "normal" to "Normal support queue",
                            "sales" to "Sales and renewals",
                            "billing" to "Billing and invoices",
                        ),
                    ),
            ),
            SuiteCase(
                id = "resolution-damaged",
                group = QuestionType.CHOICE,
                state =
                    "A ceramic mug arrived with a cracked handle. The customer sent a photo and " +
                        "the order is 12 days old.",
                question =
                    DecisionQuestion.choice(
                        "Which resolution should support offer?",
                        listOf(
                            "refund" to "Refund the purchase",
                            "replacement" to "Send a replacement",
                            "store_credit" to "Offer store credit",
                            "reject" to "Reject the claim",
                        ),
                    ),
            ),
            SuiteCase(
                id = "intent-account",
                group = QuestionType.CHOICE,
                state = "Hi, I need to change the email address on my account to the one I use at work.",
                question =
                    DecisionQuestion.choice(
                        "What is the customer asking for?",
                        listOf(
                            "account_change" to "A change to their account details",
                            "billing" to "A billing or invoice change",
                            "technical" to "Help with a technical problem",
                            "cancel" to "Cancellation of the service",
                        ),
                    ),
            ),
            SuiteCase(
                id = "area-battery",
                group = QuestionType.CHOICE,
                state =
                    "The laptop battery drains from 100% to 20% in under three hours when only " +
                        "a browser is open.",
                question =
                    DecisionQuestion.choice(
                        "Which product area does this feedback concern?",
                        listOf(
                            "battery" to "Battery and power",
                            "display" to "Display and graphics",
                            "software" to "Software and apps",
                            "shipping" to "Shipping and packaging",
                        ),
                    ),
            ),

            // MARK: - Ordered score (5)
            SuiteCase(
                id = "score-urgency-outage",
                group = QuestionType.SCORE,
                state = "The payments API is returning 500 for every request in the EU region.",
                question =
                    DecisionQuestion.score(
                        "How urgent is this incident?",
                        listOf(
                            "No urgency: informational",
                            "Low: monitor and fix this week",
                            "High: fix today during business hours",
                            "Critical: every EU customer is blocked",
                        ),
                    ),
            ),
            SuiteCase(
                id = "score-review-tone",
                group = QuestionType.SCORE,
                state =
                    "The product works as described, though it is a little more expensive than " +
                        "I expected.",
                question =
                    DecisionQuestion.score(
                        "How positive is this review?",
                        listOf("Negative", "Slightly negative", "Slightly positive", "Very positive"),
                    ),
            ),
            SuiteCase(
                id = "score-complexity-migration",
                group = QuestionType.SCORE,
                state =
                    "Move 40 internal services from shared API keys to per-service OAuth tokens, " +
                        "with a two-week freeze window.",
                question =
                    DecisionQuestion.score(
                        "How complex is this migration?",
                        listOf("Trivial", "Moderate", "Complex", "Very complex"),
                    ),
            ),
            SuiteCase(
                id = "score-risk-drop-table",
                group = QuestionType.SCORE,
                state =
                    "A script would drop the analytics table that holds raw events; the last " +
                        "export is 48 hours old.",
                question =
                    DecisionQuestion.score(
                        "How risky is running the script?",
                        listOf(
                            "No risk",
                            "Low: reversible",
                            "Medium: recoverable with effort",
                            "High: irreversible data loss",
                        ),
                    ),
            ),
            SuiteCase(
                id = "score-confidence-session",
                group = QuestionType.SCORE,
                state =
                    "The customer says they cannot log in. Logs show a valid password was " +
                        "accepted, then the session token expired after 60 seconds.",
                question =
                    DecisionQuestion.score(
                        "How confident are you that session expiry is the cause?",
                        listOf("No confidence", "Low", "Medium", "High"),
                    ),
            ),

            // MARK: - Yes / no (5)
            SuiteCase(
                id = "noul-refund",
                group = QuestionType.NOUL,
                state = "Order #4417 was charged twice. The customer wants the extra charge refunded before Friday.",
                question =
                    DecisionQuestion.noul(
                        "Is the customer asking for money back?",
                        falseText = "No refund is requested",
                        trueText = "A refund is requested",
                    ),
            ),
            SuiteCase(
                id = "noul-deadline",
                group = QuestionType.NOUL,
                state = "Please send the signed contract back before Friday so we can start on Monday.",
                question =
                    DecisionQuestion.noul(
                        "Does the message contain a deadline?",
                        falseText = "No deadline is given",
                        trueText = "A deadline is given",
                    ),
            ),
            SuiteCase(
                id = "noul-production-change",
                group = QuestionType.NOUL,
                state =
                    "The agent opened a pull request that alters the production checkout service " +
                        "and wants to merge it now.",
                question =
                    DecisionQuestion.noul(
                        "Is this a production change?",
                        falseText = "No production change",
                        trueText = "A production change",
                    ),
            ),
            SuiteCase(
                id = "noul-seller-answered",
                group = QuestionType.NOUL,
                state = "The seller replied within an hour with sizing advice, and the jacket fits perfectly.",
                question =
                    DecisionQuestion.noul(
                        "Did the seller answer the customer?",
                        falseText = "The seller has not answered",
                        trueText = "The seller answered",
                    ),
            ),
            SuiteCase(
                id = "noul-account-access",
                group = QuestionType.NOUL,
                state = "I forgot my password and the reset email never arrives; I cannot sign in.",
                question =
                    DecisionQuestion.noul(
                        "Is this request about account access?",
                        falseText = "Not about account access",
                        trueText = "About account access",
                    ),
            ),
        )

    init {
        require(Cases.size == CHOICE_COUNT + SCORE_COUNT + NOUL_COUNT)
        require(Cases.map { it.id }.toSet().size == Cases.size) { "suite ids must be unique" }
        require(Cases.count { it.group == QuestionType.CHOICE } == CHOICE_COUNT)
        require(Cases.count { it.group == QuestionType.SCORE } == SCORE_COUNT)
        require(Cases.count { it.group == QuestionType.NOUL } == NOUL_COUNT)
    }

    /** Cases of one type, in the order they run. */
    fun of(group: QuestionType): List<SuiteCase> = Cases.filter { it.group == group }
}

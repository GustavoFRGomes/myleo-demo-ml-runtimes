import Foundation

struct SuiteCase: Sendable {
    let id: String
    let group: QuestionType
    let state: String
    let question: DecisionQuestion
}

struct SuiteResult: Sendable {
    let item: SuiteCase
    let outcome: DecisionOutcome
}

/// The same 18 fixed prompts as the Android leaves: 8 multi-answer, 5 ordered scores, 5 yes/no.
/// States are invented examples, not training data.
enum DecisionSuite {
    static let choiceCount = 8
    static let scoreCount = 5
    static let noulCount = 5

    static let cases: [SuiteCase] = build()

    static func of(_ group: QuestionType) -> [SuiteCase] { cases.filter { $0.group == group } }

    private static func build() -> [SuiteCase] {
        var out: [SuiteCase] = []
        func choice(_ id: String, _ state: String, _ q: String, _ options: [(String, String)]) {
            out.append(SuiteCase(id: id, group: .choice, state: state,
                                 question: try! .choice(instructions: q, options: options)))
        }
        func score(_ id: String, _ state: String, _ q: String, _ rubric: [String]) {
            out.append(SuiteCase(id: id, group: .score, state: state,
                                 question: try! .score(instructions: q, rubric: rubric)))
        }
        func noul(_ id: String, _ state: String, _ q: String, _ no: String, _ yes: String) {
            out.append(SuiteCase(id: id, group: .noul, state: state,
                                 question: try! .noul(instructions: q, falseText: no, trueText: yes)))
        }

        choice("route-billing",
               "Order #4417 from Larkspur Home Goods was charged twice. The customer, Mira Okafor, wants the extra charge refunded before Friday.",
               "Which team should handle this request?",
               [("billing", "Billing and payment disputes"), ("shipping", "Shipping and delivery"),
                ("returns", "Returns and refunds"), ("access", "Account access and login")])
        choice("agent-supervisor",
               "The deployment agent updated the checkout service in production. The canary error rate rose from 0.2% to 1.8% and the rollback button has not been pressed.",
               "What should the supervisor do with this agent run?",
               [("continue", "Let the agent continue on its own"),
                ("human_review", "Pause and ask a person to review before the next step"),
                ("stop", "Stop the run and roll back")])
        choice("review-sentiment",
               "The jacket arrived two days late, but the fit is perfect and the colour matches the listing. The seller answered my sizing question within an hour.",
               "What is the overall sentiment of this review?",
               [("positive", "Mostly satisfied"), ("mixed", "Some praise and some complaints"),
                ("negative", "Mostly dissatisfied")])
        choice("language-french",
               "Bonjour, ma commande est arrivée en retard et le colis est abîmé.",
               "Which language is this message written in?",
               [("french", "French"), ("spanish", "Spanish"), ("italian", "Italian"),
                ("portuguese", "Portuguese")])
        choice("queue-escalation",
               "Subject: App crashes on launch. Body: I have reinstalled twice and I am cancelling my subscription today if this is not fixed.",
               "Which queue should this ticket go to?",
               [("escalation", "Urgent escalation queue"), ("normal", "Normal support queue"),
                ("sales", "Sales and renewals"), ("billing", "Billing and invoices")])
        choice("resolution-damaged",
               "A ceramic mug arrived with a cracked handle. The customer sent a photo and the order is 12 days old.",
               "Which resolution should support offer?",
               [("refund", "Refund the purchase"), ("replacement", "Send a replacement"),
                ("store_credit", "Offer store credit"), ("reject", "Reject the claim")])
        choice("intent-account",
               "Hi, I need to change the email address on my account to the one I use at work.",
               "What is the customer asking for?",
               [("account_change", "A change to their account details"),
                ("billing", "A billing or invoice change"),
                ("technical", "Help with a technical problem"),
                ("cancel", "Cancellation of the service")])
        choice("area-battery",
               "The laptop battery drains from 100% to 20% in under three hours when only a browser is open.",
               "Which product area does this feedback concern?",
               [("battery", "Battery and power"), ("display", "Display and graphics"),
                ("software", "Software and apps"), ("shipping", "Shipping and packaging")])

        score("score-urgency-outage",
              "The payments API is returning 500 for every request in the EU region.",
              "How urgent is this incident?",
              ["No urgency: informational", "Low: monitor and fix this week",
               "High: fix today during business hours", "Critical: every EU customer is blocked"])
        score("score-review-tone",
              "The product works as described, though it is a little more expensive than I expected.",
              "How positive is this review?",
              ["Negative", "Slightly negative", "Slightly positive", "Very positive"])
        score("score-complexity-migration",
              "Move 40 internal services from shared API keys to per-service OAuth tokens, with a two-week freeze window.",
              "How complex is this migration?",
              ["Trivial", "Moderate", "Complex", "Very complex"])
        score("score-risk-drop-table",
              "A script would drop the analytics table that holds raw events; the last export is 48 hours old.",
              "How risky is running the script?",
              ["No risk", "Low: reversible", "Medium: recoverable with effort",
               "High: irreversible data loss"])
        score("score-confidence-session",
              "The customer says they cannot log in. Logs show a valid password was accepted, then the session token expired after 60 seconds.",
              "How confident are you that session expiry is the cause?",
              ["No confidence", "Low", "Medium", "High"])

        noul("noul-refund",
             "Order #4417 was charged twice. The customer wants the extra charge refunded before Friday.",
             "Is the customer asking for money back?",
             "No refund is requested", "A refund is requested")
        noul("noul-deadline",
             "Please send the signed contract back before Friday so we can start on Monday.",
             "Does the message contain a deadline?",
             "No deadline is given", "A deadline is given")
        noul("noul-production-change",
             "The agent opened a pull request that alters the production checkout service and wants to merge it now.",
             "Is this a production change?",
             "No production change", "A production change")
        noul("noul-seller-answered",
             "The seller replied within an hour with sizing advice, and the jacket fits perfectly.",
             "Did the seller answer the customer?",
             "The seller has not answered", "The seller answered")
        noul("noul-account-access",
             "I forgot my password and the reset email never arrives; I cannot sign in.",
             "Is this request about account access?",
             "Not about account access", "About account access")

        precondition(out.count == choiceCount + scoreCount + noulCount)
        return out
    }
}

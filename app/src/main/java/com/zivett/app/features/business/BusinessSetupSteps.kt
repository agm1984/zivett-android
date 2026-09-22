package com.zivett.app.features.business

import com.zivett.app.core.models.BusinessSetup

/// The truth-meter rules of the business setup wizard, ported from the
/// web's shared `SetupWizard.vue`: steps carry live completeness, back
/// is always free, forward requires everything before the target, and a
/// skipped-incomplete step wears a warn ring. Pure so the gating is
/// unit-testable.
object BusinessSetupSteps {
    /// Three steps, not the web's four: the plan pick is web-only (the
    /// app never offers a plan — store policy, see PARITY.md). The
    /// server treats an unpicked business plan as free Basic.
    enum class Step(val title: String) {
        PROFILE("Business profile"), PROPERTIES("Properties"), TEAM("Team");

        val index: Int get() = ordinal
    }

    enum class Status { CURRENT, DONE, WARN, TODO }

    data class Completeness(val complete: Boolean, val missing: List<String>)

    /// Live completeness per step, from the server's OnboardingState
    /// payload. Team is genuinely optional so it never reads incomplete;
    /// properties stay skippable but the stepper is honest about an
    /// empty portfolio (same calls as the web's step list).
    fun completeness(step: Step, setup: BusinessSetup): Completeness = when (step) {
        Step.PROFILE -> Completeness(setup.steps.profile.complete, setup.steps.profile.missing ?: emptyList())
        Step.PROPERTIES -> Completeness(setup.steps.properties.complete, if (setup.steps.properties.complete) emptyList() else listOf("add at least one property"))
        Step.TEAM -> Completeness(true, emptyList())
    }

    /// Re-entering lands on the first thing still to do, not step 1.
    fun firstIncomplete(setup: BusinessSetup): Step = Step.entries.firstOrNull { !completeness(it, setup).complete } ?: Step.TEAM

    /// Backward is always allowed; forward only when every step before
    /// the target is complete.
    fun canJump(target: Step, current: Step, setup: BusinessSetup): Boolean =
        target.index <= current.index || Step.entries.take(target.index).all { completeness(it, setup).complete }

    fun status(step: Step, current: Step, setup: BusinessSetup): Status {
        if (step == current) return Status.CURRENT
        if (completeness(step, setup).complete) return Status.DONE
        return if (step.index < current.index) Status.WARN else Status.TODO
    }

    /// One honest sentence about what was left incomplete behind the
    /// current step, or null when nothing was.
    fun whatsLeft(current: Step, setup: BusinessSetup): String? {
        val gaps = Step.entries.filter { it.index < current.index }.mapNotNull { step ->
            val state = completeness(step, setup)
            if (state.complete) null else "${step.title}: ${if (state.missing.isEmpty()) "incomplete" else state.missing.joinToString(", ")}"
        }
        return if (gaps.isEmpty()) null else gaps.joinToString(" · ")
    }
}

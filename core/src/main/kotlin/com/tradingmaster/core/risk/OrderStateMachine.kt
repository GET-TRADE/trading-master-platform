package com.tradingmaster.core.risk

import com.tradingmaster.core.model.OrderState
import kotlinx.serialization.Serializable

/**
 * Order lifecycle state machine:
 * PRECHECK -> ENTRY_SUBMITTED -> ENTRY_CONFIRMED -> PROTECTION_SUBMITTED
 *          -> PROTECTION_VERIFIED -> POSITION_MONITORED -> CLOSED
 *
 * Rules enforced here:
 *  - protective orders must be VERIFIED exchange-side before the position counts as protected
 *  - on unknown/timeout states, reconcile exchange state before any retry; never blind-resubmit
 *  - failed protection blocks all new entries and flags emergency handling
 */
@Serializable
data class OrderRecord(
    val id: Long = 0,
    val symbol: String,
    val clientOrderId: String,
    val state: OrderState,
    val updatedAt: Long,
    val entryOrderId: String? = null,
    val stopOrderId: String? = null,
    val tpOrderId: String? = null,
    val protectionVerified: Boolean = false,
    val lastError: String? = null
)

object OrderStateMachine {

    private val transitions: Map<OrderState, Set<OrderState>> = mapOf(
        OrderState.PRECHECK to setOf(OrderState.ENTRY_SUBMITTED, OrderState.REJECTED, OrderState.CLOSED),
        OrderState.ENTRY_SUBMITTED to setOf(OrderState.ENTRY_CONFIRMED, OrderState.REJECTED, OrderState.FAILED_RECONCILE, OrderState.PRECHECK),
        OrderState.ENTRY_CONFIRMED to setOf(OrderState.PROTECTION_SUBMITTED, OrderState.FAILED_RECONCILE, OrderState.CLOSED),
        OrderState.PROTECTION_SUBMITTED to setOf(OrderState.PROTECTION_VERIFIED, OrderState.FAILED_RECONCILE),
        OrderState.PROTECTION_VERIFIED to setOf(OrderState.POSITION_MONITORED, OrderState.CLOSED),
        OrderState.POSITION_MONITORED to setOf(OrderState.CLOSED, OrderState.FAILED_RECONCILE),
        OrderState.REJECTED to setOf(),
        OrderState.FAILED_RECONCILE to setOf(OrderState.PRECHECK, OrderState.CLOSED),
        OrderState.CLOSED to setOf()
    )

    fun canTransition(from: OrderState, to: OrderState): Boolean =
        transitions[from]?.contains(to) == true

    fun transition(record: OrderRecord, to: OrderState, nowMs: Long, error: String? = null): OrderRecord {
        require(canTransition(record.state, to)) {
            "Illegal order transition ${record.state} -> $to for ${record.clientOrderId}. Reconcile exchange state first."
        }
        return record.copy(state = to, updatedAt = nowMs, lastError = error)
    }

    /**
     * Timeout policy: after a submit timeout the order state is UNKNOWN until reconciled.
     * The engine must fetch the order by clientOrderId from the exchange before resubmitting.
     */
    fun onTimeout(record: OrderRecord, nowMs: Long): OrderRecord =
        when (record.state) {
            OrderState.ENTRY_SUBMITTED, OrderState.PROTECTION_SUBMITTED ->
                record.copy(state = OrderState.FAILED_RECONCILE, updatedAt = nowMs,
                    lastError = "Timeout: exchange state unknown; reconciliation required before retry.")
            else -> record
        }

    fun isProtected(record: OrderRecord): Boolean =
        record.state == OrderState.PROTECTION_VERIFIED ||
            record.state == OrderState.POSITION_MONITORED

    /** True when a new entry must be blocked because protection could not be verified. */
    fun blocksNewEntries(record: OrderRecord): Boolean = record.state == OrderState.FAILED_RECONCILE
}

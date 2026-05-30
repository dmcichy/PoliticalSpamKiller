package com.personal.ptk.billing

sealed class SubscriptionState {
    data object Loading : SubscriptionState()
    data object Trial : SubscriptionState()
    data object Active : SubscriptionState()
    data object Expired : SubscriptionState()
    data object Grace : SubscriptionState()

    val hasAccess: Boolean
        get() = this is Trial || this is Active || this is Grace
}

package io.github.vvb2060.ims

class SimInfo(
    var subId: Int,
    var displayName: String?,
    var carrierName: String?,
    var simSlotIndex: Int
) {
    override fun toString(): String {
        return String.format("SIM %d: %s (%s)", simSlotIndex + 1, displayName, carrierName)
    }
}

package com.gzavagli.paraweather.domain.model

enum class ThermalChance(val label: String, val symbol: String, val description: String) {
    NONE("None", "☁️", "Stable air, zero thermal soaring lift"),
    LOW("Low", "☀️", "Weak, shallow, or broken thermals"),
    MEDIUM("Medium", "☀️☀️", "Active, good thermals. Great for soaring!"),
    HIGH("High", "☀️☀️☀️", "Exceptional, strong thermals. Outstanding soarability!")
}

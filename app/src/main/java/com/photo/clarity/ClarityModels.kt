package com.photo.clarity

enum class PhotoSlot {
    A,
    B
}

data class ClarityResult(
    val percentA: Int,
    val percentB: Int
) {
    val leadingSlot: PhotoSlot?
        get() = when {
            percentA > percentB -> PhotoSlot.A
            percentB > percentA -> PhotoSlot.B
            else -> null
        }
}

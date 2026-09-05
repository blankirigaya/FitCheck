package com.fitcheck.app.capsule

import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity

data class CapsuleItemScore(
    val item: WardrobeItemEntity,
    val versatilityScore: Int,
    val compatibilityCount: Int,
    val usageScore: Int,
    val coverageContribution: Int
)

data class RedundancyGroup(
    val items: List<WardrobeItemEntity>,
    val overlapScore: Int,
    val explanation: String
)

data class CapsuleGap(
    val role: String,
    val category: Category,
    val severity: String,
    val affectedOutfitCount: Int,
    val explanation: String
)

data class CapsuleAnalysis(
    val capsuleItems: List<CapsuleItemScore>,
    val allScores: List<CapsuleItemScore>,
    val redundancyGroups: List<RedundancyGroup>,
    val gaps: List<CapsuleGap>,
    val outfitPotential: Int,
    val categoryCoverage: Int,
    val colorVersatility: Int,
    val redundancyScore: Int,
    val capsuleScore: Int,
    val insight: String,
    val generatedAt: Long = System.currentTimeMillis()
) {
    fun getCapsuleWardrobeItems(): List<WardrobeItemEntity> = capsuleItems.map { it.item }
}

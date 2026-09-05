package com.fitcheck.app.capsule

import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlin.math.ceil
import kotlin.math.min

/** Deterministic, local wardrobe graph analysis. Gemma is not used for numbers. */
class CapsuleAnalyzer {
    fun analyze(source: List<WardrobeItemEntity>): CapsuleAnalysis {
        val items = source.filter { it.isAvailable && it.laundryStatus.name != "IN_LAUNDRY" }
        if (items.isEmpty()) return CapsuleAnalysis(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, 0, 100, 0, "Add clothing to discover your most versatile capsule pieces.")
        val scores = items.map { item ->
            val compatible = items.count { other -> other.id != item.id && pairScore(item, other) >= PAIR_THRESHOLD }
            val usage = min(100, item.wearCount * 12 + if (item.lastWorn != null) 20 else 0)
            val neutral = if (isNeutral(item.color)) 100 else 58
            val score = (compatible * 10 + neutral * 3 + usage * 2 + if (item.formality != null) 8 else 0) / 6
            CapsuleItemScore(item, score.coerceIn(0, 100), compatible, usage, compatible)
        }.sortedByDescending { it.versatilityScore }
        val capsule = selectCapsule(scores)
        val groups = redundancyGroups(items)
        val gaps = findGaps(items)
        val potential = countOutfits(capsule.map { it.item })
        val coverage = categoryCoverage(capsule.map { it.item })
        val colors = colorVersatility(capsule.map { it.item })
        val redundancy = (100 - groups.sumOf { it.overlapScore } / items.size.coerceAtLeast(1)).coerceIn(0, 100)
        val score = (coverage * .35 + min(100, potential * 5) * .35 + colors * .15 + redundancy * .15).toInt().coerceIn(0, 100)
        val insight = when {
            gaps.isNotEmpty() -> "Your strongest pieces are ${capsule.take(2).joinToString(" and ") { it.item.name }}. ${gaps.first().explanation}"
            groups.isNotEmpty() -> "Your capsule is versatile, but ${groups.first().explanation.lowercase()}"
            else -> "Your strongest pieces are versatile across the categories already in your wardrobe."
        }
        return CapsuleAnalysis(capsule, scores, groups, gaps, potential, coverage, colors, redundancy, score, insight)
    }

    fun countOutfits(items: List<WardrobeItemEntity>): Int {
        val tops = items.filter { it.category == Category.TOP }
        val bottoms = items.filter { it.category == Category.BOTTOM }
        val shoes = items.filter { it.category == Category.SHOES }
        var count = 0
        for (top in tops) for (bottom in bottoms) for (shoe in shoes) {
            if (pairScore(top, bottom) >= PAIR_THRESHOLD && pairScore(top, shoe) >= PAIR_THRESHOLD && pairScore(bottom, shoe) >= PAIR_THRESHOLD) count++
        }
        return count
    }

    private fun selectCapsule(scores: List<CapsuleItemScore>): List<CapsuleItemScore> {
        if (scores.isEmpty()) return emptyList()
        val target = min(12, maxOf(5, ceil(scores.size * .45).toInt())).coerceAtMost(scores.size)
        val selected = mutableListOf<CapsuleItemScore>()
        listOf(Category.TOP, Category.BOTTOM, Category.SHOES).forEach { category -> scores.firstOrNull { it.item.category == category }?.let(selected::add) }
        scores.filterNot { it in selected }.take(target - selected.size).forEach(selected::add)
        return selected.distinctBy { it.item.id }
    }

    private fun findGaps(items: List<WardrobeItemEntity>): List<CapsuleGap> {
        val core = items.groupingBy { it.category }.eachCount()
        val result = mutableListOf<CapsuleGap>()
        if ((core[Category.TOP] ?: 0) == 0) result += CapsuleGap("Everyday top", Category.TOP, "High", 0, "A versatile top is missing, so complete outfits cannot be formed.")
        if ((core[Category.BOTTOM] ?: 0) == 0) result += CapsuleGap("Neutral everyday bottom", Category.BOTTOM, "High", 0, "A neutral bottom would unlock combinations with your existing tops and shoes.")
        if ((core[Category.SHOES] ?: 0) == 0) result += CapsuleGap("Versatile everyday shoes", Category.SHOES, "High", 0, "A reliable shoe option is missing from the wardrobe graph.")
        if ((core[Category.OUTERWEAR] ?: 0) == 0 && items.size >= 4) result += CapsuleGap("Lightweight layering piece", Category.OUTERWEAR, "Medium", countOutfits(items), "A lightweight layer would extend existing outfits across more contexts.")
        return result.take(3)
    }

    private fun redundancyGroups(items: List<WardrobeItemEntity>): List<RedundancyGroup> = items.groupBy { key(it) }.values.filter { it.size >= 2 }.map { group ->
        RedundancyGroup(group, ((group.size - 1) * 25).coerceAtMost(90), "${group.size} ${group.first().category.name.lowercase()} pieces serve a similar ${group.first().style ?: "everyday"} role.")
    }

    private fun key(item: WardrobeItemEntity): String = listOf(item.category.name, item.color?.lowercase()?.trim(), item.subcategory?.lowercase()?.trim(), item.style?.lowercase()?.trim()).joinToString("|")
    private fun categoryCoverage(items: List<WardrobeItemEntity>): Int = (listOf(Category.TOP, Category.BOTTOM, Category.SHOES).count { category -> items.any { it.category == category } } * 100 / 3)
    private fun colorVersatility(items: List<WardrobeItemEntity>): Int = if (items.isEmpty()) 0 else (items.count { isNeutral(it.color) } * 100 / items.size).coerceAtLeast(45)
    private fun isNeutral(color: String?): Boolean = color?.lowercase()?.let { it.contains("black") || it.contains("white") || it.contains("navy") || it.contains("grey") || it.contains("gray") || it.contains("beige") || it.contains("brown") || it.contains("cream") } == true
    private fun pairScore(a: WardrobeItemEntity, b: WardrobeItemEntity): Int {
        if (a.category == b.category) return 0
        var result = 2
        if (isNeutral(a.color) || isNeutral(b.color)) result++
        if (!a.style.isNullOrBlank() && !b.style.isNullOrBlank() && a.style.equals(b.style, true)) result++
        if (a.formality != null && b.formality != null && kotlin.math.abs(a.formality - b.formality) <= 2) result++
        return result
    }
    companion object { private const val PAIR_THRESHOLD = 4 }
}

package com.fitcheck.app.ai

import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.StylePreferenceEntity
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import com.fitcheck.app.data.UserProfile
import com.fitcheck.app.data.repository.StylePreferenceRepository
import com.fitcheck.app.data.repository.WardrobeRepository
import com.fitcheck.app.data.repository.WearRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

data class TodayContext(
    val date: String,
    val time: String,
    val temperatureC: Int? = null,
    val weather: String = "Weather not provided",
    val location: String = "Location not provided",
    val occasion: String = "Everyday",
    val age: Int? = null,
    val gender: String? = null,
    val profession: String? = null
)

data class OutfitRecommendation(
    val itemIds: List<Long>,
    val explanation: String,
    val occasion: String
)

class ContextBuilder(private val profile: UserProfile? = null) {
    fun build(now: Long = System.currentTimeMillis()): TodayContext {
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now))
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val occasion = when {
            hour < 7 -> "Gym"
            hour < 16 -> "Professional"
            else -> "Casual"
        }
        return TodayContext(date = date, time = time, occasion = occasion, age = profile?.age, gender = profile?.gender, profession = profile?.profession)
    }
}

class CandidateSelector {
    suspend fun select(wardrobe: WardrobeRepository, wear: WearRepository, limitPerCategory: Int = 8): List<WardrobeItemEntity> {
        val recent = wear.getRecentlyWornItemIds(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
            .toSet()
        val available = wardrobe.getAvailableItems()
            .filter { it.laundryStatus.name != "IN_LAUNDRY" }
        val fresh = available.filterNot { it.id in recent }
        val source = if (fresh.size >= 3) fresh else available
        return Category.entries.flatMap { category ->
            source.filter { it.category == category }
                .sortedWith(compareBy<WardrobeItemEntity> { it.lastWorn ?: 0L }.thenBy { it.wearCount })
                .take(limitPerCategory)
        }
    }
}

class OutfitEngine(
    private val wardrobe: WardrobeRepository,
    private val wear: WearRepository,
    private val preferences: StylePreferenceRepository,
    private val runtime: AiRuntime,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val selector: CandidateSelector = CandidateSelector()
) {
    suspend fun recommend(previousIds: Set<Long> = emptySet(), liveContext: TodayContext? = null): Pair<TodayContext, OutfitRecommendation> {
        val context = liveContext ?: contextBuilder.build()
        val candidates = selector.select(wardrobe, wear)
        val top = bestForOccasion(candidates, Category.TOP, context.occasion)
        val bottom = bestForOccasion(candidates, Category.BOTTOM, context.occasion)
        val shoes = bestForOccasion(candidates, Category.SHOES, context.occasion)
        val accessory = candidates.firstOrNull { it.category == Category.ACCESSORY }
        require(top != null && bottom != null && shoes != null) {
            "Add at least one top, bottom, and shoes to Dress Me Today."
        }
        val fallback = listOf(top.id, bottom.id, shoes.id) + listOfNotNull(accessory?.id)
        val prompt = buildPrompt(context, candidates, preferences.readPreferences(), previousIds)
        val answer = runtime.generate(prompt).foldToString()
        val parsed = parseRecommendation(answer, candidates)
        val ids = parsed?.itemIds?.takeIf { it.size >= 3 && it != previousIds.toList() } ?: fallback
        val valid = validate(ids, candidates) ?: fallback
        return context to OutfitRecommendation(
            itemIds = valid,
            explanation = parsed?.explanation?.ifBlank { null }
                ?: "This look balances the available colors and keeps the silhouette easy for everyday wear.",
            occasion = context.occasion
        )
    }

    private fun buildPrompt(context: TodayContext, items: List<WardrobeItemEntity>, prefs: StylePreferenceEntity, previous: Set<Long>): String = buildString {
        appendLine("You are Fit Check's offline outfit stylist. Return ONLY this JSON: {\"itemIds\":[number,number,number,optionalAccessoryId],\"explanation\":\"one concise sentence\"}.")
        appendLine("Choose exactly one TOP, one BOTTOM, and one SHOES item. Prefer items tagged ${context.occasion} for this time of day. Add at most one ACCESSORY only when it genuinely complements the look. Do not invent IDs. Avoid previous IDs: $previous.")
        appendLine("Context: ${context.date}, ${context.time}, temperature=${context.temperatureC ?: "unknown"}°C, weather=${context.weather}, location=${context.location}, occasion=${context.occasion}.")
        appendLine("User context: age=${context.age ?: "unknown"}, gender=${context.gender ?: "unknown"}, profession=${context.profession ?: "unknown"}. Use this respectfully and do not stereotype.")
        appendLine("Preferred styles=${prefs.preferredStyles}, colors=${prefs.preferredColors}.")
        items.forEach { appendLine("id=${it.id}; category=${it.category}; color=${it.color}; style=${it.style}; formality=${it.formality}") }
    }

    private fun parseRecommendation(text: String, candidates: List<WardrobeItemEntity>): OutfitRecommendation? {
        val ids = Regex("itemIds\\s*\\\"?\\s*:\\s*\\[([^]]+)]", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: return null
        val explanation = Regex("explanation\\s*\\\"?\\s*:\\s*\\\"([^\\\"]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1).orEmpty()
        return OutfitRecommendation(ids, explanation, "Everyday")
    }

    private fun validate(ids: List<Long>, candidates: List<WardrobeItemEntity>): List<Long>? {
        val selected = ids.distinct().mapNotNull { id -> candidates.find { it.id == id } }
        if (selected.size !in 3..4) return null
        if (!selected.any { it.category == Category.TOP } || !selected.any { it.category == Category.BOTTOM } || !selected.any { it.category == Category.SHOES }) return null
        if (selected.count { it.category == Category.ACCESSORY } > 1 || selected.any { it.category !in setOf(Category.TOP, Category.BOTTOM, Category.SHOES, Category.ACCESSORY) }) return null
        return selected.map { it.id }
    }

    private fun bestForOccasion(items: List<WardrobeItemEntity>, category: Category, occasion: String): WardrobeItemEntity? {
        return items.filter { it.category == category }.maxByOrNull { item ->
            val text = listOfNotNull(item.subcategory, item.style, item.styleTags.joinToString(" ")).joinToString(" ").lowercase()
            if (text.contains(occasion.lowercase())) 10 else 0
        }
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<String>.foldToString(): String {
    val out = StringBuilder()
    collect { out.append(it) }
    return out.toString()
}

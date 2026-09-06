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

/** Hard safety check for occasion matching. Casual does not automatically mean gym-ready. */
fun isSuitableForOccasion(item: WardrobeItemEntity, occasion: String): Boolean {
    val normalized = occasion.lowercase()
    val text = listOfNotNull(item.name, item.subcategory, item.style, item.material, item.fit, item.styleTags.joinToString(" "))
        .joinToString(" ").lowercase()
    val isGym = listOf("gym", "workout", "training", "running").any { normalized.contains(it) }
    val isProfessional = listOf("professional", "office", "work", "presentation", "interview", "business").any { normalized.contains(it) }
    if (isProfessional) {
        // Professional looks must never use clearly athletic, beach, lounge,
        // or very casual pieces, even when the model labels them as suitable.
        val clearlyCasual = listOf(
            "shorts", "bermuda", "jogger", "track pant", "trackpants", "sweatpant", "leggings",
            "tank top", "crop top", "sports jersey", "gym", "workout", "running", "athletic",
            "flip flop", "flip-flop", "slides", "swim", "beachwear", "nightwear", "pajama", "pyjama"
        )
        if (clearlyCasual.any { text.contains(it) }) return false
    }
    if (!isGym) return true
    val clearlyUnsuitable = listOf("turtle neck", "turtleneck", "dress shirt", "formal shirt", "blazer", "suit", "kurta", "saree", "jeans", "loafer")
    if (clearlyUnsuitable.any { text.contains(it) }) return false
    val activeSignals = listOf("gym", "workout", "training", "running", "active", "athletic", "sport", "stretch", "breathable", "moisture", "flexible", "sneaker", "trainer", "track", "jogger", "leggings", "performance", "jersey", "tank", "tee")
    return activeSignals.any { text.contains(it) }
}

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
        val prefs = preferences.readPreferences()
        val top = bestForOccasion(candidates, Category.TOP, context.occasion, previousIds, prefs)
        val bottom = bestForOccasion(candidates, Category.BOTTOM, context.occasion, previousIds, prefs)
        val shoes = bestForOccasion(candidates, Category.SHOES, context.occasion, previousIds, prefs)
        val accessory = candidates.firstOrNull { it.category == Category.ACCESSORY }
        require(top != null && bottom != null && shoes != null) {
            "Add at least one top, bottom, and shoes to Dress Me Today."
        }
        val fallback = listOf(top.id, bottom.id, shoes.id) + listOfNotNull(accessory?.id)
        val prompt = buildPrompt(context, candidates, preferences.readPreferences(), previousIds)
        val answer = runtime.generate(prompt).foldToString()
        val parsed = parseRecommendation(answer, candidates)
        val ids = parsed?.itemIds?.takeIf { it.size >= 3 && it != previousIds.toList() } ?: fallback
        val valid = validate(ids, candidates, context.occasion) ?: fallback
        return context to OutfitRecommendation(
            itemIds = valid,
            explanation = parsed?.explanation?.ifBlank { null }
                ?: "This look balances the available colors and keeps the silhouette easy for everyday wear.",
            occasion = context.occasion
        )
    }

    private fun buildPrompt(context: TodayContext, items: List<WardrobeItemEntity>, prefs: StylePreferenceEntity, previous: Set<Long>): String = buildString {
        appendLine("You are Fit Check's offline outfit stylist. Return ONLY this JSON: {\"itemIds\":[number,number,number,optionalAccessoryId],\"explanation\":\"2 or 3 short sentences explaining the occasion, weather, color/style compatibility, and why any accessory helps\"}.")
        appendLine("Choose exactly one TOP, one BOTTOM, and one SHOES item for the specific occasion '${context.occasion}'. For gym/workout, prioritize activewear, breathable or stretch materials, flexible fits, and athletic shoes. For college/campus, prioritize casual, comfortable, everyday, and versatile pieces. For professional/office, prioritize structured, polished, formal, or smart-casual pieces. For any other occasion, match the item's recorded style and formality to that occasion. Do not reuse the same outfit logic for every occasion. Add at most one ACCESSORY only when it genuinely complements the look. Do not invent IDs. Avoid previous IDs: $previous.")
        appendLine("Context: ${context.date}, ${context.time}, temperature=${context.temperatureC ?: "unknown"}°C, weather=${context.weather}, location=${context.location}, occasion=${context.occasion}.")
        appendLine("User context: age=${context.age ?: "unknown"}, gender=${context.gender ?: "unknown"}, profession=${context.profession ?: "unknown"}. Use this respectfully and do not stereotype.")
        appendLine("Preferred styles=${prefs.preferredStyles}, colors=${prefs.preferredColors}.")
        items.forEach { appendLine("id=${it.id}; category=${it.category}; color=${it.color}; style=${it.style}; tags=${it.styleTags}; fit=${it.fit}; formality=${it.formality}") }
    }

    private fun parseRecommendation(text: String, candidates: List<WardrobeItemEntity>): OutfitRecommendation? {
        val ids = Regex("itemIds\\s*\\\"?\\s*:\\s*\\[([^]]+)]", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: return null
        val explanation = Regex("explanation\\s*\\\"?\\s*:\\s*\\\"([^\\\"]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1).orEmpty()
        return OutfitRecommendation(ids, explanation, "Everyday")
    }

    private fun validate(ids: List<Long>, candidates: List<WardrobeItemEntity>, occasion: String): List<Long>? {
        val selected = ids.distinct().mapNotNull { id -> candidates.find { it.id == id } }
        if (selected.size !in 3..4) return null
        if (!selected.any { it.category == Category.TOP } || !selected.any { it.category == Category.BOTTOM } || !selected.any { it.category == Category.SHOES }) return null
        if (selected.count { it.category == Category.ACCESSORY } > 1 || selected.any { it.category !in setOf(Category.TOP, Category.BOTTOM, Category.SHOES, Category.ACCESSORY) }) return null
        if (selected.any { !isSuitableForOccasion(it, occasion) }) return null
        return selected.map { it.id }
    }

    private fun bestForOccasion(items: List<WardrobeItemEntity>, category: Category, occasion: String, excluded: Set<Long> = emptySet(), prefs: StylePreferenceEntity = StylePreferenceEntity()): WardrobeItemEntity? {
        val eligible = items.filter { it.category == category && isSuitableForOccasion(it, occasion) }
        val candidates = eligible.filterNot { it.id in excluded }.ifEmpty { eligible }
        if (candidates.isEmpty()) return null
        val normalized = occasion.lowercase()
        fun score(item: WardrobeItemEntity): Int {
            val text = listOfNotNull(item.name, item.subcategory, item.style, item.material, item.fit, item.styleTags.joinToString(" "))
                .joinToString(" ").lowercase()
            val terms = when {
                listOf("gym", "workout", "training", "running").any { normalized.contains(it) } ->
                    listOf("gym", "workout", "training", "running", "active", "athletic", "sport", "stretch", "breathable", "moisture", "flexible", "sneaker", "track")
                listOf("college", "campus", "university", "school").any { normalized.contains(it) } ->
                    listOf("college", "campus", "casual", "everyday", "comfortable", "versatile", "sneaker", "relaxed")
                listOf("professional", "office", "work", "presentation", "interview").any { normalized.contains(it) } ->
                    listOf("professional", "office", "work", "formal", "polished", "structured", "smart", "blazer", "loafer")
                else -> normalized.split(Regex("\\s+"))
            }
            val occasionScore = terms.sumOf { term -> if (text.contains(term)) 1 else 0 }
            val preferredStyles = prefs.preferredStyles.map(String::lowercase)
            val preferredColors = prefs.preferredColors.map(String::lowercase)
            val preferredFits = prefs.preferredFits.map(String::lowercase)
            val preferredOccasions = prefs.preferredOccasions.map(String::lowercase)
            val preferenceScore = preferredStyles.count { text.contains(it) } * 3 +
                preferredColors.count { text.contains(it) } * 2 +
                preferredFits.count { text.contains(it) } * 2 +
                preferredOccasions.count { text.contains(it) } * 3
            return occasionScore * 10 + preferenceScore
        }
        // Prefer an occasion match and avoid the previous outfit when possible.
        val offset = normalized.hashCode().toLong().let { kotlin.math.abs(it) }
        return candidates.maxWithOrNull(compareBy<WardrobeItemEntity> { score(it) }
            .thenBy { (it.id + offset) % 997L })
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<String>.foldToString(): String {
    val out = StringBuilder()
    collect { out.append(it) }
    return out.toString()
}

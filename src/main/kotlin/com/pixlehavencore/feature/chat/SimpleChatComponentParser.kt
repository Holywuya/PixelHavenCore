package com.pixlehavencore.feature.chat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import com.pixlehavencore.util.TextUtils
import taboolib.module.chat.colored

object SimpleChatComponentParser {

    private val hoverPattern = Regex("<hover:(\\w+):([^>]+)>")
    private val clickPattern = Regex("<click:(\\w+):([^>]+)>")
    private val miniMessage = MiniMessage.miniMessage()

    fun parse(player: Player, format: String): Component {
        val processed = SimpleChatPlaceholderService.apply(player, format)
        return parseRaw(processed)
    }

    fun parseRaw(format: String): Component {
        val normalized = format.replace(":N_L:", "\n")
        if (normalized.isBlank()) {
            return Component.empty()
        }
        val extraction = extractLegacyTags(normalized)
        val baseComponent = parseUnified(extraction.remainingText)
        return if (extraction.interactionGroups.isEmpty()) {
            baseComponent
        } else {
            applyInteractionEvents(baseComponent, extraction.interactionGroups)
        }
    }

    private fun extractLegacyTags(input: String): TagExtractionResult {
        return runCatching {
            val hoverMatches = hoverPattern.findAll(input).map { match ->
                TagMatch(match.range.first, match.range.last + 1, TagType.HOVER, match.groupValues[1], match.groupValues[2])
            }.sortedBy { it.start }.toList()

            val clickMatches = clickPattern.findAll(input).map { match ->
                TagMatch(match.range.first, match.range.last + 1, TagType.CLICK, match.groupValues[1], match.groupValues[2])
            }.sortedBy { it.start }.toList()

            val allMatches = (hoverMatches + clickMatches).sortedBy { it.start }
            if (allMatches.isEmpty()) {
                return TagExtractionResult(input, emptyList())
            }

            val groups = mutableListOf<InteractionGroup>()
            val currentHover = mutableListOf<HoverTagData?>()
            val currentClick = mutableListOf<ClickTagData?>()
            var currentEnd = 0

            val tagRanges = mutableListOf<IntRange>()
            var i = 0
            while (i < allMatches.size) {
                val match = allMatches[i]
                tagRanges.add(match.start until match.end)
                when (match.type) {
                    TagType.HOVER -> currentHover.add(HoverTagData(match.action, match.value))
                    TagType.CLICK -> currentClick.add(ClickTagData(match.action, match.value))
                }
                currentEnd = match.end
                val hasNext = i + 1 < allMatches.size
                val adjacentWithNext = hasNext && allMatches[i + 1].start == match.end
                if (!adjacentWithNext) {
                    val hover = currentHover.firstNotNullOfOrNull { it }
                    val click = currentClick.firstNotNullOfOrNull { it }
                    groups.add(InteractionGroup(tagEndPosition = -1, hover = hover, click = click))
                    currentHover.clear()
                    currentClick.clear()
                }
                i++
            }

            var remainingText = input
            for (range in tagRanges.sortedByDescending { it.first }) {
                remainingText = remainingText.removeRange(range)
            }

            var pos = 0
            val adjustedGroups = groups.map { group ->
                val adjusted = group.copy(tagEndPosition = pos)
                pos += 0
                adjusted
            }

            TagExtractionResult(remainingText, adjustedGroups)
        }.getOrElse {
            TagExtractionResult(input, emptyList())
        }
    }

    private fun parseUnified(text: String): Component {
        val miniMessageInput = TextUtils.translateLegacy(text)
        return runCatching {
            miniMessage.deserialize(miniMessageInput)
        }.getOrElse {
            LegacyComponentSerializer.legacySection().deserialize(text.colored())
        }
    }

    private fun applyInteractionEvents(component: Component, groups: List<InteractionGroup>): Component {
        if (groups.size == 1 && groups[0].tagEndPosition == 0) {
            var result = component
            val group = groups[0]
            result = applyHoverEvent(result, group.hover)
            result = applyClickEvent(result, group.click)
            return result
        }

        val children = component.children()
        if (children.isEmpty()) {
            var result = component
            for (group in groups) {
                result = applyHoverEvent(result, group.hover)
                result = applyClickEvent(result, group.click)
            }
            return result
        }

        val newChildren = children.toMutableList()
        for (group in groups) {
            val targetIndex = if (group.tagEndPosition < newChildren.size) group.tagEndPosition else 0
            if (targetIndex < newChildren.size) {
                var child = newChildren[targetIndex]
                child = applyHoverEvent(child, group.hover)
                child = applyClickEvent(child, group.click)
                newChildren[targetIndex] = child
            }
        }
        return component.children(newChildren)
    }

    private fun applyHoverEvent(component: Component, hover: HoverTagData?): Component {
        if (hover == null) return component
        if (component.hoverEvent() != null) return component
        if (!hover.action.equals("show_text", true)) return component
        val hoverComponent = runCatching {
            parseUnified(hover.value.replace(":N_L:", "\n"))
        }.getOrNull() ?: return component
        return component.hoverEvent(HoverEvent.showText(hoverComponent))
    }

    private fun applyClickEvent(component: Component, click: ClickTagData?): Component {
        if (click == null) return component
        if (component.clickEvent() != null) return component
        val action = click.action.lowercase()
        val value = click.value
        return when (action) {
            "run_command" -> component.clickEvent(ClickEvent.runCommand(value))
            "suggest_command" -> component.clickEvent(ClickEvent.suggestCommand(value))
            "open_url" -> component.clickEvent(ClickEvent.openUrl(value))
            "copy" -> component.clickEvent(ClickEvent.copyToClipboard(value))
            else -> component
        }
    }

    private enum class TagType {
        HOVER, CLICK
    }

    private data class TagMatch(
        val start: Int,
        val end: Int,
        val type: TagType,
        val action: String,
        val value: String
    )
}

data class HoverTagData(
    val action: String,
    val value: String
)

data class ClickTagData(
    val action: String,
    val value: String
)

data class InteractionGroup(
    val tagEndPosition: Int,
    val hover: HoverTagData? = null,
    val click: ClickTagData? = null
)

data class TagExtractionResult(
    val remainingText: String,
    val interactionGroups: List<InteractionGroup>
)

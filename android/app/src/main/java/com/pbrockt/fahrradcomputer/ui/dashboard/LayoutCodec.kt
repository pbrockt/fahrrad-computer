package com.pbrockt.fahrradcomputer.ui.dashboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Speichert das Dashboard als JSON.
 *
 * Kacheltypen und Hoehen stehen als Text drin und werden beim Lesen tolerant uebersetzt:
 * Faellt in einer spaeteren Version ein Kacheltyp weg, verschwindet nur diese Kachel -
 * nicht das ganze, muehsam eingerichtete Layout.
 */
object LayoutCodec {

    private const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class LayoutDto(val version: Int = VERSION, val pages: List<PageDto> = emptyList())

    @Serializable
    private data class PageDto(val name: String = "", val rows: List<RowDto> = emptyList())

    @Serializable
    private data class RowDto(val height: String = RowHeight.MEDIUM.name, val tiles: List<TileDto> = emptyList())

    @Serializable
    private data class TileDto(val type: String, val wide: Boolean = false, val options: Map<String, String> = emptyMap())

    fun encode(layout: DashboardLayout): String = json.encodeToString(
        LayoutDto(
            pages = layout.pages.map { p ->
                PageDto(p.name, p.rows.map { r ->
                    RowDto(r.height.name, r.tiles.map { TileDto(it.type.name, it.wide, it.options) })
                })
            }
        )
    )

    /** null, wenn der Text gar nicht lesbar ist oder keine Seite uebrig bleibt. */
    fun decode(text: String?): DashboardLayout? {
        if (text.isNullOrBlank()) return null
        val dto = runCatching { json.decodeFromString<LayoutDto>(text) }.getOrNull() ?: return null
        val pages = dto.pages.map { p ->
            DashboardPage(
                name = p.name.ifBlank { "Seite" },
                rows = p.rows.mapNotNull { r ->
                    var units = 0
                    val tiles = r.tiles.mapNotNull { t ->
                        val type = enumOrNull<TileType>(t.type) ?: return@mapNotNull null
                        TileSlot(type, t.wide, t.options)
                    }.takeWhile { units += it.units; units <= DashboardLayout.MAX_ROW_UNITS }
                    if (tiles.isEmpty()) null
                    else TileRow(tiles, enumOrNull<RowHeight>(r.height) ?: RowHeight.MEDIUM)
                },
            )
        }
        return DashboardLayout(pages).takeIf { pages.isNotEmpty() }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}

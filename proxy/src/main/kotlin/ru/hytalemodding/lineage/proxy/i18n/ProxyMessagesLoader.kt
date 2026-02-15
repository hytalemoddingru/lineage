/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.i18n

import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Loads editable localized messages from `messages/<language>.toml`.
 */
object ProxyMessagesLoader {
    private const val CURRENT_SCHEMA_VERSION = 1
    private const val DEFAULT_LANGUAGE = "en-us"
    private const val LANGUAGE_KEY = "language"
    private const val SCHEMA_KEY = "schema_version"

    private val defaultBundles: Map<String, Map<String, String>> = mapOf(
        "en-us" to enUsDefaults(),
        "ru-ru" to ruRuDefaults(),
    )

    private val defaultMessages = ProxyMessages(
        defaultLanguage = DEFAULT_LANGUAGE,
        bundles = defaultBundles,
    )

    fun load(messagesDir: Path): ProxyMessages {
        ensureDefaultFiles(messagesDir)
        val overrides = loadOverrides(messagesDir)
        val merged = defaultBundles
            .mapValues { it.value.toMutableMap() }
            .toMutableMap()
        for ((language, entries) in overrides) {
            val bucket = merged.getOrPut(language) { linkedMapOf() }
            bucket.putAll(entries)
        }
        return ProxyMessages(
            defaultLanguage = DEFAULT_LANGUAGE,
            bundles = merged.mapValues { it.value.toMap() },
        )
    }

    fun defaults(): ProxyMessages = defaultMessages

    private fun ensureDefaultFiles(messagesDir: Path) {
        Files.createDirectories(messagesDir)
        ensureDefaultLanguageFile(messagesDir.resolve("en-us.toml"), "en-us", enUsDefaults())
        ensureDefaultLanguageFile(messagesDir.resolve("ru-ru.toml"), "ru-ru", ruRuDefaults())
    }

    private fun ensureDefaultLanguageFile(path: Path, language: String, entries: Map<String, String>) {
        if (Files.exists(path)) {
            return
        }
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write(renderLanguageToml(language, entries))
        }
    }

    private fun loadOverrides(messagesDir: Path): Map<String, Map<String, String>> {
        val overrides = mutableMapOf<String, MutableMap<String, String>>()
        Files.list(messagesDir).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) }
                .filter { it.extension.lowercase() == "toml" }
                .sortedBy { it.name.lowercase() }
                .forEach { file ->
                    val parsed = parseLanguageFile(file)
                    val bucket = overrides.getOrPut(parsed.language) { linkedMapOf() }
                    bucket.putAll(parsed.entries)
                }
        }
        return overrides.mapValues { it.value.toMap() }
    }

    private fun parseLanguageFile(path: Path): ParsedLanguage {
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            val result = Toml.parse(reader)
            if (result.hasErrors()) {
                val errors = result.errors().joinToString("; ") { it.toString() }
                throw IllegalStateException("Failed to parse ${path.fileName}: $errors")
            }
            validateSchema(path, result)
            val language = normalizeLanguage(
                result.getString(LANGUAGE_KEY) ?: path.fileName.toString().removeSuffix(".toml"),
            )
            val entries = linkedMapOf<String, String>()
            for (key in result.keySet().sorted()) {
                if (key == SCHEMA_KEY || key == LANGUAGE_KEY) {
                    continue
                }
                val value = result.getString(key)
                    ?: throw IllegalStateException("${path.fileName}: key '$key' must be a string")
                entries[key] = value
            }
            return ParsedLanguage(language, entries)
        }
    }

    private fun validateSchema(path: Path, result: TomlParseResult) {
        val schema = result.getLong(SCHEMA_KEY) ?: CURRENT_SCHEMA_VERSION.toLong()
        if (schema.toInt() != CURRENT_SCHEMA_VERSION) {
            throw IllegalStateException("${path.fileName}: unsupported schema_version=$schema")
        }
    }

    private fun renderLanguageToml(language: String, entries: Map<String, String>): String {
        return buildString {
            appendLine("# ============================================================")
            appendLine("# Lineage Proxy Messages")
            appendLine("# Edit values to customize chat/system text for this language.")
            appendLine("# ============================================================")
            appendLine("schema_version = $CURRENT_SCHEMA_VERSION")
            appendLine("language = \"${escapeToml(language)}\"")
            appendLine()
            for ((key, value) in entries) {
                appendLine("$key = \"${escapeToml(value)}\"")
            }
        }
    }

    private fun escapeToml(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    private fun normalizeLanguage(raw: String): String {
        val normalized = raw.trim().lowercase().replace('_', '-')
        return when (normalized) {
            "ru" -> "ru-ru"
            "en" -> "en-us"
            else -> normalized
        }
    }

    private fun enUsDefaults(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "proxy_prefix" to "<gradient:#FF3CD5:#DD55FF>line</gradient><gradient:#DD55FF:#FF477B>age</gradient> <#CCCCCC>| <reset>",
            "dispatcher_player_only" to "&cCommand is only available to players.",
            "dispatcher_no_permission" to "&cYou do not have permission to run this command.",
            "gateway_player_not_found" to "&cPlayer not found.",
            "gateway_player_not_authenticated" to "&cPlayer is not authenticated.",
            "gateway_command_empty" to "&cCommand must not be empty.",
            "gateway_unknown_command" to "&cUnknown command.",
            "transfer_usage" to "&eUsage: {usage}",
            "transfer_usage_console" to "&eUsage: transfer <backendId> <playerName>",
            "transfer_player_not_found" to "&cPlayer not found: &f{playerName}",
            "transfer_requested" to "&aTransfer requested: &f{playerName} &7-> &f{backendId}&a.",
            "transfer_unknown_backend" to "&cUnknown backend. Use &e/transfer list&c.",
            "transfer_backend_offline" to "&cBackend &f{backendId}&c is offline.",
            "transfer_backend_unknown_status" to "&cBackend &f{backendId}&c status is unknown. Try again shortly.",
            "transfer_already_connected" to "&eYou are already connected to &f{backendId}&e.",
            "transfer_player_not_ready" to "&cYour session is not ready for transfer yet.",
            "transfer_control_unavailable" to "&cTransfer system is currently unavailable.",
            "transfer_request_failed" to "&cTransfer request failed. Please try again.",
            "transfer_failed" to "&cTransfer failed.",
            "transfer_backends_header" to "&7Backends:",
            "transfer_backends_entry" to "&7- &f{backendId} &8({endpoint}) &7- {status}",
            "transfer_status_online" to "&aONLINE",
            "transfer_status_offline" to "&cOFFLINE",
            "transfer_status_unknown" to "&eUNKNOWN",
            "perm_usage" to "&eUsage: {usage}",
            "perm_usage_grant" to "&eUsage: perm grant <player> <permission>",
            "perm_usage_revoke" to "&eUsage: perm revoke <player> <permission>",
            "perm_usage_clear" to "&eUsage: perm clear <player>",
            "perm_usage_list" to "&eUsage: perm list <player>",
            "perm_granted" to "&aGranted &f{permission} &ato &f{subject}&a.",
            "perm_revoked" to "&eRevoked &f{permission} &efrom &f{subject}&e.",
            "perm_cleared" to "&eCleared permissions for &f{subject}&e.",
            "perm_none" to "&7No permissions for &f{subject}&7.",
            "perm_header" to "&7Permissions for &f{subject}&7:",
            "perm_entry" to "&7- &f{permission}",
            "stop_console_only" to "&cThis command is only available from console.",
            "stop_stopping" to "&eStopping Lineage proxy...",
            "mod_usage" to "&eUsage: {usage}",
            "mod_no_mods" to "&7No mods loaded.",
            "mod_loaded_header" to "&7Loaded mods: &f{count}",
            "mod_loaded_entry" to "&7- &f{id} &7{version} &8({state})",
            "mod_reload_usage" to "&eUsage: mod reload <id|all>",
            "mod_reloaded_all" to "&aReloaded all mods.",
            "mod_reloaded_one" to "&aReloaded mod &f{id}&a.",
            "mod_reload_failed" to "&cFailed to reload mod.",
            "messages_usage" to "&eUsage: {usage}",
            "messages_reload_ok" to "&aMessages and style reloaded.",
            "messages_reload_partial" to "&eReload completed with warnings: &f{details}",
            "list_usage" to "&eUsage: {usage}",
            "list_unknown_backend" to "&cUnknown backend: &f{backendId}",
            "list_none" to "&7No players online.",
            "list_none_backend" to "&7No players online on &f{backendId}&7.",
            "list_header_all" to "&7Players online: &f{count} &7(page &f{page}&7/&f{totalPages}&7)",
            "list_header_backend" to "&7Players on &f{backendId}&7: &f{count} &7(page &f{page}&7/&f{totalPages}&7)",
            "list_page_invalid" to "&cInvalid page &f{page}&c. Available pages: &f1-{totalPages}",
            "list_page_hint" to "&7Next page: &f{nextCommand}",
            "list_line" to "&f{players}",
            "info_usage" to "&eUsage: {usage}",
            "info_player_not_found" to "&cPlayer not found: &f{target}",
            "info_header" to "&7Player info: &f{name}",
            "info_line_id" to "&7- id: &f{value}",
            "info_line_name" to "&7- name: &f{value}",
            "info_line_language" to "&7- lang: &f{value}",
            "info_line_state" to "&7- state: &f{value}",
            "info_line_backend" to "&7- backend: &f{value}",
            "info_line_ip" to "&7- ip: &f{value}",
            "info_line_session" to "&7- session: &f{value}",
            "info_line_version" to "&7- clientVersion: &f{value}",
            "info_line_protocol" to "&7- protocol(crc/build): &f{value}",
            "info_line_connected" to "&7- connectedFor: &f{value}",
            "info_line_ping" to "&7- ping: &f{value}",
            "info_value_unknown" to "unknown",
            "ping_usage" to "&eUsage: {usage}",
            "ping_player_not_found" to "&cPlayer not found: &f{target}",
            "ping_unavailable" to "&ePing unavailable for &f{name}&e.",
            "ping_result_self" to "&aYour ping: &f{ping} ms",
            "ping_result_player" to "&a{name} ping: &f{ping} ms",
            "help_usage" to "&7Tip: use &f{usage} &7for command details.",
            "help_no_commands" to "&7No commands available.",
            "help_header" to "&7Available commands: &f{count}",
            "help_line_commands" to "&f{commands}",
            "help_command_not_found" to "&cCommand not found: &f{command}",
            "help_detail_header" to "&7Command: &f/{name}",
            "help_detail_description" to "&7- description: &f{value}",
            "help_detail_usage" to "&7- usage: &f{value}",
            "help_detail_aliases" to "&7- aliases: &f{value}",
            "help_detail_permission" to "&7- permission: &f{value}",
            "help_detail_player_only" to "&7- playerOnly: &atrue",
        )
    }

    private fun ruRuDefaults(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "proxy_prefix" to "<gradient:#FF3CD5:#DD55FF>line</gradient><gradient:#DD55FF:#FF477B>age</gradient> <#CCCCCC>| <reset>",
            "dispatcher_player_only" to "&cКоманда доступна только игрокам.",
            "dispatcher_no_permission" to "&cУ тебя нет прав для этой команды.",
            "gateway_player_not_found" to "&cИгрок не найден.",
            "gateway_player_not_authenticated" to "&cИгрок ещё не прошёл аутентификацию.",
            "gateway_command_empty" to "&cКоманда не должна быть пустой.",
            "gateway_unknown_command" to "&cНеизвестная команда.",
            "transfer_usage" to "&eИспользование: {usage}",
            "transfer_usage_console" to "&eИспользование: transfer <backendId> <playerName>",
            "transfer_player_not_found" to "&cИгрок не найден: &f{playerName}",
            "transfer_requested" to "&aПеревод запрошен: &f{playerName} &7-> &f{backendId}&a.",
            "transfer_unknown_backend" to "&cНеизвестный backend. Используй &e/transfer list&c.",
            "transfer_backend_offline" to "&cBackend &f{backendId}&c недоступен.",
            "transfer_backend_unknown_status" to "&cСтатус backend &f{backendId}&c пока неизвестен. Повтори позже.",
            "transfer_already_connected" to "&eТы уже подключён к &f{backendId}&e.",
            "transfer_player_not_ready" to "&cСессия ещё не готова для перевода.",
            "transfer_control_unavailable" to "&cСистема transfer сейчас недоступна.",
            "transfer_request_failed" to "&cНе удалось отправить запрос transfer.",
            "transfer_failed" to "&cTransfer завершился с ошибкой.",
            "transfer_backends_header" to "&7Список backend:",
            "transfer_backends_entry" to "&7- &f{backendId} &8({endpoint}) &7- {status}",
            "transfer_status_online" to "&aONLINE",
            "transfer_status_offline" to "&cOFFLINE",
            "transfer_status_unknown" to "&eUNKNOWN",
            "perm_usage" to "&eИспользование: {usage}",
            "perm_usage_grant" to "&eИспользование: perm grant <player> <permission>",
            "perm_usage_revoke" to "&eИспользование: perm revoke <player> <permission>",
            "perm_usage_clear" to "&eИспользование: perm clear <player>",
            "perm_usage_list" to "&eИспользование: perm list <player>",
            "perm_granted" to "&aВыдано &f{permission} &aдля &f{subject}&a.",
            "perm_revoked" to "&eОтозвано &f{permission} &eу &f{subject}&e.",
            "perm_cleared" to "&eПрава для &f{subject} &eочищены.",
            "perm_none" to "&7У &f{subject} &7нет выданных прав.",
            "perm_header" to "&7Права игрока &f{subject}&7:",
            "perm_entry" to "&7- &f{permission}",
            "stop_console_only" to "&cЭта команда доступна только из консоли.",
            "stop_stopping" to "&eОстанавливаю Lineage proxy...",
            "mod_usage" to "&eИспользование: {usage}",
            "mod_no_mods" to "&7Моды не загружены.",
            "mod_loaded_header" to "&7Загружено модов: &f{count}",
            "mod_loaded_entry" to "&7- &f{id} &7{version} &8({state})",
            "mod_reload_usage" to "&eИспользование: mod reload <id|all>",
            "mod_reloaded_all" to "&aВсе моды перезагружены.",
            "mod_reloaded_one" to "&aМод &f{id} &aперезагружен.",
            "mod_reload_failed" to "&cНе удалось перезагрузить мод.",
            "messages_usage" to "&eИспользование: {usage}",
            "messages_reload_ok" to "&aСообщения и стиль успешно перезагружены.",
            "messages_reload_partial" to "&eПерезагрузка завершена с предупреждениями: &f{details}",
            "list_usage" to "&eИспользование: {usage}",
            "list_unknown_backend" to "&cНеизвестный backend: &f{backendId}",
            "list_none" to "&7Онлайн пуст.",
            "list_none_backend" to "&7На &f{backendId} &7игроков нет.",
            "list_header_all" to "&7Игроков онлайн: &f{count} &7(страница &f{page}&7/&f{totalPages}&7)",
            "list_header_backend" to "&7Игроков на &f{backendId}&7: &f{count} &7(страница &f{page}&7/&f{totalPages}&7)",
            "list_page_invalid" to "&cНеверная страница &f{page}&c. Доступно: &f1-{totalPages}",
            "list_page_hint" to "&7Следующая страница: &f{nextCommand}",
            "list_line" to "&f{players}",
            "info_usage" to "&eИспользование: {usage}",
            "info_player_not_found" to "&cИгрок не найден: &f{target}",
            "info_header" to "&7Информация об игроке: &f{name}",
            "info_line_id" to "&7- id: &f{value}",
            "info_line_name" to "&7- имя: &f{value}",
            "info_line_language" to "&7- язык: &f{value}",
            "info_line_state" to "&7- состояние: &f{value}",
            "info_line_backend" to "&7- backend: &f{value}",
            "info_line_ip" to "&7- ip: &f{value}",
            "info_line_session" to "&7- сессия: &f{value}",
            "info_line_version" to "&7- clientVersion: &f{value}",
            "info_line_protocol" to "&7- protocol(crc/build): &f{value}",
            "info_line_connected" to "&7- в сети: &f{value}",
            "info_line_ping" to "&7- ping: &f{value}",
            "info_value_unknown" to "неизвестно",
            "ping_usage" to "&eИспользование: {usage}",
            "ping_player_not_found" to "&cИгрок не найден: &f{target}",
            "ping_unavailable" to "&eПинг для &f{name}&e недоступен.",
            "ping_result_self" to "&aТвой пинг: &f{ping} ms",
            "ping_result_player" to "&aПинг &f{name}&a: &f{ping} ms",
            "help_usage" to "&7Подсказка: используй &f{usage} &7для деталей по команде.",
            "help_no_commands" to "&7Нет доступных команд.",
            "help_header" to "&7Доступных команд: &f{count}",
            "help_line_commands" to "&f{commands}",
            "help_command_not_found" to "&cКоманда не найдена: &f{command}",
            "help_detail_header" to "&7Команда: &f/{name}",
            "help_detail_description" to "&7- описание: &f{value}",
            "help_detail_usage" to "&7- использование: &f{value}",
            "help_detail_aliases" to "&7- алиасы: &f{value}",
            "help_detail_permission" to "&7- право: &f{value}",
            "help_detail_player_only" to "&7- только для игрока: &atrue",
        )
    }

    private data class ParsedLanguage(
        val language: String,
        val entries: Map<String, String>,
    )
}

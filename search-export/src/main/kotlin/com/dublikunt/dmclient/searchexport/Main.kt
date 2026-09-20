package com.dublikunt.dmclient.searchexport

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

private val PRETTY_JSON = Json { prettyPrint = true; encodeDefaults = true }
private val MIN_JSON = Json { prettyPrint = false; encodeDefaults = true }

fun main(args: Array<String>) = runBlocking {
    val opts = try {
        parseArgs(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        printUsage()
        exitProcess(2)
    }
    if (opts.help) {
        printUsage()
        return@runBlocking
    }

    val exporter = SearchExporter(
        maxPages = opts.maxPages,
        retries = opts.retries,
        pageDelayMs = opts.pageDelayMs
    )
    val bundle = exporter.export(opts.types)

    val json =
        if (opts.minify) MIN_JSON.encodeToString(bundle) else PRETTY_JSON.encodeToString(bundle)
    val outFile = File(opts.out)
    outFile.parentFile?.mkdirs()
    outFile.writeText(json)

    println("Wrote ${outFile.absolutePath}")
    println(
        "tags=${bundle.tags.size} artists=${bundle.artists.size} " +
                "characters=${bundle.characters.size} parodies=${bundle.parodies.size}"
    )
    println("generatedAt=${bundle.generatedAt} version=${bundle.version}")
}

private data class Options(
    val out: String = "search-data.json",
    val maxPages: Int = 1000,
    val retries: Int = 4,
    val pageDelayMs: Long = 500L,
    val types: Set<String> = SearchExporter.TYPES.keys,
    val minify: Boolean = false,
    val help: Boolean = false
)

private fun parseArgs(args: Array<String>): Options {
    var out = "search-data.json"
    var maxPages = 1000
    var retries = 4
    var pageDelayMs = 500L
    var types: Set<String> = SearchExporter.TYPES.keys
    var minify = false
    var help = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--out" -> {
                out = args.getOrNull(++i) ?: throw IllegalArgumentException("--out needs a value")
            }

            "--max-pages" -> {
                maxPages = args.getOrNull(++i)?.toIntOrNull()
                    ?: throw IllegalArgumentException("--max-pages needs an integer")
                require(maxPages >= 1) { "--max-pages must be >= 1" }
            }

            "--retries" -> {
                retries = args.getOrNull(++i)?.toIntOrNull()
                    ?: throw IllegalArgumentException("--retries needs an integer")
                require(retries >= 1) { "--retries must be >= 1" }
            }

            "--page-delay-ms" -> {
                pageDelayMs = args.getOrNull(++i)?.toLongOrNull()
                    ?: throw IllegalArgumentException("--page-delay-ms needs an integer")
                require(pageDelayMs >= 0) { "--page-delay-ms must be >= 0" }
            }

            "--types" -> {
                val raw = args.getOrNull(++i)
                    ?: throw IllegalArgumentException("--types needs a value")
                types = raw.split(",").map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }.toSet()
                require(types.isNotEmpty()) { "--types must not be empty" }
                val unknown = types - SearchExporter.TYPES.keys
                require(unknown.isEmpty()) {
                    "Unknown types: $unknown. Expected subset of ${SearchExporter.TYPES.keys}"
                }
            }

            "--minify" -> minify = true
            "--pretty" -> minify = false
            "-h", "--help" -> help = true
            else -> throw IllegalArgumentException("Unknown argument '${args[i]}'")
        }
        i++
    }
    return Options(out, maxPages, retries, pageDelayMs, types, minify, help)
}

private fun printUsage() {
    println(
        """
        search-export - fetch nhentai search suggestion data and save it as JSON.

        Usage: search-export [options]

        Options:
          --out <path>         Output file (default: search-data.json)
          --types <csv>        Subset of tags,artists,characters,parodies (default: all)
          --max-pages <n>      Page cap per type, mirrors the app (default: 1000)
          --retries <n>        Retries per request on 429/5xx (default: 4)
          --page-delay-ms <n>  Delay between pages to avoid rate limiting (default: 500)
          --minify / --pretty  JSON formatting (default: --pretty)
          -h, --help           Show this help

        Output format (also understood by the app's SearchBundleImporter):
          {"version":1,"generatedAt":...,"tags":[...],"artists":[...],
           "characters":[...],"parodies":[...]}

        Examples:
          ./gradlew :search-export:run --args="--out search-data.json"
          ./gradlew :search-export:run --args="--out search-data.json --types tags,artists --max-pages 5"
        """.trimIndent()
    )
}

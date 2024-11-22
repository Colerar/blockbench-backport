package io.github.colerar.backportbb

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import team.unnamed.mocha.MochaEngine
import team.unnamed.mocha.runtime.value.Value
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.math.abs


@Serializable
data class AnimationFile(
    @SerialName("format_version") val formatVersion: String, val animations: HashMap<String, Animation>
)

@Serializable
data class Animation(
    val loop: Boolean, val animation_length: Double, val bones: HashMap<String, Bone>
)

@Serializable
data class Bone(
    var position: JsonElement? = null,
    var rotation: JsonElement? = null,
    var scale: JsonElement? = null,
)

fun main(args: Array<String>): Unit = MainArgs().main(args)

class MainArgs : CliktCommand() {
    private val inputFiles by argument()
        .file(
            mustExist = true,
            canBeFile = true,
            canBeDir = false,
            mustBeWritable = true,
            mustBeReadable = true,
            canBeSymlink = false,
        )
        .multiple(required = true)
        .help("Input Files")
        .validate { files ->
            files.forEach { file ->
                if (!file.endsWith(".animation.json")) {
                    throw PrintMessage("Invalid file name `$file`, should end with `.animation.json`")
                }
            }
        }

    val start: Double by option("-s", "--start").double().default(0.0).help("When the animation duration starts at")
        .check { it >= 0 }

    val end: Double by option("-e", "--end").double().default(3.0).help("When the animation duration ends at")
        .check { it > 0 && end > start }

    val step by option("-t", "--step").double().default(0.25).help("Time interval of keyframes").check { it > 0 }

    val noSmooth by option("-n", "--no-smooth").flag().help("Disable smooth")

    override fun run() {
        inputFiles.forEach { file ->
            processFile(file, this)
        }
    }
}

val animJsonRegex = Regex("""\.animation\.json$""")

fun processFile(file: File, context: MainArgs) {
    println("Processing: ${file.absolutePath}")
    println("Reading Animation JSON")

    val input = BufferedReader(FileInputStream(file).reader()).use { it.readText() }
    val json = Json.decodeFromString<AnimationFile>(input)

    println("Converting")
    json.animations.forEach { (animName, anim) ->
        anim.bones.forEach { (boneName, bone) ->
            bone.position?.let { position -> bone.position = convert(position, context) }
            bone.rotation?.let { rotation -> bone.rotation = convert(rotation, context) }
            bone.scale?.let { rotation -> bone.scale = convert(rotation, context) }
        }
    }

    println("Writing modified Animation JSON")
    val path = file.absolutePath.replace(animJsonRegex, "-modified.animation.json")
    Files.newBufferedWriter(Path(path)).use { wrt ->
        wrt.write(Json.encodeToString(json))
    }
}

fun convert(element: JsonElement, context: MainArgs): JsonElement {
    when (element) {
        is JsonArray -> {
            if (!element[0].isString() && !element[1].isString() && !element[2].isString()) {
                return element
            }
            return compose(element, context)
        }

        else -> return element
    }
}

fun JsonElement.isString(): Boolean = this is JsonPrimitive && this.jsonPrimitive.isString

fun generateDoubleList(step: Double = 0.05, start: Double = 0.0, end: Double = 3.0): List<Double> {
    var i = 0.0
    val arr = mutableListOf<Double>()
    do {
        arr.add(i)
        i += step
    } while (i < end)
    return arr
}

fun compose(positions: JsonArray, context: MainArgs): JsonObject {
    val smooth = !context.noSmooth
    val mocha = MochaEngine.createStandard()
    return buildJsonObject {
        val keyframeAt = generateDoubleList(step = context.step, start = context.start, end = context.end)
        for (animTime in keyframeAt) {
            mocha.scope().set("anim_time", Value.of(animTime))
            val array = buildJsonArray {
                positions.forEach { value ->
                    if (value.isString()) {
                        val expr = value.jsonPrimitive.content.replace("q.anim_time", "anim_time")
                        val computed = mocha.eval(expr)
                        // println("$expr (anim_time = $animTime) -> $computed")
                        this.add(computed)
                    } else {
                        this.add(value.jsonPrimitive.intOrNull ?: value.jsonPrimitive.doubleOrNull)
                    }
                }
            }
            if (smooth) {
                val pos3object = buildJsonObject {
                    this.put("post", array)
                    this.put("lerp_mode", "catmullrom")
                }
                this.put(animTime.toString(), pos3object)
            } else {
                this.put(animTime.toString(), array)
            }
        }
    }
}

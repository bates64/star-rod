package tools

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.accessibility.*
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * SwingInspector for remote controlling Star Rod's UI from within the same JVM.
 *
 * Usage (via StarRod.jar):
 *   java -jar StarRod.jar --remote list-windows
 *   java -jar StarRod.jar --remote tree <window-title>
 *   java -jar StarRod.jar --remote find <window-title> <component-name>
 *   java -jar StarRod.jar --remote click <window-title> <component-name>
 *   java -jar StarRod.jar --remote screenshot <window-title> <output.png>
 */

private val gson = GsonBuilder().setPrettyPrinting().create()

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(1)
    }

    val command = args[0]

    try {
        when (command) {
            "list-windows" -> listWindows()
            "tree" -> {
                if (args.size < 2) {
                    System.err.println("Usage: tree <window-title>")
                    exitProcess(1)
                }
                printTree(args[1])
            }
            "find" -> {
                if (args.size < 3) {
                    System.err.println("Usage: find <window-title> <component-name>")
                    exitProcess(1)
                }
                findComponent(args[1], args[2])
            }
            "click" -> {
                if (args.size < 3) {
                    System.err.println("Usage: click <window-title> <component-name>")
                    exitProcess(1)
                }
                clickComponent(args[1], args[2])
            }
            "screenshot" -> {
                if (args.size < 3) {
                    System.err.println("Usage: screenshot <window-title> <output.png>")
                    exitProcess(1)
                }
                takeScreenshot(args[1], args[2])
            }
            else -> {
                System.err.println("Unknown command: $command")
                printUsage()
                exitProcess(1)
            }
        }
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun printUsage() {
    println("SwingInspector - CLI tool for inspecting Swing applications")
    println()
    println("IMPORTANT: Start Star Rod first, then run this tool from the same GUI session.")
    println()
    println("Commands:")
    println("  list-windows                           List all visible windows")
    println("  tree <window-title>                    Show component tree")
    println("  find <window-title> <component-name>   Find component by name")
    println("  click <window-title> <component-name>  Click a component")
    println("  screenshot <window-title> <file.png>   Take screenshot")
}

private fun listWindows() {
    val windows = Window.getWindows()
    val result = JsonArray()

    for (window in windows) {
        if (!window.isVisible) continue

        val obj = JsonObject().apply {
            addProperty("title", window.windowTitle)
            addProperty("class", window.javaClass.name)
            addProperty("visible", window.isVisible)
            addProperty("showing", window.isShowing)

            val bounds = window.bounds
            add("bounds", JsonObject().apply {
                addProperty("x", bounds.x)
                addProperty("y", bounds.y)
                addProperty("width", bounds.width)
                addProperty("height", bounds.height)
            })
        }

        result.add(obj)
    }

    println(gson.toJson(result))
}

private fun printTree(windowTitle: String) {
    val window = findWindow(windowTitle) ?: run {
        System.err.println("Window not found: $windowTitle")
        exitProcess(1)
    }

    val ac = window.accessibleContext ?: run {
        System.err.println("Window has no accessible context")
        exitProcess(1)
    }

    val tree = buildComponentTree(ac)
    println(gson.toJson(tree))
}

private fun findComponent(windowTitle: String, componentName: String) {
    val window = findWindow(windowTitle) ?: run {
        System.err.println("Window not found: $windowTitle")
        exitProcess(1)
    }

    val ac = findAccessibleComponent(window.accessibleContext, componentName) ?: run {
        System.err.println("Component not found: $componentName")
        exitProcess(1)
    }

    val info = buildComponentInfo(ac)
    println(gson.toJson(info))
}

private fun clickComponent(windowTitle: String, componentName: String) {
    val window = findWindow(windowTitle) ?: run {
        System.err.println("Window not found: $windowTitle")
        exitProcess(1)
    }

    val ac = findAccessibleComponent(window.accessibleContext, componentName) ?: run {
        System.err.println("Component not found: $componentName")
        exitProcess(1)
    }

    val action = ac.accessibleAction ?: run {
        System.err.println("Component has no actions")
        exitProcess(1)
    }

    // Trigger the default action (usually index 0)
    val success = action.doAccessibleAction(0)

    val result = JsonObject().apply {
        addProperty("success", success)
        addProperty("action", action.getAccessibleActionDescription(0))
    }
    println(gson.toJson(result))
}

private fun takeScreenshot(windowTitle: String, outputFile: String) {
    val window = findWindow(windowTitle) ?: run {
        System.err.println("Window not found: $windowTitle")
        exitProcess(1)
    }

    val robot = Robot()
    val bounds = window.bounds
    val screenshot = robot.createScreenCapture(bounds)

    ImageIO.write(screenshot, "png", File(outputFile))

    val result = JsonObject().apply {
        addProperty("success", true)
        addProperty("file", outputFile)
        addProperty("width", screenshot.width)
        addProperty("height", screenshot.height)
    }
    println(gson.toJson(result))
}

private fun findWindow(title: String): Window? {
    val windows = Window.getWindows()
    return windows.firstOrNull { window ->
        window.isVisible && window.windowTitle?.contains(title) == true
    }
}

private val Window.windowTitle: String?
    get() = when (this) {
        is Frame -> this.title
        is Dialog -> this.title
        else -> accessibleContext?.accessibleName ?: javaClass.simpleName
    }

private fun buildComponentTree(ac: AccessibleContext): JsonObject {
    val obj = buildComponentInfo(ac)

    val childCount = ac.accessibleChildrenCount
    if (childCount > 0) {
        val children = JsonArray()
        for (i in 0 until childCount) {
            val child = ac.getAccessibleChild(i)
            val childContext = child?.accessibleContext
            if (childContext != null) {
                children.add(buildComponentTree(childContext))
            }
        }
        obj.add("children", children)
    }

    return obj
}

private fun buildComponentInfo(ac: AccessibleContext): JsonObject {
    return JsonObject().apply {
        addProperty("name", ac.accessibleName)
        addProperty("description", ac.accessibleDescription)
        addProperty("role", ac.accessibleRole.toString())

        // States
        ac.accessibleStateSet?.let { states ->
            val statesArray = JsonArray()
            states.toArray().forEach { state ->
                statesArray.add(state.toString())
            }
            add("states", statesArray)
        }

        // Text (if available)
        ac.accessibleText?.let { text ->
            try {
                val textContent = text.getAtIndex(AccessibleText.SENTENCE, 0)
                if (textContent != null) {
                    addProperty("text", textContent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Actions (if available)
        ac.accessibleAction?.let { actions ->
            val actionsArray = JsonArray()
            for (i in 0 until actions.accessibleActionCount) {
                actionsArray.add(actions.getAccessibleActionDescription(i))
            }
            add("actions", actionsArray)
        }

        // Component info
        ac.accessibleComponent?.let { comp ->
            val bounds = comp.bounds
            add("bounds", JsonObject().apply {
                addProperty("x", bounds.x)
                addProperty("y", bounds.y)
                addProperty("width", bounds.width)
                addProperty("height", bounds.height)
            })

            addProperty("visible", comp.isVisible)
            addProperty("showing", comp.isShowing)
            addProperty("enabled", comp.isEnabled)
        }
    }
}

private fun findAccessibleComponent(root: AccessibleContext?, name: String): AccessibleContext? {
    if (root == null) return null

    if (root.accessibleName == name) return root

    // Search children
    val childCount = root.accessibleChildrenCount
    for (i in 0 until childCount) {
        val child = root.getAccessibleChild(i)
        val childContext = child?.accessibleContext
        val found = findAccessibleComponent(childContext, name)
        if (found != null) return found
    }

    return null
}

package com.aicodeeditor.feature.agent.domain.container

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinuxContainerEngine @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    companion object {
        private const val TAG = "LinuxContainerEngine"
    }

    /**
     * Executes a command synchronously inside the container (or native shell for POC)
     * and returns the output.
     */
    suspend fun runCommandSync(command: String, projectPath: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val process = startContainerProcess(command, projectPath)

            // Read output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // Wait for command to finish
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "Command exited with code: $exitCode")
            }

            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Starts the process. If rootfs is properly installed, uses PRoot.
     * Otherwise, falls back to native Android shell (POC/Fallback).
     */
    private fun startContainerProcess(command: String, projectPath: String?): Process {
        // TODO: In Phase 2, this will always use PRoot
        // val useProot = containerInstaller.isInstalled() && hasProotBinary()
        val useProot = false // Force native shell for initial POC since we don't have PRoot binary yet

        val processBuilder = if (useProot) {
            buildProotProcess(command, projectPath)
        } else {
            buildNativeProcess(command, projectPath)
        }

        // Redirect stderr to stdout so we capture everything in one stream
        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    private fun buildProotProcess(command: String, projectPath: String?): ProcessBuilder {
        val rootfs = containerInstaller.rootfsDir.absolutePath
        val prootBin = "$rootfs/usr/bin/proot"

        val commandArgs = mutableListOf(
            prootBin,
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc"
        )

        // Setup path mapping if provided
        if (projectPath != null) {
            commandArgs.add("-b")
            commandArgs.add("$projectPath:/workspace")
            commandArgs.add("-w")
            commandArgs.add("/workspace")
        }

        commandArgs.add("/bin/sh")
        commandArgs.add("-c")
        commandArgs.add(command)

        val processBuilder = ProcessBuilder(commandArgs)

        // Setup basic Linux environment
        val env = processBuilder.environment()
        env["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"

        return processBuilder
    }

    private fun buildNativeProcess(command: String, projectPath: String?): ProcessBuilder {
        // Fallback to Android's native shell
        val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
        if (projectPath != null) {
            processBuilder.directory(java.io.File(projectPath))
        }
        return processBuilder
    }
}